(ns crux.sparql-test
  (:require [clojure.test :as t]
            [crux.fixtures.api :as fapi :refer [*node*]]
            [crux.api :as crux]
            [crux.fixtures.kv :as fkv]
            [crux.fixtures.api :as apif]
            [crux.fixtures.standalone :as fs]
            [clojure.java.io :as io]
            [crux.sparql :as sparql]
            [crux.rdf :as rdf]))

(t/use-fixtures :each fs/with-standalone-node fkv/with-kv-dir apif/with-node)

;; https://jena.apache.org/tutorials/sparql.html
(t/deftest test-can-transact-and-query-using-sparql
  (fapi/submit+await-tx (->> (rdf/ntriples "crux/vc-db-1.nt") (rdf/->tx-ops) (rdf/->default-language)))

  (t/testing "querying transacted data"
    (t/is (= #{[(keyword "http://somewhere/JohnSmith/")]}
             (crux/q (crux/db *node*)
                     (sparql/sparql->datalog
                      "
SELECT ?x
WHERE { ?x  <http://www.w3.org/2001/vcard-rdf/3.0#FN>  \"John Smith\" }"))))

    (t/is (= #{[(keyword "http://somewhere/RebeccaSmith/") "Becky Smith"]
               [(keyword "http://somewhere/SarahJones/") "Sarah Jones"]
               [(keyword "http://somewhere/JohnSmith/") "John Smith"]
               [(keyword "http://somewhere/MattJones/") "Matt Jones"]}
             (crux/q (crux/db *node*)
                     (sparql/sparql->datalog
                      "
SELECT ?x ?fname
WHERE {?x  <http://www.w3.org/2001/vcard-rdf/3.0#FN>  ?fname}"))))

    (t/is (= #{["John"]
               ["Rebecca"]}
             (crux/q (crux/db *node*)
                     (sparql/sparql->datalog
                      "
SELECT ?givenName
WHERE
  { ?y  <http://www.w3.org/2001/vcard-rdf/3.0#Family>  \"Smith\" .
    ?y  <http://www.w3.org/2001/vcard-rdf/3.0#Given>  ?givenName .
  }"))))

    (t/is (= #{["Rebecca"]
               ["Sarah"]}
             (crux/q (crux/db *node*)
                     (sparql/sparql->datalog
                      "
PREFIX vcard: <http://www.w3.org/2001/vcard-rdf/3.0#>

SELECT ?g
WHERE
{ ?y vcard:Given ?g .
  FILTER regex(?g, \"r\", \"i\") }"))))

    (t/is (= #{[(keyword "http://somewhere/JohnSmith/")]}
             (crux/q (crux/db *node*)
                     (sparql/sparql->datalog
                      "
PREFIX info: <http://somewhere/peopleInfo#>

SELECT ?resource
WHERE
  {
    ?resource info:age ?age .
    FILTER (?age >= 24)
  }"))))

    ;; NOTE: Without post processing the extra optional is correct.
    (t/is (= #{["Becky Smith" 23]
               ["Sarah Jones" :crux.sparql/optional]
               ["John Smith" 25]
               ["Matt Jones" :crux.sparql/optional]}
             (crux/q (crux/db *node*)
                     (sparql/sparql->datalog
                      "
PREFIX info:    <http://somewhere/peopleInfo#>
PREFIX vcard:   <http://www.w3.org/2001/vcard-rdf/3.0#>

SELECT ?name ?age
WHERE
{
    ?person vcard:FN  ?name .
    OPTIONAL { ?person info:age ?age }
}"))))

    (t/is (= #{["Becky Smith" 23]
               ["John Smith" 25]}
             (crux/q (crux/db *node*)
                     (sparql/sparql->datalog
                      "
PREFIX info:   <http://somewhere/peopleInfo#>
PREFIX vcard:  <http://www.w3.org/2001/vcard-rdf/3.0#>

SELECT ?name ?age
WHERE
{
    ?person vcard:FN  ?name .
    ?person info:age ?age .
}"))))

    (t/is (= #{["Sarah Jones" :crux.sparql/optional]
               ["John Smith" 25]
               ["Matt Jones" :crux.sparql/optional]}
             (crux/q (crux/db *node*)
                     (sparql/sparql->datalog
                      "
PREFIX info:        <http://somewhere/peopleInfo#>
PREFIX vcard:      <http://www.w3.org/2001/vcard-rdf/3.0#>

SELECT ?name ?age
WHERE
{
    ?person vcard:FN  ?name .
    OPTIONAL { ?person info:age ?age . FILTER ( ?age > 24 ) }
}"))))))
