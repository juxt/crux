(ns crux.kafka
  (:require [clojure.java.io :as io]
            [clojure.set :as set]
            [clojure.spec.alpha :as s]
            [clojure.tools.logging :as log]
            [crux.codec :as c]
            [crux.db :as db]
            [crux.io :as cio]
            [crux.node :as n]
            [crux.status :as status]
            [crux.tx :as tx]
            [taoensso.nippy :as nippy]
            [crux.kv :as kv])
  (:import crux.db.DocumentStore
           [crux.kafka.nippy NippyDeserializer NippySerializer]
           java.io.Closeable
           java.time.Duration
           [java.util Collection Date Map UUID]
           java.util.concurrent.ExecutionException
           [org.apache.kafka.clients.admin AdminClient NewTopic TopicDescription]
           [org.apache.kafka.clients.consumer ConsumerRebalanceListener ConsumerRecord KafkaConsumer]
           [org.apache.kafka.clients.producer KafkaProducer ProducerRecord RecordMetadata]
           [org.apache.kafka.common.errors TopicExistsException InterruptException]
           org.apache.kafka.common.TopicPartition))

(s/def ::bootstrap-servers string?)
(s/def ::group-id string?)
(s/def ::topic string?)
(s/def ::partitions pos-int?)
(s/def ::replication-factor pos-int?)

(s/def ::tx-topic ::topic)
(s/def ::doc-topic ::topic)
(s/def ::doc-partitions ::partitions)
(s/def ::create-topics boolean?)

(def default-producer-config
  {"enable.idempotence" "true"
   "acks" "all"
   "compression.type" "snappy"
   "key.serializer" (.getName NippySerializer)
   "value.serializer" (.getName NippySerializer)})

(def ^:private default-consumer-config
  {"enable.auto.commit" "false"
   "isolation.level" "read_committed"
   "auto.offset.reset" "earliest"
   "key.deserializer" (.getName NippyDeserializer)
   "value.deserializer" (.getName NippyDeserializer)})

(def default-topic-config
  {"message.timestamp.type" "LogAppendTime"})

(def tx-topic-config
  {"retention.ms" (str Long/MAX_VALUE)})

(def doc-topic-config
  {"cleanup.policy" "compact"})

(defn- read-kafka-properties-file [f]
  (when f
    (with-open [in (io/reader (io/file f))]
      (cio/load-properties in))))

(defn- derive-kafka-config [{:keys [crux.kafka/bootstrap-servers
                                    crux.kafka/kafka-properties-file
                                    crux.kafka/kafka-properties-map]}]
  (merge {"bootstrap.servers" bootstrap-servers}
         (read-kafka-properties-file kafka-properties-file)
         kafka-properties-map))

(defn create-producer
  ^org.apache.kafka.clients.producer.KafkaProducer [config]
  (KafkaProducer. ^Map (merge default-producer-config config)))

(defn create-consumer ^org.apache.kafka.clients.consumer.KafkaConsumer [config]
  (KafkaConsumer. ^Map (merge default-consumer-config config)))

(defn create-admin-client
  ^org.apache.kafka.clients.admin.AdminClient [config]
  (AdminClient/create ^Map config))

(defn create-topic [^AdminClient admin-client topic num-partitions replication-factor config]
  (let [new-topic (doto (NewTopic. topic num-partitions replication-factor)
                    (.configs (merge default-topic-config config)))]
    (try
      @(.all (.createTopics admin-client [new-topic]))
      (catch ExecutionException e
        (let [cause (.getCause e)]
          (when-not (instance? TopicExistsException cause)
            (throw e)))))))

(defn- ensure-topic-exists [admin-client topic topic-config partitions {::keys [replication-factor create-topics]}]
  (when create-topics
    (create-topic admin-client topic partitions replication-factor topic-config)))

(defn- ensure-tx-topic-has-single-partition [^AdminClient admin-client tx-topic]
  (let [name->description @(.all (.describeTopics admin-client [tx-topic]))]
    (assert (= 1 (count (.partitions ^TopicDescription (get name->description tx-topic)))))))

(defn- seek-consumer [^KafkaConsumer consumer tp-offsets]
  ;; tp-offsets :: TP -> offset
  (doseq [^TopicPartition tp (.assignment consumer)]
    (if-let [next-offset (get tp-offsets tp)]
      (.seek consumer tp ^long next-offset)
      (.seekToBeginning consumer [tp]))))

(defn subscribe-consumer [^KafkaConsumer consumer ^Collection topics tp-offsets]
  (.subscribe consumer topics (reify ConsumerRebalanceListener
                                (onPartitionsRevoked [_ partitions]
                                  (log/debug "Partitions revoked:" (str partitions)))
                                (onPartitionsAssigned [_ partitions]
                                  (log/debug "Partitions assigned:" (str partitions))
                                  (seek-consumer consumer tp-offsets)))))

