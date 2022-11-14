(ns meshmon.views.main
  (:require
   [re-frame.core :as re-frame]
   [meshmon.subs :as subs]
   [meshmon.events :as events]
   [meshmon.utils :as utils]
   [meshmon.views.packets :as packets]
   [meshmon.views.nodes :as nodes]
   ))

(defn tab-item [tab-name]
  (let [active-tab-name (re-frame/subscribe [::subs/active-tab-name])]
    [:li
     (if (= tab-name @active-tab-name)
       {:class "is-active"}
       {:class ""})
     [:a {:class "title is-5" :on-click #(re-frame/dispatch [::events/switch-tab tab-name])} tab-name]]))

(defn tabs []
  [:div {:class "tabs"}
   [:ul
    (tab-item "Packets")
    (tab-item "Nodes")]])

(defn panel []
  (let [active-tab-name @(re-frame/subscribe [::subs/active-tab-name])]
    (case active-tab-name
      "Packets" (packets/packets)
      "Nodes" (nodes/nodes))))

(defn actions []
  "Returns the list of actions a user can take: load packets, etc."
  (let [start-ts @(re-frame/subscribe [::subs/start-ts])
        end-ts @(re-frame/subscribe [::subs/end-ts])
        active-tab-name @(re-frame/subscribe [::subs/active-tab-name])]
    [:div {:class "actions"}
     [:h5 {:class "title is-5"} "Actions"]
     [:div {:class "field is-horizontal"}
      [:button {:class "button"
                :on-click #(re-frame/dispatch [::events/get-all-packets])} "Get All Packets"]
      [:button {:class "button"
                :on-click #(re-frame/dispatch [::events/get-next-packet])} "Get Next Packet"]
      [:button {:class "button"
                :on-click #(re-frame/dispatch [::events/get-new-packets])} "Get New Packets"]
      [:button {:class "button"
                :on-click #(re-frame/dispatch [::events/get-range-packets])} "Get Packets In Range"]
      [:div {:class "field-label is-normal"}
       [:label {:class "label"} "Start"]]
      [:input {:class "input"
               :type "datetime-local"
               :step 1
               :value (utils/ts-to-datetime-local start-ts)
               :on-change #(re-frame/dispatch [::events/set-start-ts (-> % .-target .-value)])}]
      [:div {:class "field-label is-normal"}
       [:label {:class "label"} "End"]]
      [:input {:class "input"
               :type "datetime-local"
               :step 1
               :value (utils/ts-to-datetime-local end-ts)
               :on-change #(re-frame/dispatch [::events/set-end-ts (-> % .-target .-value)])}]]
     ;; these actions only make sense when you can see the selected packet
     (if (= active-tab-name "Packets")
       [:div {:class "field is-horizontal"}
        [:button {:class "button"
                  :on-click #(re-frame/dispatch [::events/drop-packets :before])} "Drop Packets Before Selected"]
        [:button {:class "button"
                  :on-click #(re-frame/dispatch [::events/drop-packets :after])} "Drop Packets After Selected"]])]))

(defn app []
 [:div
  [:h1 {:class "title"} "MeshMon"]
  (actions)
  (tabs)
  (panel)])
