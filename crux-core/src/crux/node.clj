(ns crux.node
  (:require [clojure.java.io :as io]
            [clojure.spec.alpha :as s]
            [clojure.tools.logging :as log]
            [com.stuartsierra.dependency :as dep]
            [crux.api :as api]
            [crux.backup :as backup]
            [crux.codec :as c]
            [crux.config :as cc]
            [crux.db :as db]
            [crux.index :as idx]
            [crux.io :as cio]
            [crux.kv :as kv]
            [crux.lru :as lru]
            crux.object-store
            [crux.query :as q]
            [crux.status :as status]
            [crux.topology :as topo]
            [crux.tx :as tx]
            [crux.bus :as bus])
  (:import [crux.api ICruxAPI ICruxAsyncIngestAPI NodeOutOfSyncException ICursor]
           java.io.Closeable
           java.util.Date
           [java.util.concurrent Executors]
           java.util.concurrent.locks.StampedLock))

(def crux-version
  (when-let [pom-file (io/resource "META-INF/maven/juxt/crux-core/pom.properties")]
    (with-open [in (io/reader pom-file)]
      (let [{:strs [version revision]} (cio/load-properties in)]
        {:crux.version/version version
         :crux.version/revision revision}))))

(defn- ensure-node-open [{:keys [closed?]}]
  (when @closed?
    (throw (IllegalStateException. "Crux node is closed"))))

