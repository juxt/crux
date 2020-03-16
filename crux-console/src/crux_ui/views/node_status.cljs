(ns crux-ui.views.node-status
  (:require [re-frame.core :as rf]
            [garden.core :as garden]
            [garden.stylesheet :as gs]
            [crux-ui.views.commons.contenteditable :as editable]
            [crux-ui.functions :as f]
            [crux-ui.views.commons.keycodes :as kc]
            [crux-ui.views.functions :as vf]
            [crux-ui.views.style :as s]
            [crux-ui.views.commons.dom :as dom]))



(def ^:private -sub-node-addr (rf/subscribe [:subs.sys/host]))
(def ^:private -sub-node-status (rf/subscribe [:subs.sys.host/status]))

(defn- on-host-change [{v :value :as change-complete-evt}]
  (rf/dispatch [:evt.db/host-change v]))

(def ^:private node-styles
  [:style
   (garden/css
     [:.led
      (let [size :8px]
        {:height size
         :width size
         :background :grey
         :margin-right :8px
         :border-radius size})
      [:&--on
       {:background :green}]]
     [:.node
      {:display :flex
       :position :relative
       :justify-content :space-between
       :align-items :center
       :width "100%"}
      [:>.node__status
       {:display :none}]
      [:&:hover
       [:>.node__status
        {:position      :absolute
         :background    :white
         :padding :8px
        ;:max-width :500px
         :font-family "monospace"
         :display :block
         :z-index       1000
         :font-size :1.2rem
         :border-radius :2px
         :border        s/q-ui-border
         :top           :32px}]]
      [:&__addr
       {:display "flex"
        :justify-content "space-between"
        :overflow :hidden
        :white-space :nowrap
        :text-overflow :ellipsis
        :flex "1 1 120px"
        :align-items "center"}]]
     (gs/at-media {:max-width :1000px}
       [".node>.node__status"
        {:display :none}]))])


(defn led [on?]
  [:div (vf/bem :led {:on on? :off (not on?)})])

(defn node-status []
  [:div.node
   node-styles
   [:div.node__led [led (boolean @-sub-node-status)]]
   [:div.node__status
    (if-let [node-info @-sub-node-status]
      [:pre (f/pprint-str node-info)] ; alternatively [edn/root node-info]
      [:pre "Cannot connect to the specified node address"])]
   [:div.node__addr
    [editable/div ::host-input
     {:on-change-complete on-host-change
      :on-key-down
      (dom/dispatch-on-keycode
        {::kc/enter #(some-> % (.-target) (.blur))})
      :placeholder        "Node hostname/and-path"
      :value              @-sub-node-addr}]]])
