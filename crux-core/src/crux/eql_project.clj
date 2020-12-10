(ns ^:no-doc crux.eql-project
  (:require [crux.codec :as c]
            [crux.db :as db]
            [edn-query-language.core :as eql]
            [clojure.string :as string]
            [clojure.set :as set])
  (:import clojure.lang.MapEntry))

(defn- recognise-union [child]
  (when (and (= :join (:type child))
             (= :union (get-in child [:children 0 :type])))
    :union))

(defn- replace-docs [v docs]
  (if (not-empty (::hashes (meta v)))
    (v docs)
    v))

(defn- lookup-docs [v {:keys [document-store]}]
  (when-let [hashes (not-empty (::hashes (meta v)))]
    (db/fetch-docs document-store hashes)))

(defmacro let-docs [[binding hashes] & body]
  `(-> (fn ~'let-docs [~binding]
         ~@body)
       (with-meta {::hashes ~hashes})))

(defn- after-doc-lookup [f lookup]
  (if-let [hashes (::hashes (meta lookup))]
    (let-docs [docs hashes]
      (let [res (replace-docs lookup docs)]
        (if (::hashes (meta res))
          (after-doc-lookup f res)
          (f res))))
    (f lookup)))

(defn- raise-doc-lookup-out-of-coll
  "turns a vector/set where each of the values could be doc lookups into a single doc lookup returning a vector/set"
  [coll]
  (if-let [hashes (not-empty (into #{} (mapcat (comp ::hashes meta)) coll))]
    (let-docs [docs hashes]
      (->> coll
           (into (empty coll) (map #(replace-docs % docs)))
           raise-doc-lookup-out-of-coll))
    coll))

(defrecord RecurseState [^long recurse-depth child-fns])

(defn- project-child [v db ^RecurseState recurse-state]
  (->> (mapv (fn [f]
               (f v db recurse-state))
             (.child-fns recurse-state))
       (raise-doc-lookup-out-of-coll)
       (after-doc-lookup (fn [res]
                           (into {} (mapcat identity) res)))))

(declare project-child-fns)

(defn- ->next-recurse-state-fn [{:keys [query] :as join}]
  (cond
    ;; TODO temporarily feature flagging recursion until it passes Datascript tests, see #1220
    ;; (= '... query) (fn [^RecurseState recurse-state]
    ;;                  (-> recurse-state (update :recurse-depth inc)))
    ;; (int? query) (fn [^RecurseState recurse-state]
    ;;                (when (< (.recurse-depth recurse-state) ^long query)
    ;;                  (-> recurse-state (update :recurse-depth inc))))
    :else (constantly (RecurseState. 0 (project-child-fns join)))))

(defn- forward-joins-child-fn [{:keys [props special forward-joins unions]}]
  (when-not (every? empty? [props special forward-joins unions])
    (let [forward-join-child-fns (for [{:keys [dispatch-key] :as join} forward-joins]
                                   (let [next-recurse-state (->next-recurse-state-fn join)
                                         k (or (get-in join [:params :as] dispatch-key))]
                                     (fn [doc db recurse-state]
                                       (when-let [v (get doc dispatch-key)]
                                         (when-let [recurse-state (next-recurse-state recurse-state)]
                                           (->> (if (c/multiple-values? v)
                                                  (->> (into [] (map #(project-child % db recurse-state)) v)
                                                       (raise-doc-lookup-out-of-coll))
                                                  (project-child v db recurse-state))
                                                (after-doc-lookup (fn [res]
                                                                    (MapEntry/create k res)))))))))
          union-child-fns (for [{:keys [dispatch-key children]} unions
                                {:keys [union-key] :as child} (get-in children [0 :children])]
                            (let [next-recurse-state (->next-recurse-state-fn child)]
                              (fn [value doc db recurse-state]
                                (->> (c/vectorize-value (get doc dispatch-key))
                                     (keep (fn [v]
                                             (when (= v union-key)
                                               (when-let [recurse-state (next-recurse-state recurse-state)]
                                                 (project-child value db recurse-state)))))))))
          prop-dispatch-keys (into #{} (map :dispatch-key) props)
          project-star? (contains? (into #{} (map :dispatch-key) special) '*)

          with-renamed-props (if-let [rename-mapping (->> props
                                                          (into {}
                                                                (keep (fn [{:keys [dispatch-key] :as prop}]
                                                                        (when-let [as (get-in prop [:params :as])]
                                                                          (MapEntry/create dispatch-key as)))))
                                                          not-empty)]
                               #(set/rename-keys % rename-mapping)
                               identity)]

      (fn [value {:keys [entity-resolver-fn] :as db} recurse-state]
        (let [content-hash (entity-resolver-fn (c/->id-buffer value))]
          (let-docs [docs #{content-hash}]
            (let [doc (get docs (c/new-id content-hash))]
              (->> (concat (->> forward-join-child-fns (map (fn [f] (f doc db recurse-state))))
                           (->> union-child-fns (mapcat (fn [f] (f value doc db recurse-state)))))
                   (raise-doc-lookup-out-of-coll)
                   (after-doc-lookup (fn [res]
                                       ;; TODO do we need a deeper merge here?
                                       (-> (into (if project-star?
                                                   doc
                                                   (select-keys doc prop-dispatch-keys))
                                                 res)
                                           with-renamed-props)))))))))))

(defn- reverse-joins-child-fn [reverse-joins]
  (when-not (empty? reverse-joins)
    (let [reverse-join-child-fns (for [{:keys [dispatch-key] :as join} reverse-joins]
                                   (let [forward-key (keyword (namespace dispatch-key)
                                                              (subs (name dispatch-key) 1))
                                         one? (= :one (get-in join [:params :crux/cardinality]))
                                         next-recurse-state (->next-recurse-state-fn join)
                                         k (or (get-in join [:params :as]) dispatch-key)]
                                     (fn [value-buffer {:keys [index-snapshot entity-resolver-fn] :as db} recurse-state]
                                       (when-let [recurse-state (next-recurse-state recurse-state)]
                                         (->> (vec (for [v (cond->> (db/ave index-snapshot (c/->id-buffer forward-key) value-buffer nil entity-resolver-fn)
                                                             one? (take 1)
                                                             :always vec)]
                                                     (project-child (db/decode-value index-snapshot v) db recurse-state)))
                                              (raise-doc-lookup-out-of-coll)
                                              (after-doc-lookup (fn [res]
                                                                  (when (seq res)
                                                                    (MapEntry/create k (cond->> res
                                                                                         one? first))))))))))]
      (fn [value db recurse-state]
        (let [value-buffer (c/->value-buffer value)]
          (->> reverse-join-child-fns
               (keep (fn [f]
                       (f value-buffer db recurse-state)))
               (raise-doc-lookup-out-of-coll)))))))

(defn- project-child-fns [project-spec]
  (let [{special :special,
         props :prop,
         joins :join,
         unions :union} (->> (:children project-spec)
                             (group-by (some-fn recognise-union
                                                :type
                                                (constantly :special))))

        {forward-joins false, reverse-joins true}
        (group-by (comp #(string/starts-with? % "_") name :dispatch-key) joins)]

    (->> [(forward-joins-child-fn {:special special, :props props, :forward-joins forward-joins, :unions unions})
          (reverse-joins-child-fn reverse-joins)]
         (remove nil?))))

(defn compile-project-spec [project-spec]
  (let [recurse-state (RecurseState. 0 (project-child-fns (eql/query->ast project-spec)))]
    (fn [value db]
      (project-child value db recurse-state))))

(defn ->project-result [db compiled-find q-conformed res]
  (->> res
       (map (fn [row]
              (mapv (fn [value ->result]
                      (->result value db))
                    row
                    (mapv :->result compiled-find))))
       (partition-all (or (:batch-size q-conformed)
                          (:batch-size db)
                          100))
       (map (fn [results]
              (->> results
                   (mapv raise-doc-lookup-out-of-coll)
                   raise-doc-lookup-out-of-coll)))
       (mapcat (fn [lookup]
                 (if (::hashes (meta lookup))
                   (recur (replace-docs lookup (lookup-docs lookup db)))
                   lookup)))))
