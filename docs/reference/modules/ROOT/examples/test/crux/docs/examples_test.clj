(ns crux.docs.examples-test
  (:require [clojure.java.io :as io]
            [crux.api :as crux]))

;; tag::require-ek[]
(require '[crux.kafka.embedded :as ek])
;; end::require-ek[]

;; tag::ek-example[]
(defn start-embedded-kafka [kafka-port storage-dir]
  (ek/start-embedded-kafka {:crux.kafka.embedded/zookeeper-data-dir (io/file storage-dir "zk-data")
                            :crux.kafka.embedded/kafka-log-dir (io/file storage-dir "kafka-log")
                            :crux.kafka.embedded/kafka-port kafka-port}))
;; end::ek-example[]

(defn stop-embedded-kafka [^java.io.Closeable embedded-kafka]
;; tag::ek-close[]
(.close embedded-kafka)
;; end::ek-close[]
)

;; tag::start-http-client[]
(defn start-http-client [port]
  (crux/new-api-client (str "http://localhost:" port)))
;; end::start-http-client[]

(defn example-query-entity [node]
;; tag::query-entity[]
(crux/entity (crux/db node) :dbpedia.resource/Pablo-Picasso)
;; end::query-entity[]
)

(defn example-query-valid-time [node]
;; tag::query-valid-time[]
(crux/q (crux/db node #inst "2018-05-19T09:20:27.966-00:00")
        '{:find [e]
          :where [[e :name "Pablo"]]})
;; end::query-valid-time[]
)



#_(comment
;; tag::history-full[]
(api/submit-tx
  node
  [[:crux.tx/put
    {:crux.db/id :ids.persons/Jeff
     :person/name "Jeff"
     :person/wealth 100}
    #inst "2018-05-18T09:20:27.966"]
   [:crux.tx/put
    {:crux.db/id :ids.persons/Jeff
     :person/name "Jeff"
     :person/wealth 1000}
    #inst "2015-05-18T09:20:27.966"]])

; yields
{:crux.tx/tx-id 1555314836178,
 :crux.tx/tx-time #inst "2019-04-15T07:53:56.178-00:00"}

; Returning the history in descending order
; To return in ascending order, use :asc in place of :desc
(api/entity-history (api/db node) :ids.persons/Jeff :desc)

; yields
[{:crux.tx/tx-time #inst "2019-04-15T07:53:55.817-00:00",
  :crux.tx/tx-id 1555314835817,
  :crux.db/valid-time #inst "2018-05-18T09:20:27.966-00:00",
  :crux.db/content-hash ; sha1 hash of document contents
  "6ca48d3bf05a16cd8d30e6b466f76d5cc281b561"}
 {:crux.tx/tx-time #inst "2019-04-15T07:53:56.178-00:00",
  :crux.tx/tx-id 1555314836178,
  :crux.db/valid-time #inst "2015-05-18T09:20:27.966-00:00",
  :crux.db/content-hash "a95f149636e0a10a78452298e2135791c0203529"}]
;; end::history-full[]

;; tag::history-with-docs[]
(api/entity-history (api/db node) :ids.persons/Jeff :desc {:with-docs? true})

; yields
[{:crux.tx/tx-time #inst "2019-04-15T07:53:55.817-00:00",
  :crux.tx/tx-id 1555314835817,
  :crux.db/valid-time #inst "2018-05-18T09:20:27.966-00:00",
  :crux.db/content-hash
  "6ca48d3bf05a16cd8d30e6b466f76d5cc281b561"
  :crux.db/doc
  {:crux.db/id :ids.persons/Jeff
   :person/name "Jeff"
   :person/wealth 100}}
 {:crux.tx/tx-time #inst "2019-04-15T07:53:56.178-00:00",
  :crux.tx/tx-id 1555314836178,
  :crux.db/valid-time #inst "2015-05-18T09:20:27.966-00:00",
  :crux.db/content-hash "a95f149636e0a10a78452298e2135791c0203529"
  :crux.db/doc
  {:crux.db/id :ids.persons/Jeff
   :person/name "Jeff"
   :person/wealth 1000}}]
;; end::history-with-docs[]

;; tag::history-range[]

; Passing the additional 'opts' map with the start/end bounds.
; As we are returning results in :asc order, the :start map contains the earlier coordinates -
; If returning history range in descending order, we pass the later coordinates to the :start map
(api/entity-history
 (api/db node)
 :ids.persons/Jeff
 :asc
 {:start {:crux.db/valid-time #inst "2015-05-18T09:20:27.966" ; valid-time-start
          :crux.tx/tx-time #inst "2015-05-18T09:20:27.966"} ; tx-time-start
  :end {:crux.db/valid-time #inst "2020-05-18T09:20:27.966" ; valid-time-end
        :crux.tx/tx-time #inst "2020-05-18T09:20:27.966"} ; tx-time-end
  })

; yields
[{:crux.tx/tx-time #inst "2019-04-15T07:53:56.178-00:00",
  :crux.tx/tx-id 1555314836178,
  :crux.db/valid-time #inst "2015-05-18T09:20:27.966-00:00",
  :crux.db/content-hash
  "a95f149636e0a10a78452298e2135791c0203529"}
 {:crux.tx/tx-time #inst "2019-04-15T07:53:55.817-00:00",
  :crux.tx/tx-id 1555314835817
  :crux.db/valid-time #inst "2018-05-18T09:20:27.966-00:00",
  :crux.db/content-hash "6ca48d3bf05a16cd8d30e6b466f76d5cc281b561"}]

;; end::history-range[]
)
