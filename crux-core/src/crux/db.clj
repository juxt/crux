(ns ^:no-doc crux.db
  (:require [clojure.set :as set]))

;; tag::Index[]
(defprotocol Index
  (seek-values [this k])
  (next-values [this]))
;; end::Index[]

;; tag::LayeredIndex[]
(defprotocol LayeredIndex
  (open-level [this])
  (close-level [this])
  (max-depth [this]))
;; end::LayeredIndex[]

(defprotocol IndexStoreTx
  (index-docs [this docs])
  (unindex-eids [this eids])
  (index-entity-txs [this entity-txs])
  (commit-index-tx [this])
  (abort-index-tx [this]))

;; tag::IndexStore[]
(defprotocol IndexStore
  (exclusive-avs [this eids])
  (store-index-meta [this k v])
  (latest-completed-tx [this])
  (tx-failed? [this tx-id])
  (begin-index-tx [index-store tx fork-at]))
;; end::IndexStore[]

(defprotocol IndexMeta
  (-read-index-meta [this k not-found]))

(defn read-index-meta
  ([index-meta k] (-read-index-meta index-meta k nil))
  ([index-meta k not-found] (-read-index-meta index-meta k not-found)))

(defprotocol IndexSnapshotFactory
  (open-index-snapshot ^java.io.Closeable [this]))

;; tag::IndexSnapshot[]
(defprotocol IndexSnapshot
  (av [this a min-v])
  (ave [this a v min-e entity-resolver-fn])
  (ae [this a min-e])
  (aev [this a e min-v entity-resolver-fn])
  (entity-as-of-resolver [this eid valid-time tx-id])
  (entity-as-of ^crux.codec.EntityTx [this eid valid-time tx-id])
  (entity-history [this eid sort-order opts])
  (decode-value [this value-buffer])
  (encode-value [this value])
  (resolve-tx [this tx])
  (open-nested-index-snapshot ^java.io.Closeable [this]))
;; end::IndexSnapshot[]

;; tag::TxLog[]
(defprotocol TxLog
  (submit-tx [this tx-events])
  (open-tx-log ^crux.api.ICursor [this after-tx-id])
  (latest-submitted-tx [this]))
;; end::TxLog[]

(defprotocol TxIngester
  (begin-tx [tx-ingester tx fork-at])
  (ingester-error [tx-ingester]))

(defprotocol InFlightTx
  (index-tx-events [in-flight-tx tx-events])
  (commit [in-flight-tx])
  (abort [in-flight-tx]))

(defprotocol DocumentStore
  (submit-docs [this id-and-docs])
  (-fetch-docs [this ids]))

(defn fetch-docs [document-store ids]
  (let [ids (set ids)]
    (loop [docs {}]
      (let [missing-ids (set/difference ids (set (keys docs)))
            docs (into docs
                       (when (seq missing-ids)
                         (-fetch-docs document-store missing-ids)))]
        (if (= (count docs) (count ids))
          docs
          (do
            (Thread/sleep 100)
            (recur docs)))))))
