(ns crux.rdf-test
  (:require [clojure.test :as t]
            [clojure.java.io :as io]
            [cheshire.core :as json]
            [crux.rdf :as rdf]))

;; Example based on:
;; https://github.com/eclipse/rdf4j-doc/blob/master/examples/src/main/resources/example-data-artists.ttl
(defn check-artists-graph [iri->entity]
  (t/is (= 7 (count iri->entity)))

  (let [artist (:http://example.org/Picasso iri->entity)
        painting (:http://example.org/creatorOf artist)]

    (t/is (= :http://example.org/guernica painting))
    (t/is (= "oil on canvas"
             (-> painting
                 iri->entity
                 :http://example.org/technique)))

    (t/is (= {:http://example.org/street "31 Art Gallery",
              :http://example.org/city "Madrid",
              :http://example.org/country "Spain"}
             (-> artist
                 :http://example.org/homeAddress
                 iri->entity
                 (dissoc :crux.rdf/iri))))))

(defn maps-by-id [maps]
  (->> (for [m maps]
         {(:crux.rdf/iri m) m})
       (into {})))

(defn load-ntriples-example [resource]
  (with-open [in (io/input-stream (io/resource resource))]
    (->> (rdf/ntriples-seq in)
         (rdf/statements->maps)
         (maps-by-id))))

(defn load-jsonld-example [resource]
  (with-open [in (io/reader (io/resource resource))]
    (->> (json/parse-stream in true)
         (rdf/jsonld->maps)
         (maps-by-id))))

(t/deftest test-can-parse-ntriples-into-maps
  (->> (load-ntriples-example "crux/example-data-artists.nt")
       (check-artists-graph)))

(t/deftest test-can-parse-jsonld-into-maps
  (->> (load-jsonld-example "crux/example-data-artists.jsonld")
       (check-artists-graph)))

(t/deftest test-can-parse-dbpedia-entity
  (let [picasso (-> (load-ntriples-example "crux/Pablo_Picasso.ntriples")
                    :http://dbpedia.org/resource/Pablo_Picasso)]
    (t/is (= 48 (count picasso)))
    (t/is (= {:http://xmlns.com/foaf/0.1/givenName "Pablo"
              :http://xmlns.com/foaf/0.1/surname "Picasso"
              :http://dbpedia.org/ontology/birthDate #inst "1881-10-25"}
             (select-keys picasso
                          [:http://xmlns.com/foaf/0.1/givenName
                           :http://xmlns.com/foaf/0.1/surname
                           :http://dbpedia.org/ontology/birthDate])))))
