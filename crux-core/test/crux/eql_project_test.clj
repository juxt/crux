(ns crux.eql-project-test
  (:require [clojure.test :as t]
            [crux.api :as crux]
            [crux.fixtures :as fix :refer [*api*]]
            [crux.eql-project :as project]
            [clojure.java.io :as io]))

(t/use-fixtures :each fix/with-node)

(t/deftest test-project
  (fix/submit+await-tx (for [doc (read-string (slurp (io/resource "data/james-bond.edn")))]
                         [:crux.tx/put doc]))

  (let [->lookup-docs (let [f @#'project/lookup-docs]
                        (fn [!lookup-counts]
                          (fn [v db]
                            (swap! !lookup-counts conj (count (::project/hashes (meta v))))
                            (f v db))))
        db (crux/db *api*)]

    (t/is (= #{[{}]}
             (crux/q db '{:find [(eql/project ?v [])]
                          :where [[?v :vehicle/brand "Aston Martin"]]})))

    (t/testing "simple props"
      (let [expected #{[{:vehicle/brand "Aston Martin", :vehicle/model "DB5"}]
                       [{:vehicle/brand "Aston Martin", :vehicle/model "DB10"}]
                       [{:vehicle/brand "Aston Martin", :vehicle/model "DBS"}]
                       [{:vehicle/brand "Aston Martin", :vehicle/model "DBS V12"}]
                       [{:vehicle/brand "Aston Martin", :vehicle/model "V8 Vantage Volante"}]
                       [{:vehicle/brand "Aston Martin", :vehicle/model "V12 Vanquish"}]}]
        (let [!lookup-counts (atom [])]
          (with-redefs [project/lookup-docs (->lookup-docs !lookup-counts)]
            (t/is (= expected
                     (crux/q db '{:find [(eql/project ?v [:vehicle/brand :vehicle/model])]
                                  :where [[?v :vehicle/brand "Aston Martin"]]})))
            (t/is (= [6] @!lookup-counts) "batching lookups")))

        (let [!lookup-counts (atom [])]
          (with-redefs [project/lookup-docs (->lookup-docs !lookup-counts)]
            (t/is (= expected
                     (crux/q db '{:find [(eql/project ?v [:vehicle/brand :vehicle/model])]
                                  :where [[?v :vehicle/brand "Aston Martin"]]
                                  :batch-size 3})))
            (t/is (= [3 3] @!lookup-counts) "batching lookups")))))

    (t/testing "renames"
      (t/is (= #{[{:brand "Aston Martin", :model "DB5"}]
                 [{:brand "Aston Martin", :model "DB10"}]
                 [{:brand "Aston Martin", :model "DBS"}]
                 [{:brand "Aston Martin", :model "DBS V12"}]
                 [{:brand "Aston Martin", :model "V8 Vantage Volante"}]
                 [{:brand "Aston Martin", :model "V12 Vanquish"}]}

               (crux/q db '{:find [(eql/project ?v [(:vehicle/brand {:as :brand})
                                                    (:vehicle/model {:as :model})])]
                            :where [[?v :vehicle/brand "Aston Martin"]]}))))

    (t/testing "forward joins"
      (let [!lookup-counts (atom [])]
        (with-redefs [project/lookup-docs (->lookup-docs !lookup-counts)]
          (t/is (= #{[{:film/year "2002",
                       :film/name "Die Another Day"
                       :film/bond {:person/name "Pierce Brosnan"},
                       :film/director {:person/name "Lee Tamahori"},
                       :film/vehicles [{:vehicle/brand "Jaguar", :vehicle/model "XKR"}
                                       {:vehicle/brand "Aston Martin", :vehicle/model "V12 Vanquish"}
                                       {:vehicle/brand "Ford", :vehicle/model "Thunderbird"}
                                       {:vehicle/brand "Ford", :vehicle/model "Fairlane"}]}]}
                   (crux/q db '{:find [(eql/project ?f [{:film/bond [:person/name]}
                                                        {:film/director [:person/name]}
                                                        {:film/vehicles [:vehicle/brand :vehicle/model]}
                                                        :film/name :film/year])]
                                :where [[?f :film/name "Die Another Day"]]})))
          (t/is (= [1 6] @!lookup-counts) "batching lookups"))))

    (t/testing "reverse joins"
      (let [!lookup-counts (atom [])]
        (with-redefs [project/lookup-docs (->lookup-docs !lookup-counts)]
          (t/is (= #{[{:person/name "Daniel Craig",
                       :film/_bond [#:film{:name "Skyfall", :year "2012"}
                                    #:film{:name "Spectre", :year "2015"}
                                    #:film{:name "Casino Royale", :year "2006"}
                                    #:film{:name "Quantum of Solace", :year "2008"}]}]}
                   (crux/q db '{:find [(eql/project ?dc [:person/name
                                                         {:film/_bond [:film/name :film/year]}])]
                                :where [[?dc :person/name "Daniel Craig"]]})))
          (t/is (= [5] @!lookup-counts) "batching lookups"))))

    (t/testing "reverse joins, rename"
      (t/is (= #{[{:person/name "Daniel Craig",
                   :films [#:film{:name "Skyfall", :year "2012"}
                           #:film{:name "Spectre", :year "2015"}
                           #:film{:name "Casino Royale", :year "2006"}
                           #:film{:name "Quantum of Solace", :year "2008"}]}]}
               (crux/q db '{:find [(eql/project ?dc [:person/name
                                                     {(:film/_bond {:as :films}) [:film/name :film/year]}])]
                            :where [[?dc :person/name "Daniel Craig"]]}))))

    (t/testing "project *"
      (t/is (= #{[{:crux.db/id :daniel-craig
                   :person/name "Daniel Craig",
                   :type :person}]}
               (crux/q db '{:find [(eql/project ?dc [*])]
                            :where [[?dc :person/name "Daniel Craig"]]}))))

    (t/testing "project fn"
      (t/is (= #:film{:name "Spectre", :year "2015"}
               (crux/project db (pr-str [:film/name :film/year]) :spectre)))
      (t/is (= #:film{:name "Spectre", :year "2015"}
               (crux/project db [:film/name :film/year] :spectre))))

    (t/testing "projectMany fn"
      (t/is (= #{#:film{:name "Skyfall", :year "2012"}
                 #:film{:name "Spectre", :year "2015"}}
               (set (crux/project-many db (pr-str [:film/name :film/year]) #{:skyfall :spectre}))))

      (t/is (= #{#:film{:name "Skyfall", :year "2012"}
                 #:film{:name "Spectre", :year "2015"}}
               (set (crux/project-many db [:film/name :film/year] #{:skyfall :spectre})))))))

(t/deftest test-union
  (fix/submit+await-tx [[:crux.tx/put {:crux.db/id :foo
                                       :type :a
                                       :x 2
                                       :y "this"
                                       :z :not-this}]
                        [:crux.tx/put {:crux.db/id :bar
                                       :type :b
                                       :y "not this"
                                       :z 5}]])

  (t/is (= #{[{:crux.db/id :foo, :x 2, :y "this"}]
             [{:crux.db/id :bar, :z 5}]}
           (crux/q (crux/db *api*)
                   '{:find [(eql/project ?it [{:type {:a [:x :y], :b [:z]}}
                                              :crux.db/id])]
                     :where [[?it :crux.db/id]]}))))

;; TODO temporarily feature flagging recursion until it passes Datascript tests, see #1220
#_(t/deftest test-recursive
  (fix/submit+await-tx [[:crux.tx/put {:crux.db/id :root}]
                        [:crux.tx/put {:crux.db/id :a
                                       :parent :root}]
                        [:crux.tx/put {:crux.db/id :b
                                       :parent :root}]
                        [:crux.tx/put {:crux.db/id :aa
                                       :parent :a}]
                        [:crux.tx/put {:crux.db/id :ab
                                       :parent :a}]
                        [:crux.tx/put {:crux.db/id :aba
                                       :parent :ab}]
                        [:crux.tx/put {:crux.db/id :abb
                                       :parent :ab}]])

  (t/testing "forward unbounded recursion"
    (t/is (= {:crux.db/id :aba
              :parent {:crux.db/id :ab
                       :parent {:crux.db/id :a
                                :parent {:crux.db/id :root}}}}
             (ffirst (crux/q (crux/db *api*)
                             '{:find [(eql/project ?aba [:crux.db/id {:parent ...}])]
                               :where [[?aba :crux.db/id :aba]]})))))

  (t/testing "forward bounded recursion"
    (t/is (= {:crux.db/id :aba
              :parent {:crux.db/id :ab
                       :parent {:crux.db/id :a}}}
             (ffirst (crux/q (crux/db *api*)
                             '{:find [(eql/project ?aba [:crux.db/id {:parent 2}])]
                               :where [[?aba :crux.db/id :aba]]})))))

  (t/testing "reverse unbounded recursion"
    (t/is (= {:crux.db/id :root
              :_parent [{:crux.db/id :a
                         :_parent [{:crux.db/id :aa}
                                   {:crux.db/id :ab
                                    :_parent [{:crux.db/id :aba}
                                              {:crux.db/id :abb}]}]}
                        {:crux.db/id :b}]}
             (ffirst (crux/q (crux/db *api*)
                             '{:find [(eql/project ?root [:crux.db/id {:_parent ...}])]
                               :where [[?root :crux.db/id :root]]})))))

  (t/testing "reverse bounded recursion"
    (t/is (= {:crux.db/id :root
              :_parent [{:crux.db/id :a
                         :_parent [{:crux.db/id :aa}
                                   {:crux.db/id :ab}]}
                        {:crux.db/id :b}]}
             (ffirst (crux/q (crux/db *api*)
                             '{:find [(eql/project ?root [:crux.db/id {:_parent 2}])]
                               :where [[?root :crux.db/id :root]]}))))))

(t/deftest test-doesnt-hang-on-unknown-eid
  (t/is (= #{[{}]}
           (crux/q (crux/db *api*)
                   '{:find [(eql/project ?e [*])]
                     :in [?e]
                     :timeout 500}
                   "doesntexist"))))
