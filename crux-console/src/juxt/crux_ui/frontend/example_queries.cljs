(ns juxt.crux-ui.frontend.example-queries
  (:require [medley.core :as m]))


(def currencies
  [:currency/usd
   :currency/eur
   :currency/gbp
   :currency/chf
   :currency/rub
   :currency/yen
   :currency/cny])

(def industries
  [:pharma
   :tech
   :oil
   :agriculture
   :chem
   :industry
   :fashion])


(def ctr (atom 1))

(defn get-ctr []
  (swap! ctr inc)
  @ctr)

(def used-ids (atom []))

(defn- -gen-id []
  (keyword 'ids (str (name (rand-nth industries)) "-ticker-" (get-ctr))))

(defn- gen-id []
  (let [id (-gen-id)]
    (swap! used-ids conj id)
    id))

(defn- get-id []
  (if (empty? @used-ids)
    (-gen-id)
    (rand-nth @used-ids)))

(defn- gen-vt []
  #inst "2018-09-12T03:30")

(defn- gen-ticker []
  {:crux.db/id (gen-id)
   :price      (inc (rand-int 100))
   :currency   (rand-nth currencies)})

(def generators
  {:examples/put (fn [] [[:crux.tx/put (gen-ticker)]])
   :examples/put-10 (fn [] (mapv (fn [_] [:crux.tx/put (gen-ticker)]) (range 10)))
   :examples/put-w-valid (fn [] [[:crux.tx/put (gen-ticker) (gen-vt)]])

   :examples/query
   (fn []
     '{:find [e]
       :where [[e :crux.db/id _]]})

   :examples/crux-night
   (fn [] [[:crux.tx/put {:crux.db/id :github/some-username :crux-night/question "Where can I find the docs for Crux?"}]])

   :examples/query-w-full-res
   (fn []
     '{:find [e]
       :where [[e :crux.db/id _]]
       :full-results? true})

   :examples/delete (fn [] [[:crux.tx/delete (get-id)]])
   :examples/evict (fn [] [[:crux.tx/evict (get-id)]])
   :examples/evict-w-valid (fn [] [[:crux.tx/evict (get-id) (gen-vt)]])})


(def examples
  [{:title "[crux.tx/put :some-data]"
    :generator (:examples/put generators)}
   {:title "put 10"
    :generator (:examples/put-10 generators)}
   {:title "put with valid time"
    :generator (:examples/put-w-valid generators)}
   {:title "simple query"
    :generator (:examples/query generators)}
   {:title "query with full-results"
    :generator (:examples/query-w-full-res generators)}
   {:title "Hello Crux Night"
    :generator (:examples/crux-night generators)}
   {:title "delete"
    :generator (:examples/delete generators)}
   {:title "evict"
    :generator (:examples/evict generators)}
   {:title "evict with vt"
    :generator (:examples/evict-w-valid generators)}])

(defn generate [ex-id]
  (if-let [gen-fn (:generator (m/find-first #(= ex-id (:title %)) examples))]
    (gen-fn)))

