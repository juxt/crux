(ns crux.compaction-test
  (:require [clojure.test :as t]
            [crux.api :as api]
            [crux.fixtures :as f]
            [crux.io :as cio]
            [crux.jdbc :as j]
            [next.jdbc :as jdbc]
            [clojure.java.io :as io]
            [crux.node :as n])
  (:import crux.api.Crux
           [java.nio.file Files]))

(def ^:dynamic *db-name*)

(defn- with-prep-for-tests [f]
  (f/with-tmp-dir "compaction-test" [dir]
    (let [db-name (.getPath (io/file dir "cruxtest"))
          ds (jdbc/get-datasource {:dbtype "h2", :dbname db-name})]
      (j/prep-for-tests! "h2" ds)
      (binding [*db-name* db-name]
        (f)))))

(t/use-fixtures :each with-prep-for-tests)

(t/deftest test-compaction-leaves-replayable-log
  (let [db-dir (str (cio/create-tmpdir "kv-store"))
        opts {:crux.node/topology 'crux.jdbc/topology
              :crux.jdbc/dbtype "h2"
              :crux.jdbc/dbname *db-name*
              :crux.kv/db-dir db-dir
              :crux.kv/kv-store "crux.kv.memdb/kv"}]
    (try
      (let [api (api/start-node opts)
            tx (api/submit-tx api [[:crux.tx/put {:crux.db/id :foo}]])]
        (api/await-tx api tx)
        (f/transact! api [{:crux.db/id :foo}])
        (.close api)

        (with-open [api2 (api/start-node opts)]
          (api/await-tx api2 tx)
          (t/is (= 2 (count (api/history api2 :foo))))))
      (finally
        (cio/delete-dir db-dir)))))
