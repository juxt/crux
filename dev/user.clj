(ns user
  (:require [crux.api :as crux]
            [clojure.tools.namespace.repl :as ctn]
            [integrant.core :as i]
            [integrant.repl.state :refer [system]]
            [integrant.repl :as ir :refer [clear go suspend resume halt reset reset-all]])
  (:import (java.io Closeable)))

(ctn/disable-reload!)

(defmethod i/init-key :node [_ node-opts]
  (crux/start-node node-opts))

(defmethod i/halt-key! :node [_ ^Closeable node]
  (.close node))

(def config
  {:node (merge {:crux.node/topology 'crux.standalone/topology
                 :crux.kv/db-dir "dev/dev-node/db-dir"
                 :crux.standalone/event-log-dir "dev/dev-node/event-log"})})

(ir/set-prep! (fn [] config))
