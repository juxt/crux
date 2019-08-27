(ns ivan.http-server
  "Barbarious bootstrap for http server"
  (:require [crux.api :as api]
            [crux.http-server :as http-server]
            [crux.bootstrap.remote-api-client :as http-client]))


(def opts
  {:kv-backend    "crux.kv.rocksdb.RocksKv"
   :event-log-dir "data/eventlog-1"
   :db-dir        "data/db-dir-1"})

(def simple-node
  (api/start-standalone-node opts))

(def srv
  (http-server/start-http-server simple-node))


(def client-node
  (http-client/new-api-client "localhost:3000"))

(api/submit-tx
  simple-node
  [[:crux.tx/put
    {:crux.db/id :ids/raptor}]
   [:crux.tx/put
    {:crux.db/id :ids/owl}]])

(api/history-range
  simple-node
  :ids/owl
  nil nil nil nil)

(api/document simple-node "686c3e1f00fb8ccabd43e93f5cd2da546d50d80d")

(api/q (api/db simple-node)
       '{:find [e]
         :where
         [[e :crux.db/id _]]})

(api/documents
  simple-node
  #{"686c3e1f00fb8ccabd43e93f5cd2da546d50d80d"
    "773d1c878c512d5d50bb1e74e46d4e5e315046de"})