(defrecord CruxNode [kv-store tx-log document-store indexer tx-consumer object-store bus
                     options close-fn status-fn closed? ^StampedLock lock]
  ICruxAPI
  (db [this] (.db this nil nil))
  (db [this valid-time] (.db this valid-time nil))

  (db [this valid-time tx-time]
    (cio/with-read-lock lock
      (ensure-node-open this)
      (let [latest-tx-time (:crux.tx/tx-time (.latestCompletedTx this))
            _ (when (and tx-time (or (nil? latest-tx-time) (pos? (compare tx-time latest-tx-time))))
                (throw (NodeOutOfSyncException. (format "node hasn't indexed the requested transaction: requested: %s, available: %s"
                                                        tx-time latest-tx-time)
                                                tx-time latest-tx-time)))
            tx-time (or tx-time latest-tx-time)
            valid-time (or valid-time (Date.))]

        (q/db indexer
              (lru/get-named-cache kv-store ::query-cache)
              (lru/get-named-cache kv-store ::conform-cache)
              object-store
              bus
              valid-time
              tx-time))))

  (openDB [this] (.openDB this nil nil))
  (openDB [this valid-time] (.openDB this valid-time nil))
  (openDB [this valid-time tx-time]
    (let [db (.db this valid-time tx-time)]
      (assoc db :index-store (q/open-index-store db))))

  (document [this content-hash]
    (cio/with-read-lock lock
      (ensure-node-open this)
      (with-open [index-store (db/open-index-store indexer)]
        (-> (db/get-single-object object-store index-store (c/new-id content-hash))
            (idx/keep-non-evicted-doc)))))

  (documents [this content-hash-set]
    (cio/with-read-lock lock
      (ensure-node-open this)
      (with-open [index-store (db/open-index-store indexer)]
        (->> (db/get-objects object-store index-store (map c/new-id content-hash-set))
             (into {} (remove (comp idx/evicted-doc? val)))))))

  (history [this eid]
    (reverse (.historyRange this eid nil nil nil nil)))

  (historyRange [this eid valid-time-start transaction-time-start valid-time-end transaction-time-end]
    (cio/with-read-lock lock
      (ensure-node-open this)
      (with-open [index-store (db/open-index-store indexer)]
        (->> (db/entity-history-range index-store eid valid-time-start transaction-time-start valid-time-end transaction-time-end)
             (mapv c/entity-tx->edn)
             (sort-by (juxt :crux.db/valid-time :crux.tx/tx-time))))))

  (status [this]
    (cio/with-read-lock lock
      (ensure-node-open this)
      ;; we don't have status-fn set when other components use node as a dependency within the topology
      (if status-fn
        (status-fn)
        (into {} (mapcat status/status-map) [indexer kv-store object-store tx-log]))))

  (attributeStats [this]
    (cio/with-read-lock lock
      (ensure-node-open this)
      (db/read-index-meta indexer :crux.kv/stats)))

  (submitTx [this tx-ops]
    (cio/with-read-lock lock
      (ensure-node-open this)
      (db/submit-docs document-store (tx/tx-ops->id-and-docs tx-ops))
      @(db/submit-tx tx-log (map tx/tx-op->tx-event tx-ops))))

  (hasTxCommitted [this {:keys [::tx/tx-id ::tx/tx-time] :as submitted-tx}]
    (cio/with-read-lock lock
      (ensure-node-open this)
      (let [{latest-tx-id ::tx/tx-id, latest-tx-time ::tx/tx-time} (.latestCompletedTx this)]
        (if (and tx-id (or (nil? latest-tx-id) (pos? (compare tx-id latest-tx-id))))
          (throw
           (NodeOutOfSyncException.
            (format "Node hasn't indexed the transaction: requested: %s, available: %s" tx-time latest-tx-time)
            tx-time latest-tx-time))
          (with-open [index-store (db/open-index-store indexer)]
            (db/tx-failed? index-store tx-id))))))

  (openTxLog ^ICursor [this after-tx-id with-ops?]
    (cio/with-read-lock lock
      (ensure-node-open this)
      (if (let [latest-submitted-tx-id (::tx/tx-id (api/latest-submitted-tx this))]
            (or (nil? latest-submitted-tx-id)
                (and after-tx-id (>= after-tx-id latest-submitted-tx-id))))
        (cio/->cursor #() [])

        (let [tx-log-iterator (db/open-tx-log tx-log after-tx-id)
              index-store (db/open-index-store indexer)
              tx-log (-> (iterator-seq tx-log-iterator)
                         (->> (filter #(db/tx-failed? index-store (:crux.tx/tx-id %))))
                         (cond->> with-ops? (map (fn [{:keys [crux.tx/tx-id
                                                              crux.tx.event/tx-events] :as tx-log-entry}]
                                                   (-> tx-log-entry
                                                       (dissoc :crux.tx.event/tx-events)
                                                       (assoc :crux.api/tx-ops
                                                              (->> tx-events
                                                                   (mapv #(tx/tx-event->tx-op % index-store object-store)))))))))]

          (cio/->cursor (fn []
                          (.close index-store)
                          (.close tx-log-iterator))
                        tx-log)))))

  (sync [this timeout]
    (when-let [tx (db/latest-submitted-tx (:tx-log this))]
      (-> (api/await-tx this tx nil)
          :crux.tx/tx-time)))

  (awaitTxTime [this tx-time timeout]
    (cio/with-read-lock lock
      (ensure-node-open this)
      (-> (tx/await-tx-time indexer tx-consumer tx-time (or timeout (:crux.tx-log/await-tx-timeout options)))
          :crux.tx/tx-time)))

  (awaitTx [this submitted-tx timeout]
    (cio/with-read-lock lock
      (ensure-node-open this)
      (tx/await-tx indexer tx-consumer submitted-tx (or timeout (:crux.tx-log/await-tx-timeout options)))))

  (latestCompletedTx [this]
    (db/latest-completed-tx indexer))

  (latestSubmittedTx [this]
    (db/latest-submitted-tx tx-log))

  ICruxAsyncIngestAPI
  (submitTxAsync [this tx-ops]
    (cio/with-read-lock lock
      (ensure-node-open this)
      (db/submit-docs document-store (tx/tx-ops->id-and-docs tx-ops))
      (db/submit-tx tx-log (map tx/tx-op->tx-event tx-ops))))

  backup/INodeBackup
  (write-checkpoint [this {:keys [crux.backup/checkpoint-directory] :as opts}]
    (cio/with-read-lock lock
      (ensure-node-open this)
      (kv/backup kv-store (io/file checkpoint-directory "kv-store"))

      (when (satisfies? backup/INodeBackup tx-log)
        (backup/write-checkpoint tx-log opts))))

  Closeable
  (close [_]
    (cio/with-write-lock lock
      (when (and (not @closed?) close-fn) (close-fn))
      (reset! closed? true))))

(def ^:private node-component
  {:start-fn (fn [{::keys [indexer tx-consumer document-store object-store tx-log kv-store bus]} node-opts]
               (map->CruxNode {:options node-opts
                               :kv-store kv-store
                               :tx-log tx-log
                               :indexer indexer
                               :tx-consumer tx-consumer
                               :document-store document-store
                               :object-store object-store
                               :bus bus
                               :closed? (atom false)
                               :lock (StampedLock.)}))
   :deps #{::indexer ::tx-consumer ::kv-store ::bus ::document-store ::object-store ::tx-log}
   :args {:crux.tx-log/await-tx-timeout {:doc "Default timeout for awaiting transactions being indexed."
                                         :default nil
                                         :crux.config/type :crux.config/duration}}})

(def base-topology
  {::kv-store 'crux.kv.memdb/kv
   ::object-store 'crux.object-store/kv-object-store
   ::indexer 'crux.tx/kv-indexer
   ::tx-consumer 'crux.tx.consumer/tx-consumer
   ::bus 'crux.bus/bus
   ::node 'crux.node/node-component})

(defn start ^crux.api.ICruxAPI [options]
  (let [[{::keys [node] :as components} close-fn] (topo/start-topology options)]
    (-> node
        (assoc :status-fn (fn []
                            (merge crux-version
                                   (into {} (mapcat status/status-map) (vals (dissoc components ::node)))))
               :close-fn close-fn)
        (vary-meta assoc ::topology components))))
