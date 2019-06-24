(ns ivan
  (:require [crux.api :as api]
            [crux.codec :as c]
            [crux.bootstrap.standalone :as bs]
            [clojure.spec.alpha :as s]))

;;;;; This is not a benchmark
;;;;; Rather a sanity check tool for internal changes
;;;;; Use profiles +test

(def currencies
  (mapv #(assoc % :crux.db/id (:currency/id %))
        [{:currency/id :currency.id/eur
          :currency/name "Euro"}
         {:currency/id :currency.id/usd
          :currency/name "USD"}
         {:currency/id :currency.id/chf
          :currency/name "Swiss Franc"}
         {:currency/id :currency.id/gbp
          :currency/name "British Pound"}
         {:currency/id :currency.id/rub
          :currency/name "Russian Rouble"}
         {:currency/id :currency.id/cny
          :currency/name "Chinese Yuan"}
         {:currency/id :currency.id/jpy
          :currency/name "Japanese Yen"}]))

(def currencies-by-id
  (reduce (fn [m v] (assoc m (:crux.db/id v) v)) {} currencies))

(def id-buffer->id
  (let [ks (keys currencies-by-id)]
    (zipmap (map c/->id-buffer ks) ks)))

(s/assert ::bs/standalone-options {:db-dir "ee" :kv-backend "ee"})

(defn gen-stock [i]
  (let [id (keyword "stock.id" (str "company-" i))]
    {:stock/id id
     :crux.db/id id
     :stock/price (rand-int 1000)
     :stock/currency-id (:currency/id (rand-nth currencies))
     :stock/backup-currency-id (:currency/id (rand-nth currencies))}))

(def stocks-count 10000)
(def query-size 1000)

(defn with-stocks-data [f]
  (api/submit-tx f/*api* (f/maps->tx-ops currencies))
  (println "stocks count" stocks-count)
  (let [ctr (atom 0)
        partitions-total (/ stocks-count 1000)]
    (doseq [stocks-batch (partition-all 1000 (map gen-stock (range stocks-count)))]
      (swap! ctr inc)
      (println "partition " @ctr "/" partitions-total)
      (api/submit-tx f/*api* (f/maps->tx-ops stocks-batch))))
  (api/sync f/*api* (java.time.Duration/ofMinutes 20))
  (f))

(t/use-fixtures :once
                f/with-embedded-kafka-cluster
                f/with-kafka-client
                f/with-cluster-node
                with-stocks-data)

(comment

  (def system
    (api/start-standalone-system
      {:kv-backend "crux.kv.memdb.MemKv"
       :db-dir     "data/db-dir-1"}))

  (binding [f/*api* system]
    (with-stocks-data println))

  stocks

  (api/q (api/db system) query))

(def query-1000
  '[:find stock-id currency-id
    :limit 1000
    :where
    [currency-id :currency/name currency-name]
    [stock-id :stock/currency-id currency-id]])

(def query-10000
  '[:find stock-id currency-id
    :limit 10000
    :where
    [currency-id :currency/name currency-name]
    [stock-id :stock/currency-id currency-id]])

(def query-100000
  '[:find stock-id currency-id
    :limit 100000
    :where
    [currency-id :currency/name currency-name]
    [stock-id :stock/currency-id currency-id]])

(def query-2
  '[:find stock-id currency-id
    :where
    [currency-id :currency/name "Euro"]
    [stock-id :stock/currency-id currency-id]])

(def query-3
  '[:find stock-id currency-name
    :where
    [currency-id :currency/name currency-name]
    [stock-id :stock/currency-id currency-id]
    (or [stock-id :stock/backup-currency-id "USD"]
        [stock-id :stock/backup-currency-id "Euro"])])

(def sample-size 10)

(defn not-really-benchmarking [db n]
  (for [_ (range n)]
    (bench/duration-millis (api/q db query-100000))))

(t/deftest test-stocks-query
  (println "running a stocks query with join to currencies")
  (let [db (api/db f/*api*)
        -dry (bench/duration-millis (api/q db query-1000))
        durations (not-really-benchmarking db sample-size)]
    (println "with cache durations in millis are" durations)
    (println "avg" (/ (apply + durations) sample-size)))
  (t/is (min stocks-count 1000)   (count (api/q (api/db f/*api*) query-1000)))
  (t/is (min stocks-count 10000)  (count (api/q (api/db f/*api*) query-10000)))
  (t/is (min stocks-count 100000) (count (api/q (api/db f/*api*) query-100000))))

