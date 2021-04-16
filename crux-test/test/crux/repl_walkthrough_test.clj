(ns crux.repl-walkthrough-test
  (:require [crux.api :as crux]
            [clojure.pprint :as pp]
            [clojure.test :as t]
            [crux.fixtures :as fix :refer [*api*]]
            [clojure.java.io :as io])
  (:import (crux.api ICruxAPI)))

(t/use-fixtures :each fix/with-node)

(def nodes
  (for [n [{:user/name :User1, :hasRoleInGroups #{:U1G3R34 :U1G2R23}}
           {:user/name :User2, :hasRoleInGroups #{:U2G2R34 :U2G3R56 :U2G1R25}}
           {:role/name :Role1}
           {:role/name :Role2}
           {:role/name :Role3}
           {:role/name :Role4}
           {:role/name :Role5}
           {:role/name :Role6}
           {:group/name :Group1}
           {:group/name :Group2}
           {:group/name :Group3}
           {:roleInGroup/name :U2G2R34, :hasGroups #{:Group2}, :hasRoles #{:Role3 :Role4}}
           {:roleInGroup/name :U1G2R23, :hasGroups #{:Group2}, :hasRoles #{:Role2 :Role3}}
           {:roleInGroup/name :U1G3R34, :hasGroups #{:Group3}, :hasRoles #{:Role3 :Role4}}
           {:roleInGroup/name :U2G3R56, :hasGroups #{:Group3}, :hasRoles #{:Role5 :Role6}}
           {:roleInGroup/name :U2G1R25, :hasGroups #{:Group1}, :hasRoles #{:Role2 :Role5}}
           {:roleInGroup/name :U1G1R12, :hasGroups #{:Group1}, :hasRoles #{:Role1 :Role2}}]]
    (assoc n :crux.db/id (some n [:user/name :group/name :role/name :roleInGroup/name]))))

(t/deftest graph-traversal-test
  (fix/submit+await-tx (mapv (fn [n] [:crux.tx/put n]) nodes))

  (let [db (crux/db *api*)]
    (t/is (= #{[:Role2] [:Role3]}
             (crux/q db '{:find [?roleName]
                          :where
                          [[?e :hasRoleInGroups ?roleInGroup]
                           [?roleInGroup :hasGroups ?group]
                           [?roleInGroup :hasRoles ?role]
                           [?role :role/name ?roleName]]
                          :args [{?e :User1 ?group :Group2}]})))

    (t/is (= #{[:Group1 :Role5] [:Group3 :Role5] [:Group2 :Role4]
               [:Group3 :Role6] [:Group2 :Role3] [:Group1 :Role2]}
             (crux/q db '{:find [?groupName ?roleName]
                          :where
                          [[?e :hasRoleInGroups ?roleInGroup]
                           [?roleInGroup :hasGroups ?group]
                           [?group :group/name ?groupName]
                           [?roleInGroup :hasRoles ?role]
                           [?role :role/name ?roleName]]
                          :args [{?e :User2}]})))

    (t/is (= #{[:Group3 :Role4] [:Group3 :Role3] [:Group2 :Role3] [:Group2 :Role2]}
             (crux/q db {:find '[?groupName ?roleName]
                         :where '[(user-roles-in-groups ?user ?role ?group)
                                  [?group :group/name ?groupName]
                                  [?role :role/name ?roleName]]
                         :rules '[[(user-roles-in-groups ?user ?role ?group)
                                   [?user :hasRoleInGroups ?roleInGroup]
                                   [?roleInGroup :hasGroups ?group]
                                   [?roleInGroup :hasRoles ?role]]]
                         :args '[{?user :User1}]})))))

(t/deftest walkthrough-test
  (fix/submit+await-tx [[:crux.tx/put {:crux.db/id :dbpedia.resource/Pablo-Picasso
                                       :name "Pablo"
                                       :last-name "Picasso"
                                       :location "Spain"}
                         #inst "1881-10-25T09:20:27.966-00:00"]
                        [:crux.tx/put {:crux.db/id :dbpedia.resource/Pablo-Picasso
                                       :name "Pablo"
                                       :last-name "Picasso"
                                       :location "Sain2"}
                         #inst "1881-10-25T09:20:27.966-00:00"]])

  (fix/submit+await-tx [[:crux.tx/match
                         :dbpedia.resource/Pablo-Picasso
                         {:crux.db/id :dbpedia.resource/Pablo-Picasso
                          :name "Pablo"
                          :last-name "Picasso"
                          :location "Spain"}
                         #inst "1973-04-08T09:20:27.966-00:00"]
                        [:crux.tx/put
                         {:crux.db/id :dbpedia.resource/Pablo-Picasso
                          :name "Pablo"
                          :last-name "Picasso"
                          :height 1.63
                          :location "France"}
                         #inst "1973-04-08T09:20:27.966-00:00"]])

  (fix/submit+await-tx [[:crux.tx/delete :dbpedia.resource/Pablo-Picasso
                         #inst "1973-04-08T09:20:27.966-00:00"]])

  (t/is (= #{[{:crux.db/id :dbpedia.resource/Pablo-Picasso, :name "Pablo", :last-name "Picasso", :location "Sain2"}]}
           (crux/q
            (crux/db *api* #inst "1973-04-07T09:20:27.966-00:00")
            '{:find [(pull e [*])]
              :where [[e :name "Pablo"]]})))

  (fix/submit+await-tx [[:crux.tx/evict :dbpedia.resource/Pablo-Picasso]])

  (t/is (empty? (crux/q (crux/db *api*)
                        '{:find [(pull e [*])]
                          :where [[e :name "Pablo"]]})))

  (fix/submit+await-tx [[:crux.tx/put {:crux.db/id :dbpedia.resource/Pablo-Picasso
                                       :name "Pablo"
                                       :last-name "Picasso"
                                       :height 1.63
                                       :location "France"}
                         #inst "1973-04-08T09:20:27.966-00:00"]])

  (t/is (= #{[{:crux.db/id :dbpedia.resource/Pablo-Picasso
               :name "Pablo"
               :last-name "Picasso"
               :height 1.63
               :location "France"}]}
           (crux/q (crux/db *api*)
                   '{:find [(pull e [*])]
                     :where [[e :name "Pablo"]]}))))
