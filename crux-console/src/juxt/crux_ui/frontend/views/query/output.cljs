(ns juxt.crux-ui.frontend.views.query.output
  (:require [juxt.crux-ui.frontend.views.comps :as comps]
            [juxt.crux-ui.frontend.views.query.results-tree :as q-results-tree]
           ;[juxt.crux-ui.frontend.views.query.results-table :as q-results-table]
            [juxt.crux-ui.frontend.views.query.results-grid :as q-results-table]
            [garden.core :as garden]
            [garden.stylesheet :as gs]
            [re-frame.core :as rf]
            [juxt.crux-ui.frontend.views.style :as s]
            [juxt.crux-ui.frontend.views.attr-stats :as attr-stats]
            [juxt.crux-ui.frontend.views.codemirror :as cm]))


(def ^:private -sub-query-res (rf/subscribe [:subs.query/result]))
(def ^:private -sub-output-tab (rf/subscribe [:subs.ui/output-main-tab]))
(def ^:private -sub-output-side-tab (rf/subscribe [:subs.ui/output-side-tab]))
(def ^:private -sub-results-table (rf/subscribe [:subs.query/results-table]))

(defn- query-output-edn []
  (let [raw @-sub-query-res
        fmt (with-out-str (cljs.pprint/pprint raw))]
    [cm/code-mirror fmt {:read-only? true}]))


(def empty-placeholder
  [:div.q-output-empty "Your query or transaction results here shall be"])

(defn set-side-tab [tab-name]
  (rf/dispatch [:evt.ui.output/side-tab-switch tab-name]))

(defn set-main-tab [tab-name]
  (rf/dispatch [:evt.ui.output/main-tab-switch tab-name]))


(def ^:private q-output-tabs-styles
  [:style
    (garden/css
      [:.output-tabs
       {:display :flex}
       [:&__item
        {:ee 3}]
       [:&__sep
        {:padding "0 8px"}]])])

(defn out-tab-item [tab active-tab on-click]
  [comps/button-textual
   {:on-click on-click :active? (= tab active-tab) :text (name tab)}])


(defn side-output-tabs [active-tab]
  [:div.output-tabs.output-tabs--side
   q-output-tabs-styles
   (interpose
     [:div.output-tabs__sep "/"]
     (for [tab-type [:db.ui.output-tab/tree :db.ui.output-tab/attr-stats]]
       ^{:key tab-type}
       [out-tab-item tab-type active-tab #(set-side-tab tab-type)]))])

(defn main-output-tabs [active-tab]
  [:div.output-tabs.output-tabs--main
   q-output-tabs-styles
   (interpose
     [:div.output-tabs__sep "/"]
     (for [tab-type [:db.ui.output-tab/table
                     :db.ui.output-tab/tree
                     :db.ui.output-tab/edn]]
       ^{:key tab-type}
       [out-tab-item tab-type active-tab #(set-main-tab tab-type)]))])


(def ^:private q-output-styles
  [:style
    (garden/css
      [:.q-output
       {:border "0px solid red"
        :height :100%
        :display :grid
        :position :relative
        :grid-template "'side main' 1fr / minmax(200px, 300px) 1fr"}
       [:&__side
        {:border-right s/q-ui-border
         :grid-area :side
         :overflow :hidden
         :position :relative}
        [:&__content
         {:overflow :auto
          :height :100%}]]
       [:&__main
        {:border-radius :2px
         :grid-area :main
         :position :relative
         :overflow :hidden}]
       ["&__main__links"
        "&__side__links"
        {:position :absolute
         :z-index 10
         :background :white
         :padding :8px
         :bottom  :0px
         :right   :0px}]]
      [:.q-output-edn
       {:padding :8px}]
      [:.q-output-empty
       {:height :100%
        :display :flex
        :color s/color-placeholder
        :align-items :center
        :justify-content :center}]
      (gs/at-media {:max-width :1000px}
        [:.q-output
         {:grid-template "'main main' 1fr / minmax(200px, 300px) 1fr"}
         [:&__side
          {:display :none}]]))])

(defn root []
  [:div.q-output
   q-output-styles

   [:div.q-output__side
    (let [out-tab @-sub-output-side-tab]
      [:<>
        [:div.q-output__side__content
            (case out-tab
              :db.ui.output-tab/attr-stats [attr-stats/root]
              :db.ui.output-tab/empty empty-placeholder
              [q-results-tree/root])]
        [:div.q-output__side__links
         [side-output-tabs out-tab]]])]

   [:div.q-output__main
    (if-let [out-tab @-sub-output-tab]
      [:<>
       (case out-tab
         :db.ui.output-tab/table [q-results-table/root @-sub-results-table]
         :db.ui.output-tab/tree  [q-results-tree/root]
         :db.ui.output-tab/edn   [query-output-edn]
         :db.ui.output-tab/empty empty-placeholder
         [q-results-table/root @-sub-results-table])
       [:div.q-output__main__links
        [main-output-tabs out-tab]]]
      empty-placeholder)]])
