(ns crux.java-api-test
  (:require [clojure.java.io :as io]
            [clojure.test :as t]
            [crux.fixtures :as fix]
            [crux.kv :as kv])
  (:import [crux.api Crux ICruxAsyncIngestAPI ICruxAPI]
           [crux.api.alpha CasOperation CruxId CruxNode DeleteOperation Document EvictOperation PutOperation Query]
           [crux.api.configuration NodeConfiguration ModuleConfiguration NodeConfiguration$Builder ModuleConfiguration$Builder]
           java.util.function.Consumer))

(defmacro consume {:style/indent 1} [[binding] & body]
  `(reify Consumer
     (~'accept [_# ~binding]
      ~@body)))

(defn start-rocks-node ^ICruxAPI [data-dir]
  (Crux/startNode
    (NodeConfiguration/build
   (consume [c]
     (doto ^NodeConfiguration$Builder c
       (.with "crux/tx-log"
              (consume [c]
                (doto ^ModuleConfiguration$Builder c
                  (.with "kv-store"
                         (consume [c]
                           (doto ^ModuleConfiguration$Builder c
                             (.module "crux.rocksdb/->kv-store")
                             (.set "db-dir" (io/file data-dir "txs"))))))))
       (.with "crux/document-store"
              (consume [c]
                (doto ^ModuleConfiguration$Builder c
                  (.with "kv-store"
                         (consume [c]
                           (doto ^ModuleConfiguration$Builder c
                             (.module "crux.rocksdb/->kv-store")
                             (.set "db-dir" (io/file data-dir "docs"))))))))
       (.with "crux/index-store"
              (consume [c]
                (doto ^ModuleConfiguration$Builder c
                  (.with "kv-store"
                         (consume [c]
                           (doto ^ModuleConfiguration$Builder c
                             (.module "crux.rocksdb/->kv-store")
                             (.set "db-dir" (io/file data-dir "indexes")))))))))))))

(defn start-rocks-ingest-node ^ICruxAsyncIngestAPI [data-dir]
  (Crux/newIngestClient
    (NodeConfiguration/build
   (consume [c]
     (doto ^NodeConfiguration$Builder c
       (.with "crux/document-store"
              (consume [c]
                (doto ^ModuleConfiguration$Builder c
                  (.with "kv-store"
                         (consume [c]
                           (doto ^ModuleConfiguration$Builder c
                             (.module "crux.rocksdb/->kv-store")
                             (.set "db-dir" (io/file data-dir "docs")))))))))))))

(t/deftest test-configure-rocks
  (fix/with-tmp-dir "data" [data-dir]
    (with-open [node (start-rocks-node data-dir)]
      (t/is (= "crux.rocksdb.RocksKv"
               (kv/kv-name (get-in node [:tx-log :kv-store]))
               (kv/kv-name (get-in node [:document-store :document-store :kv-store]))
               (kv/kv-name (get-in node [:index-store :kv-store]))))
      (t/is (= (.toPath (io/file data-dir "txs"))
               (get-in node [:tx-log :kv-store :db-dir]))))))

(t/deftest test-configure-rocks-ingest
  (fix/with-tmp-dir "data" [data-dir]
    (with-open [node (start-rocks-ingest-node data-dir)]
      (t/is (= "crux.rocksdb.RocksKv"
               (kv/kv-name (get-in node [:document-store :document-store :kv-store])))))))

(t/deftest test-java-api
  (t/testing "Can create node, transact to node, and query node"
    (with-open [node (CruxNode/startNode)]
      (t/testing "Can create node"
        (t/is node))

      (t/testing "Transactions"
        (let [id (CruxId/cruxId "test-id")
              doc (-> (Document/document id)
                      (.with "Key" "Value1"))
              doc2 (-> (Document/document id)
                       (.with "Key" "Value2"))]

          (t/testing "Can create Documents/id"
            (t/is id)
            (t/is doc)
            (t/is doc2))

          (let [putOp (PutOperation/putOp doc)
                casOp (CasOperation/casOp doc doc2)
                delOp (DeleteOperation/deleteOp id)
                evictOp (EvictOperation/evictOp id)]

            (t/testing "Can create Operations"
              (t/is putOp)
              (t/is casOp)
              (t/is delOp)
              (t/is evictOp))

            (t/testing "Can submit Transactions"
              (t/is (.submitTx node (fix/vec->array-list [putOp])))
              (Thread/sleep 300)

              (t/is (.submitTx node (fix/vec->array-list [casOp])))
              (Thread/sleep 300)

              (t/is (.submitTx node (fix/vec->array-list [delOp])))
              (Thread/sleep 300)

              (t/is (.submitTx node (fix/vec->array-list [evictOp])))
              (Thread/sleep 300)))))

      (t/testing "Queries"
        (let [query (-> (Query/find "[e]")
                        (.where "[[e :crux.db/id _]]"))
              db (.db node)]
          (t/testing "Can create query"
            (t/is query))
          (t/testing "Can get a database out of node"
            (t/is db))
          (t/testing "Can query database"
            (t/is (= [] (.query db query))))))

      (t/testing "Can close node"
        (t/is (nil? (.close node))))

      (t/testing "Calling function on closed node creates an exception"
        (t/is (thrown-with-msg? IllegalStateException
                                #"Crux node is closed"
                                (.db node)))))))
