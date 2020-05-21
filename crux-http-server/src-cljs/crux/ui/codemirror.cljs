(ns crux.ui.codemirror
  (:require cljsjs.codemirror
            cljsjs.codemirror.mode.clojure
            cljsjs.codemirror.mode.javascript
            cljsjs.codemirror.addon.edit.closebrackets
            cljsjs.codemirror.addon.edit.matchbrackets
            cljsjs.codemirror.addon.hint.show-hint
            cljsjs.codemirror.addon.hint.anyword-hint
            cljsjs.codemirror.addon.display.autorefresh
            cljsjs.codemirror.addon.fold.foldcode
            cljsjs.codemirror.addon.fold.foldgutter
            cljsjs.codemirror.addon.fold.brace-fold
            cljsjs.codemirror.addon.fold.indent-fold
            [reagent.core :as r]
            [reagent.dom :as rd]
            [goog.object :as gobj]
            [goog.string :as gstring]
            [cljs.pprint :as pprint]))

(defn escape-re [input]
  (let [re (js/RegExp. "([.*+?^=!:${}()|[\\]\\/\\\\])" "g")]
    (-> input str (.replace re "\\$1"))))

(defn fuzzy-re [input]
  (-> (reduce (fn [s c] (str s (escape-re c) ".*")) "" input)
      (js/RegExp "i")))

(def crux-builtin-keywords
 [:find :where :args :rules :offset :limit :order-by
  :timeout :full-results? :crux.db/id])

(defn- autocomplete [index cm options]
  (let [cur (.getCursor cm)
        line (.-line cur)
        ch (.-ch cur)
        token (.getTokenAt cm cur)
        reg (subs (.-string token) 0 (- ch (.-start token)))
        blank? (#{"[" "{" " " "("} reg)
        start (if blank? cur (.Pos js/CodeMirror line (gobj/get token "start")))
        end (if blank? cur (.Pos js/CodeMirror line (gobj/get token "end")))
        words (concat crux-builtin-keywords index)
        fuzzy (if blank? #".*" (fuzzy-re reg))
        words (->> words
                   (map str)
                   (filter #(re-find fuzzy %)))]
    (clj->js {:list words
              :from start
              :to end})))

(defn code-mirror
  [initial-value {:keys [class stats on-change
                         on-blur on-cm-init]}]
  (let [value-atom (atom (or initial-value ""))
        on-change (or on-change (constantly nil))
        on-blur (or on-blur (constantly nil))
        cm-inst (atom nil)
        indexes (when (map? stats) (keys stats))]
    (r/create-class
     {:component-did-mount
      (fn [this]
        (let [el (rd/dom-node this)
              opts #js {:lineNumbers true
                        :undoDepth 100000000
                        :historyEventDelay 1
                        :viewportMargin js/Infinity
                        :autoRefresh true
                        :value @value-atom
                        :theme "eclipse"
                        :foldGutter true
                        :gutters #js ["CodeMirror-linenumbers", "CodeMirror-foldgutter"]
                        :autoCloseBrackets true
                        :hintOptions #js {:hint (partial autocomplete indexes)
                                          :completeSingle false}
                        :extraKeys {"Ctrl-Space" "autocomplete"}
                        :matchBrackets true}
              inst (reset! cm-inst (js/CodeMirror. el opts))]
          (.on inst "keyup"
               (fn [cm e] (when (and (not (gobj/getValueByKeys cm #js ["state" "completionActive"]))
                                     (= 1 (-> (gobj/get e "key") (count)))
                                     (= (gobj/get e "key") ":"))
                            (.showHint inst))))
          (.on inst "change"
               (fn []
                 (let [value (.getValue inst)]
                   (when-not (= value @value-atom)
                     (on-change value)
                     (reset! value-atom value)))))
          (.on inst "blur" (fn [] (on-blur)))
          (when on-cm-init
            (on-cm-init inst))))
      :component-did-update
      (fn []
        (when-not (= @value-atom (.getValue @cm-inst))
          (.setValue @cm-inst @value-atom)
          ;; reset the cursor to the end of the text, if the text was changed
          ;; externally
          (let [last-line (.lastLine @cm-inst)
                last-ch (count (.getLine @cm-inst last-line))]
            (.setCursor @cm-inst last-line last-ch))))
      :reagent-render
      (fn []
        [:div.textarea
         {:class class}])})))


(defn indent-code
  ([level]
   (indent-code level 0))
  ([level plus-any]
   (gstring/unescapeEntities
    (apply str (repeat (+ level plus-any) "&nbsp;")))))

(defn coll-type
  [m]
  (cond
    (vector? m) ["[" "]"]
    (list? m) ["(" ")"]
    (set? m) ["#{" "}"]))

(defn span-class
  [m]
  (cond
    (string? m) "cm-string"
    (keyword? m) "cm-atom"
    (number? m) "cm-number"
    :else nil))

(defn unfolding-icon
  [state m k]
  [:span
   {:style {:cursor "pointer"}
    :on-click #(swap! state update k not)}
   (if (get @state k)
     (gstring/unescapeEntities "&#9660;")
     (str (gstring/unescapeEntities "&#9654;")
          (cond-> " "
            (map? m) (str "{...}")
            (vector? m) (str "[...]")
            (list? m) (str "(...)")
            (set? m) (str "#{...}"))))])

(defn code-snippet
  [_ _]
  (let [state (r/atom nil)]
    (fn [m links]
      (let [generate-snippet
            (fn generate-snippet [parent-keys m parent]
              (let [level (inc (count parent-keys))]
                (cond
                  (get links m) [:a {:href (str (get links m))}
                                 (with-out-str
                                   (pprint/with-pprint-dispatch
                                     pprint/code-dispatch
                                     (pprint/pprint m)))]
                  (map? m) [:<>
                            [unfolding-icon state m parent-keys]
                            (when (get @state parent-keys)
                              [:pre {:style {:margin 0}}
                               [:span (indent-code level)
                                "{"]
                               (for [[k v] m]
                                 ^{:key k}
                                 [:pre {:style {:margin 0}}
                                  [:span (indent-code level)]
                                  [:span.cm-atom (str k " ")]
                                  (generate-snippet (conj parent-keys k) v m)])
                               [:span (indent-code level)
                                "}"]])]
                  (coll? m) (let [[open close] (coll-type m)]
                              [:<>
                               [unfolding-icon state m parent-keys]
                               (when (get @state parent-keys)
                                 [:pre {:style {:margin 0}}
                                  [:span (indent-code level) open]
                                  (doall
                                   (map-indexed
                                    (fn [idx v]
                                      ^{:key v}
                                      [:pre {:style {:margin 0}}
                                       [:span (indent-code level 1)]
                                       (generate-snippet (conj parent-keys idx) v m)])
                                    m))
                                  [:span (indent-code level) close]])])
                  :else [:span
                         {:class (span-class m)}
                         (if (string? m) (str "\"" m "\"") (str m))])))]
        [:div.CodeMirror.cm-s-eclipse
         {:style {:cursor "default"}}
         [:pre
          [:span "{"]
          (doall
           (for [[k v] m]
             ^{:key k}
             [:pre {:style {:margin 0
                            :line-height "1.2rem"}}
              [:span.cm-atom (str k " ")]
              (generate-snippet [k] v m)]))
          [:span (indent-code 0) "}"]]]))))
