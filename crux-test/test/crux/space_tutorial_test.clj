(ns crux.space-tutorial-test
  (:require [clojure.test :as t]
            [crux.api :as crux]
            [crux.io :as cio]
            [crux.fixtures :as fix :refer [*api*]]
            [clojure.java.io :as io]
            [crux.codec :as c])
  (:import java.io.Closeable))

(t/use-fixtures :each fix/with-node)

(def manifest
  {:crux.db/id :manifest
   :pilot-name "Johanna"
   :id/rocket "SB002-sol"
   :id/employee "22910x2"
   :badges "SETUP"
   :cargo ["stereo" "gold fish" "slippers" "secret note"]})

(defn filter-appearance [description]
  (crux/q (crux/db *api*)
          {:find '[name IUPAC]
           :where '[[e :common-name name]
                    [e :IUPAC-name IUPAC]
                    [e :appearance appearance]]
           :args [{'appearance description}]}))

(defn put-all! [docs]
  (fix/submit+await-tx (for [doc docs]
                         [:crux.tx/put doc])))

(defn stock-check
  [company-id item]
  {:result (crux/q (crux/db *api*)
                   {:find '[name funds stock]
                    :where ['[e :company-name name]
                            '[e :credits funds]
                            ['e item 'stock]]
                    :args [{'e company-id}]})
   :item item})

(defn format-stock-check [{:keys [result item] :as stock-check}]
  (for [[name funds commod] result]
    (str "Name: " name ", Funds: " funds ", " item " " commod)))


(defn full-query [node]
  (crux/q (crux/db node)
          '{:find [(pull id [*])]
            :where [[e :crux.db/id id]]}))

(t/deftest space-tutorial-test
  (t/testing "earth-test"
    (fix/submit+await-tx [[:crux.tx/put manifest]])

    (t/is (= {:crux.db/id :manifest,
              :pilot-name "Johanna",
              :id/rocket "SB002-sol",
              :id/employee "22910x2",
              :badges "SETUP",
              :cargo ["stereo" "gold fish" "slippers" "secret note"]}
             (crux/entity (crux/db *api*) :manifest)))

    (t/is (= #{["secret note"]}
             (crux/q (crux/db *api*)
                     {:find '[belongings]
                      :where '[[e :cargo belongings]]
                      :args [{'belongings "secret note"}]}))))

  (t/testing "pluto-tests"
    (fix/submit+await-tx [[:crux.tx/put
                           {:crux.db/id :commodity/Pu
                            :common-name "Plutonium"
                            :type :element/metal
                            :density 19.816
                            :radioactive true}]

                          [:crux.tx/put
                           {:crux.db/id :commodity/N
                            :common-name "Nitrogen"
                            :type :element/gas
                            :density 1.2506
                            :radioactive false}]

                          [:crux.tx/put
                           {:crux.db/id :commodity/CH4
                            :common-name "Methane"
                            :type :molecule/gas
                            :density 0.717
                            :radioactive false}]])


    (fix/submit+await-tx [[:crux.tx/put {:crux.db/id :stock/Pu
                                         :commod :commodity/Pu
                                         :weight-ton 21}
                           #inst "2115-02-13T18"] ;; valid-time

                          [:crux.tx/put {:crux.db/id :stock/Pu
                                         :commod :commodity/Pu
                                         :weight-ton 23}
                           #inst "2115-02-14T18"]

                          [:crux.tx/put {:crux.db/id :stock/Pu
                                         :commod :commodity/Pu
                                         :weight-ton 22.2}
                           #inst "2115-02-15T18"]

                          [:crux.tx/put {:crux.db/id :stock/Pu
                                         :commod :commodity/Pu
                                         :weight-ton 24}
                           #inst "2115-02-18T18"]

                          [:crux.tx/put {:crux.db/id :stock/Pu
                                         :commod :commodity/Pu
                                         :weight-ton 24.9}
                           #inst "2115-02-19T18"]])

    (fix/submit+await-tx [[:crux.tx/put {:crux.db/id :stock/N
                                         :commod :commodity/N
                                         :weight-ton 3}
                           #inst "2115-02-13T18" ;; start valid-time
                           #inst "2115-02-19T18"] ;; end valid-time

                          [:crux.tx/put {:crux.db/id :stock/CH4
                                         :commod :commodity/CH4
                                         :weight-ton 92}
                           #inst "2115-02-15T18"
                           #inst "2115-02-19T18"]])

    (t/is (= {:crux.db/id :stock/Pu, :commod :commodity/Pu, :weight-ton 21}
             (crux/entity (crux/db *api* #inst "2115-02-14") :stock/Pu)))
    (t/is (= {:crux.db/id :stock/Pu, :commod :commodity/Pu, :weight-ton 22.2}
             (crux/entity (crux/db *api* #inst "2115-02-18") :stock/Pu)))

    (fix/submit+await-tx [[:crux.tx/put (assoc manifest :badges ["SETUP" "PUT"])]])

    (t/is (= {:crux.db/id :manifest,
              :pilot-name "Johanna",
              :id/rocket "SB002-sol",
              :id/employee "22910x2",
              :badges ["SETUP" "PUT"],
              :cargo ["stereo" "gold fish" "slippers" "secret note"]}
             (crux/entity (crux/db *api*) :manifest))))

  (t/testing "mercury-tests"
    (put-all! [{:crux.db/id :commodity/Pu
                :common-name "Plutonium"
                :type :element/metal
                :density 19.816
                :radioactive true}

               {:crux.db/id :commodity/N
                :common-name "Nitrogen"
                :type :element/gas
                :density 1.2506
                :radioactive false}

               {:crux.db/id :commodity/CH4
                :common-name "Methane"
                :type :molecule/gas
                :density 0.717
                :radioactive false}

               {:crux.db/id :commodity/Au
                :common-name "Gold"
                :type :element/metal
                :density 19.300
                :radioactive false}

               {:crux.db/id :commodity/C
                :common-name "Carbon"
                :type :element/non-metal
                :density 2.267
                :radioactive false}

               {:crux.db/id :commodity/borax
                :common-name "Borax"
                :IUPAC-name "Sodium tetraborate decahydrate"
                :other-names ["Borax decahydrate" "sodium borate" "sodium tetraborate" "disodium tetraborate"]
                :type :mineral/solid
                :appearance "white solid"
                :density 1.73
                :radioactive false}])

    (t/is (={:crux.db/id :commodity/borax
             :common-name "Borax"
             :IUPAC-name "Sodium tetraborate decahydrate"
             :other-names ["Borax decahydrate" "sodium borate" "sodium tetraborate" "disodium tetraborate"]
             :type :mineral/solid
             :appearance "white solid"
             :density 1.73
             :radioactive false}
            (crux/entity (crux/db *api*) :commodity/borax)))
    (t/is (= #{[:commodity/Pu] [:commodity/Au]}
             (crux/q (crux/db *api*)
                     '{:find [element]
                       :where [[element :type :element/metal]]})))
    (t/is (=
           (crux/q (crux/db *api*)
                   '{:find [element]
                     :where [[element :type :element/metal]]} )

           (crux/q (crux/db *api*)
                   {:find '[element]
                    :where '[[element :type :element/metal]]} )

           (crux/q (crux/db *api*)
                   (quote
                    {:find [element]
                     :where [[element :type :element/metal]]}) )))

    (t/is (= #{["Gold"] ["Plutonium"]}
             (crux/q (crux/db *api*)
                     '{:find [name]
                       :where [[e :type :element/metal]
                               [e :common-name name]]} )))

    (t/is (= #{["Nitrogen" 1.2506] ["Carbon" 2.267] ["Methane" 0.717] ["Borax" 1.73] ["Gold" 19.3] ["Plutonium" 19.816]}
             (crux/q (crux/db *api*)
                     '{:find [name rho]
                       :where [[e :density rho]
                               [e :common-name name]]})))

    (t/is (= #{["Plutonium"]}
             (crux/q (crux/db *api*)
                     '{:find [name]
                       :where [[e :common-name name]
                               [e :radioactive true]]})))

    (t/is (= #{["Gold"] ["Plutonium"]}
             (crux/q (crux/db *api*)
                     {:find '[name]
                      :where '[[e :type t]
                               [e :common-name name]]
                      :args [{'t :element/metal}]})))

    (t/is (= #{["Gold"] ["Plutonium"]}
             (crux/q (crux/db *api*)
                     {:find '[name]
                      :where '[[e :type t]
                               [e :common-name name]]
                      :args [{'t :element/metal}]})))

    (t/is (= (filter-appearance "white solid")
             #{["Borax" "Sodium tetraborate decahydrate"]}
             ))

    (fix/submit+await-tx [[:crux.tx/put (assoc manifest :badges ["SETUP" "PUT" "DATALOG-QUERIES"])]])

    (t/is (= {:crux.db/id :manifest,
              :pilot-name "Johanna",
              :id/rocket "SB002-sol",
              :id/employee "22910x2",
              :badges ["SETUP" "PUT" "DATALOG-QUERIES"],
              :cargo ["stereo" "gold fish" "slippers" "secret note"]}
             (crux/entity (crux/db *api*) :manifest))))

  (t/testing "neptune-test"
    (fix/submit+await-tx [[:crux.tx/put
                           {:crux.db/id :consumer/RJ29sUU
                            :consumer-id :RJ29sUU
                            :first-name "Jay"
                            :last-name "Rose"
                            :cover? true
                            :cover-type :Full}
                           #inst "2114-12-03"]])
    (fix/submit+await-tx [[:crux.tx/put ;; <1>
                           {:crux.db/id :consumer/RJ29sUU
                            :consumer-id :RJ29sUU
                            :first-name "Jay"
                            :last-name "Rose"
                            :cover? true
                            :cover-type :Full}
                           #inst "2113-12-03" ;; Valid time start
                           #inst "2114-12-03"] ;; Valid time end

                          [:crux.tx/put ;; <2>
                           {:crux.db/id :consumer/RJ29sUU
                            :consumer-id :RJ29sUU
                            :first-name "Jay"
                            :last-name "Rose"
                            :cover? true
                            :cover-type :Full}
                           #inst "2112-12-03"
                           #inst "2113-12-03"]

                          [:crux.tx/put ;; <3>
                           {:crux.db/id :consumer/RJ29sUU
                            :consumer-id :RJ29sUU
                            :first-name "Jay"
                            :last-name "Rose"
                            :cover? false}
                           #inst "2112-06-03"
                           #inst "2112-12-02"]

                          [:crux.tx/put ;; <4>
                           {:crux.db/id :consumer/RJ29sUU
                            :consumer-id :RJ29sUU
                            :first-name "Jay"
                            :last-name "Rose"
                            :cover? true
                            :cover-type :Promotional}
                           #inst "2111-06-03"
                           #inst "2112-06-03"]])

    (t/is (= #{[true :Full]}
             (crux/q (crux/db *api* #inst "2115-07-03")
                     '{:find [cover type]
                       :where [[e :consumer-id :RJ29sUU]
                               [e :cover? cover]
                               [e :cover-type type]]})))

    (t/is (= #{}
             (crux/q (crux/db *api* #inst "2112-07-03")
                     '{:find [cover type]
                       :where [[e :consumer-id :RJ29sUU]
                               [e :cover? cover]
                               [e :cover-type type]]})))

    (t/is (= #{[true :Promotional]}
             (crux/q (crux/db *api* #inst "2111-07-03")
                     '{:find [cover type]
                       :where [[e :consumer-id :RJ29sUU]
                               [e :cover? cover]
                               [e :cover-type type]]})))
    (fix/submit+await-tx [[:crux.tx/put
                           (assoc manifest :badges ["SETUP" "PUT" "DATALOG-QUERIES"
                                                    "BITEMP"])]])

    (t/is (= {:crux.db/id :manifest,
              :pilot-name "Johanna",
              :id/rocket "SB002-sol",
              :id/employee "22910x2",
              :badges ["SETUP" "PUT" "DATALOG-QUERIES" "BITEMP"],
              :cargo ["stereo" "gold fish" "slippers" "secret note"]}
             (crux/entity (crux/db *api*) :manifest))))

  (t/testing "saturn-tests"
    (put-all! [{:crux.db/id :gold-harmony
                :company-name "Gold Harmony"
                :seller? true
                :buyer? false
                :units/Au 10211
                :credits 51}

               {:crux.db/id :tombaugh-resources
                :company-name "Tombaugh Resources Ltd."
                :seller? true
                :buyer? false
                :units/Pu 50
                :units/N 3
                :units/CH4 92
                :credits 51}

               {:crux.db/id :encompass-trade
                :company-name "Encompass Trade"
                :seller? true
                :buyer? true
                :units/Au 10
                :units/Pu 5
                :units/CH4 211
                :credits 1002}

               {:crux.db/id :blue-energy
                :seller? false
                :buyer? true
                :company-name "Blue Energy"
                :credits 1000}])

    (fix/submit+await-tx [[:crux.tx/match
                           :blue-energy
                           {:crux.db/id :blue-energy
                            :seller? false
                            :buyer? true
                            :company-name "Blue Energy"
                            :credits 1000}]
                          [:crux.tx/put
                           {:crux.db/id :blue-energy
                            :seller? false
                            :buyer? true
                            :company-name "Blue Energy"
                            :credits 900
                            :units/CH4 10}]

                          [:crux.tx/match
                           :tombaugh-resources
                           {:crux.db/id :tombaugh-resources
                            :company-name "Tombaugh Resources Ltd."
                            :seller? true
                            :buyer? false
                            :units/Pu 50
                            :units/N 3
                            :units/CH4 92
                            :credits 51}]
                          [:crux.tx/put
                           {:crux.db/id :tombaugh-resources
                            :company-name "Tombaugh Resources Ltd."
                            :seller? true
                            :buyer? false
                            :units/Pu 50
                            :units/N 3
                            :units/CH4 82
                            :credits 151}]])
    (t/is (= ["Name: Tombaugh Resources Ltd., Funds: 151, :units/CH4 82"]
             (format-stock-check (stock-check :tombaugh-resources :units/CH4))))

    (t/is (= ["Name: Blue Energy, Funds: 900, :units/CH4 10"]
             (format-stock-check (stock-check :blue-energy :units/CH4))))
    (fix/submit+await-tx [[:crux.tx/match
                           :gold-harmony
                           ;; Old doc
                           {:crux.db/id :gold-harmony
                            :company-name "Gold Harmony"
                            :seller? true
                            :buyer? false
                            :units/Au 10211
                            :credits 51}]
                          [:crux.tx/put
                           ;; New doc
                           {:crux.db/id :gold-harmony
                            :company-name "Gold Harmony"
                            :seller? true
                            :buyer? false
                            :units/Au 211
                            :credits 51}]

                          [:crux.tx/match
                           :encompass-trade
                           ;; Old doc
                           {:crux.db/id :encompass-trade
                            :company-name "Encompass Trade"
                            :seller? true
                            :buyer? true
                            :units/Au 10
                            :units/Pu 5
                            :units/CH4 211
                            :credits 100002}]
                          [:crux.tx/put
                           ;; New doc
                           {:crux.db/id :encompass-trade
                            :company-name "Encompass Trade"
                            :seller? true
                            :buyer? true
                            :units/Au 10010
                            :units/Pu 5
                            :units/CH4 211
                            :credits 1002}]])

    (t/is (= '("Name: Gold Harmony, Funds: 51, :units/Au 10211")
             (format-stock-check (stock-check :gold-harmony :units/Au))))

    (t/is (= '("Name: Encompass Trade, Funds: 1002, :units/Au 10")
             (format-stock-check (stock-check :encompass-trade :units/Au))))

    (fix/submit+await-tx [[:crux.tx/put (assoc manifest :badges ["SETUP" "PUT" "DATALOG-QUERIES" "BITEMP" "MATCH"])]])

    (t/is (= {:crux.db/id :manifest,
              :pilot-name "Johanna",
              :id/rocket "SB002-sol",
              :id/employee "22910x2",
              :badges ["SETUP" "PUT" "DATALOG-QUERIES" "BITEMP" "MATCH"],
              :cargo ["stereo" "gold fish" "slippers" "secret note"]}
             (crux/entity (crux/db *api*) :manifest)))))

(t/deftest jupiter-tests
  (let [doc1 {:crux.db/id :kaarlang/clients, :clients [:encompass-trade]}
        doc2 {:crux.db/id :kaarlang/clients, :clients [:encompass-trade :blue-energy]}
        doc3 {:crux.db/id :kaarlang/clients, :clients [:blue-energy]}
        doc4 {:crux.db/id :kaarlang/clients, :clients [:blue-energy :gold-harmony :tombaugh-resources]}]
    (fix/submit+await-tx [[:crux.tx/put doc1 #inst "2110-01-01T09" #inst "2111-01-01T09"]
                          [:crux.tx/put doc2 #inst "2111-01-01T09" #inst "2113-01-01T09"]
                          [:crux.tx/put doc3 #inst "2113-01-01T09" #inst "2114-01-01T09"]
                          [:crux.tx/put doc4 #inst "2114-01-01T09" #inst "2115-01-01T09"]])

    (t/is (= doc4 (crux/entity (crux/db *api* #inst "2114-01-01T09") :kaarlang/clients)))

    (t/is (= [{:crux.tx/tx-id 0, :crux.db/valid-time #inst "2110-01-01T09", :crux.db/content-hash (c/new-id doc1), :crux.db/doc doc1}
              {:crux.tx/tx-id 0, :crux.db/valid-time #inst "2111-01-01T09", :crux.db/content-hash (c/new-id doc2), :crux.db/doc doc2}
              {:crux.tx/tx-id 0, :crux.db/valid-time #inst "2113-01-01T09", :crux.db/content-hash (c/new-id doc3), :crux.db/doc doc3}
              {:crux.tx/tx-id 0, :crux.db/valid-time #inst "2114-01-01T09", :crux.db/content-hash (c/new-id doc4), :crux.db/doc doc4}
              {:crux.tx/tx-id 0, :crux.db/valid-time #inst "2115-01-01T09", :crux.db/content-hash (c/new-id c/nil-id-buffer), :crux.db/doc nil}]

             (->> (crux/entity-history (crux/db *api* #inst "2116-01-01T09") :kaarlang/clients :asc {:with-docs? true})
                  (mapv #(dissoc % :crux.tx/tx-time)))))

    (fix/submit+await-tx [[:crux.tx/delete :kaarlang/clients #inst "2110-01-01" #inst "2116-01-01"]])

    (t/is nil? (crux/entity (crux/db *api* #inst "2114-01-01T09") :kaarlang/clients))

    (t/is (= [{:crux.tx/tx-id 1, :crux.db/valid-time #inst "2110-01-01T00", :crux.db/content-hash (c/new-id c/nil-id-buffer)}
              {:crux.tx/tx-id 1, :crux.db/valid-time #inst "2110-01-01T09", :crux.db/content-hash (c/new-id c/nil-id-buffer)}
              {:crux.tx/tx-id 1, :crux.db/valid-time #inst "2111-01-01T09", :crux.db/content-hash (c/new-id c/nil-id-buffer)}
              {:crux.tx/tx-id 1, :crux.db/valid-time #inst "2113-01-01T09", :crux.db/content-hash (c/new-id c/nil-id-buffer)}
              {:crux.tx/tx-id 1, :crux.db/valid-time #inst "2114-01-01T09", :crux.db/content-hash (c/new-id c/nil-id-buffer)}
              {:crux.tx/tx-id 1, :crux.db/valid-time #inst "2115-01-01T09", :crux.db/content-hash (c/new-id c/nil-id-buffer)}
              {:crux.tx/tx-id 0, :crux.db/valid-time #inst "2116-01-01T00", :crux.db/content-hash (c/new-id c/nil-id-buffer)}]

             (->> (crux/entity-history (crux/db *api* #inst "2116-01-01T09") :kaarlang/clients :asc {:with-docs? false})
                  (mapv #(dissoc % :crux.tx/tx-time)))))))

(t/deftest Oumuamua-test
  (fix/submit+await-tx [[:crux.tx/put {:crux.db/id :person/kaarlang
                                       :full-name "Kaarlang"
                                       :origin-planet "Mars"
                                       :identity-tag :KA01299242093
                                       :DOB #inst "2040-11-23"}]

                        [:crux.tx/put {:crux.db/id :person/ilex
                                       :full-name "Ilex Jefferson"
                                       :origin-planet "Venus"
                                       :identity-tag :IJ01222212454
                                       :DOB #inst "2061-02-17"}]

                        [:crux.tx/put {:crux.db/id :person/thadd
                                       :full-name "Thad Christover"
                                       :origin-moon "Titan"
                                       :identity-tag :IJ01222212454
                                       :DOB #inst "2101-01-01"}]

                        [:crux.tx/put {:crux.db/id :person/johanna
                                       :full-name "Johanna"
                                       :origin-planet "Earth"
                                       :identity-tag :JA012992129120
                                       :DOB #inst "2090-12-07"}]])

  (t/is (= #{[{:crux.db/id :person/ilex,
               :full-name "Ilex Jefferson",
               :origin-planet "Venus",
               :identity-tag :IJ01222212454,
               :DOB #inst "2061-02-17T00:00:00.000-00:00"}]
             [{:crux.db/id :person/thadd,
               :full-name "Thad Christover",
               :origin-moon "Titan",
               :identity-tag :IJ01222212454,
               :DOB #inst "2101-01-01T00:00:00.000-00:00"}]
             [{:crux.db/id :person/kaarlang,
               :full-name "Kaarlang",
               :origin-planet "Mars",
               :identity-tag :KA01299242093,
               :DOB #inst "2040-11-23T00:00:00.000-00:00"}]
             [{:crux.db/id :person/johanna,
               :full-name "Johanna",
               :origin-planet "Earth",
               :identity-tag :JA012992129120,
               :DOB #inst "2090-12-07T00:00:00.000-00:00"}]}
           (full-query *api*)))

  (fix/submit+await-tx [[:crux.tx/evict :person/kaarlang]]) ;; <1>
  (t/is empty? (full-query *api*))

  ;; Check not nil, history constantly changing so it is hard to check otherwise
  (with-open [history (crux/open-entity-history (crux/db *api*) :person/kaarlang :desc)]
    (t/is history))

  (with-open [history (crux/open-entity-history (crux/db *api*) :person/ilex :desc)]
    (t/is history))

  (with-open [history (crux/open-entity-history (crux/db *api*) :person/thadd :desc)]
    (t/is history))

  (with-open [history (crux/open-entity-history (crux/db *api*) :person/johanna :desc)]
    (t/is history)))