(defn consumer-seqs [^KafkaConsumer consumer ^Duration poll-duration]
  (lazy-seq
    (log/debug "polling")
    (when-let [records (seq (try
                              (.poll consumer poll-duration)
                              (catch InterruptException e
                                (Thread/interrupted)
                                (throw (.getCause e)))))]
      (log/debugf "got %d records" (count records))
      (cons records (consumer-seqs consumer poll-duration)))))

(defn- tx-record->tx-log-entry [^ConsumerRecord record]
  {:crux.tx.event/tx-events (.value record)
   :crux.tx/tx-id (.offset record)
   :crux.tx/tx-time (Date. (.timestamp record))})

(defrecord KafkaTxLog [^KafkaProducer producer, ^KafkaConsumer latest-submitted-tx-consumer, tx-topic, kafka-config]
  db/TxLog
  (submit-tx [this tx-events]
    (try
      (let [tx-send-future (.send producer (ProducerRecord. tx-topic nil tx-events))]
        (delay
         (let [record-meta ^RecordMetadata @tx-send-future]
           {::tx/tx-id (.offset record-meta)
            ::tx/tx-time (Date. (.timestamp record-meta))})))))

  (open-tx-log [this after-tx-id]
    (let [tp-offsets {(TopicPartition. tx-topic 0) (some-> after-tx-id inc)}
          consumer (doto (create-consumer kafka-config)
                     (.assign (keys tp-offsets))
                     (seek-consumer tp-offsets))]
      (cio/->cursor #(.close consumer)
                    (->> (consumer-seqs consumer (Duration/ofSeconds 1))
                         (mapcat identity)
                         (map tx-record->tx-log-entry)))))

  (latest-submitted-tx [this]
    (let [tx-tp (TopicPartition. tx-topic 0)
          end-offset (-> (.endOffsets latest-submitted-tx-consumer [tx-tp]) (get tx-tp))]
      (when (pos? end-offset)
        {:crux.tx/tx-id (dec end-offset)})))

  status/Status
  (status-map [_]
    {:crux.zk/zk-active?
     (try
       (boolean (.listTopics latest-submitted-tx-consumer))
       (catch Exception e
         (log/debug e "Could not list Kafka topics:")
         false))}))

(defn submit-docs [id-and-docs {:keys [^KafkaProducer producer, doc-topic]}]
  (doseq [[content-hash doc] id-and-docs]
    (->> (ProducerRecord. doc-topic content-hash doc)
         (.send producer)))
  (.flush producer))

(defrecord KafkaDocumentStore [^KafkaProducer producer doc-topic
                               kv-store object-store
                               ^Thread indexing-thread !indexing-error]
  Closeable
  (close [_]
    (.interrupt indexing-thread)
    (.join indexing-thread))

  db/DocumentStore
  (submit-docs [this id-and-docs]
    (submit-docs id-and-docs this))

  (fetch-docs [this ids]
    (loop [indexed {}]
      (let [missing-ids (set/difference (set ids) (set (keys indexed)))
            indexed (merge indexed (when (seq missing-ids)
                                     (with-open [snapshot (kv/new-snapshot kv-store)]
                                       (db/get-objects object-store snapshot missing-ids))))]
        (if (= (count indexed) (count ids))
          indexed
          (do
            (Thread/sleep 100)
            (when-let [error @!indexing-error]
              (throw (RuntimeException. "Doc indexing error" error)))
            (recur indexed)))))))

(defn- read-doc-offsets [indexer]
  (->> (db/read-index-meta indexer :crux.tx-log/consumer-state)
       (into {} (map (fn [[k {:keys [next-offset]}]]
                       [(let [[_ t p] (re-matches #"(.+)-(\d+)" k)]
                          (TopicPartition. t (Long/parseLong p)))
                        next-offset])))))

(defn- store-doc-offsets [indexer tp-offsets]
  (db/store-index-meta indexer :crux.tx-log/consumer-state (->> tp-offsets
                                                                (into {} (map (fn [[k v]]
                                                                                [(str k) {:next-offset v}]))))))

(defn- update-doc-offsets [tp-offsets doc-records]
  (reduce (fn [tp-offsets ^ConsumerRecord record]
            (assoc tp-offsets
              (TopicPartition. (.topic record) (.partition record)) (inc (.offset record))))
          tp-offsets
          doc-records))

(defn doc-record->id+doc [^ConsumerRecord doc-record]
  [(c/new-id (.key doc-record)) (.value doc-record)])

(defn- index-doc-log [{:keys [bus object-store kv-store indexer !error]}
                      {:keys [::doc-topic ::group-id kafka-config]}]
  (let [tp-offsets (read-doc-offsets indexer)]
    (try
      (with-open [consumer (doto (create-consumer (assoc kafka-config
                                                         "group.id" (or group-id (str (UUID/randomUUID)))))
                             (subscribe-consumer #{doc-topic} tp-offsets))]
        (loop [tp-offsets tp-offsets]
          (let [tp-offsets (->> (consumer-seqs consumer (Duration/ofSeconds 1))
                                (reduce (fn [tp-offsets doc-records]
                                          (db/put-objects object-store (->> doc-records (into {} (map doc-record->id+doc))))
                                          (doto (update-doc-offsets tp-offsets doc-records)
                                            (->> (store-doc-offsets indexer))))
                                        tp-offsets))]
            (when (Thread/interrupted)
              (throw (InterruptedException.)))
            (recur tp-offsets))))
      (catch InterruptException e
        (Thread/interrupted))
      (catch InterruptedException e)
      (catch Exception e
        (reset! !error e)
        (log/error e "Error while consuming documents")))))

(def default-options
  {::bootstrap-servers {:doc "URL for connecting to Kafka i.e. \"kafka-cluster-kafka-brokers.crux.svc.cluster.local:9092\""
                        :default "localhost:9092"
                        :crux.config/type :crux.config/string}
   ::tx-topic {:doc "Kafka transaction topic"
               :default "crux-transaction-log"
               :crux.config/type :crux.config/string}
   ::doc-topic {:doc "Kafka document topic"
                :default "crux-docs"
                :crux.config/type :crux.config/string}
   ::doc-partitions {:doc "Partitions for document topic"
                     :default 1
                     :crux.config/type :crux.config/nat-int}
   ::create-topics {:doc "Create topics if they do not exist"
                    :default true
                    :crux.config/type :crux.config/boolean}
   ::replication-factor {:doc "Level of durability for Kafka"
                         :default 1
                         :crux.config/type :crux.config/nat-int}
   ::group-id {:doc "Kafka client group.id"
               :required false
               :crux.config/type :crux.config/string}
   ::kafka-properties-file {:doc "Used for supplying Kafka connection properties to the underlying Kafka API."
                            :crux.config/type :crux.config/string}
   ::kafka-properties-map {:doc "Used for supplying Kafka connection properties to the underlying Kafka API."
                           :crux.config/type [map? identity]}})

(def admin-client
  {:start-fn (fn [_ options]
               (create-admin-client (derive-kafka-config options)))
   :args default-options})

(def producer
  {:start-fn (fn [_ options]
               (create-producer (derive-kafka-config options)))
   :args default-options})

(def latest-submitted-tx-consumer
  {:start-fn (fn [_ options]
               (create-consumer (derive-kafka-config options)))
   :args default-options})

(def tx-log
  {:start-fn (fn [{:keys [::producer ::admin-client ::latest-submitted-tx-consumer]}
                  {:keys [crux.kafka/tx-topic] :as options}]
               (let [kafka-config (derive-kafka-config options)]
                 (ensure-topic-exists admin-client tx-topic tx-topic-config 1 options)
                 (ensure-tx-topic-has-single-partition admin-client tx-topic)
                 (->KafkaTxLog producer latest-submitted-tx-consumer tx-topic kafka-config)))
   :deps [::producer ::admin-client ::latest-submitted-tx-consumer]
   :args default-options})

(def document-store
  {:start-fn (fn [{::keys [producer admin-client], ::n/keys [indexer kv-store object-store bus] :as deps}
                  {::keys [doc-topic doc-partitions] :as options}]
               (let [kafka-config (derive-kafka-config options)
                     doc-store (map->KafkaDocumentStore
                                {:producer producer
                                 :doc-topic doc-topic
                                 :indexer indexer
                                 :kv-store kv-store
                                 :object-store object-store
                                 :bus bus
                                 :!indexing-error (atom nil)})]
                 (ensure-topic-exists admin-client doc-topic doc-topic-config doc-partitions options)
                 (assoc doc-store
                        :indexing-thread
                        (doto (Thread. #(index-doc-log doc-store (assoc options :kafka-config kafka-config)))
                          (.setName "crux-doc-consumer")
                          (.start)))))
   :deps [::producer ::admin-client ::n/kv-store ::n/object-store ::n/indexer ::n/bus]
   :args default-options})

(def topology
  (merge n/base-topology
         {:crux.node/tx-log tx-log
          :crux.node/document-store document-store
          ::admin-client admin-client
          ::producer producer
          ::latest-submitted-tx-consumer latest-submitted-tx-consumer}))
