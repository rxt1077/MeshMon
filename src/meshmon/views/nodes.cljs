(ns meshmon.views.nodes
  (:require
   [re-frame.core :as re-frame]
   [reagent.core :as reagent]
   [react-leaflet :refer (MapContainer TileLayer Marker Tooltip useMap)]
   [meshmon.subs :as subs]
   [meshmon.events :as events]
   [meshmon.utils :as utils]
   ))

(defn nodes-row [[id node]]
  "Returns a <tr> for a node in the `node` map"
  (let [nodeinfo (:last-nodeinfo-packet node)
        nodeinfo-payload (:payload (:decoded nodeinfo))
        position (:last-position-packet node)
        position-payload (:payload (:decoded position))
        telemetry-payload (:payload (:decoded (:last-telemetry-packet node)))
        active-node (re-frame/subscribe [::subs/active-node])
        short-name (:shortName nodeinfo-payload)
        long-name (:longName nodeinfo-payload)]
    [:tr {:class (if (= id @active-node) "active-node-row" "node-row")
          :key id
          :on-click #(re-frame/dispatch [::events/toggle-node id])}
     [:td (if (some? short-name) short-name "???")]
     [:td (if (some? long-name)
            [:a {:on-click #(re-frame/dispatch [::events/switch-packet nodeinfo])}
                 long-name id])]
     [:td (if (some? position-payload)
            [:a {:on-click #(re-frame/dispatch [::events/switch-packet position])}
             (str (utils/latlon-to-str (:latitudeI position-payload)) ", "
                  (utils/latlon-to-str (:longitudeI position-payload)))])]
     [:td (if (contains? telemetry-payload :deviceMetrics) (str (:batteryLevel (:deviceMetrics telemetry-payload)) "%"))]
     [:td (utils/ts-to-str (:last-heard node))]]))

(defn nodes-table []
  [:table {:class "table is-narrow"}
   [:thead
    [:tr
     [:th "Short Name"]
     [:th "Long Name"]
     [:th "Position"]
     [:th "Battery"]
     [:th "Last Heard"]]]
   [:tbody
    (let [nodes (re-frame/subscribe [::subs/nodes])]
      (doall (map nodes-row @nodes)))]])

(defn position-packet-in-nodes? [nodes]
  "Returns true if there is at least one position packet in the nodes map"
  (> (count (filter
              (fn [[_ value]] (some? (:last-position-packet value)))
              nodes))
     0))

(defn fit-map! [nodes]
  "Used as a react component inside a MapContainer this function uses useMap
  to fit the map on the nodes if there are any or on [[-90 -180] [90 180]]."
 (let [bounds (if (position-packet-in-nodes? nodes)
                (utils/get-bounds nodes) [[-90 -180] [90 180]])
       _ (.fitBounds (useMap) (clj->js bounds))]
    [:div])) ;; have to return something I guess :)

(defn nodes-marker [[id node]]
  "This function is meant to be called by map with the nodes map. It creates
  markers and popups for each node."
  (if (some? (:last-position-packet node))
    (let [position-payload (:payload (:decoded (:last-position-packet node)))
         latitude (* (:latitudeI position-payload) 1e-7)
         longitude (* (:longitudeI position-payload) 1e-7)
         active-node (re-frame/subscribe [::subs/active-node])]
      [(reagent/adapt-react-class Marker)
       {:key id
        :position [latitude, longitude]
        :eventHandlers {:click #(re-frame/dispatch [::events/toggle-node id])}}
       (if (= id @active-node)
         [(reagent/adapt-react-class Tooltip)
          {:permanent true}
          (:longName (:payload (:decoded (:last-nodeinfo-packet node))))])])))

(defn nodes-map []
   (let [nodes (re-frame/subscribe [::subs/nodes])]
     [(reagent/adapt-react-class MapContainer)
      {:id "map"
       ;;:zoom 13
       :scrollWheelZoom false}
      [(reagent/adapt-react-class TileLayer)
       {:attribution "&copy; <a href='https://www.openstreetmap.org/copyright'>OpenStreetMap</a> contributors"
        :url "https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"}]
      (doall (map nodes-marker @nodes))
      [:f> fit-map! @nodes]]))

(defn nodes-chat-row [nodes packet]
  "Returns a div representing a message from a TEXT_MESSAGE_APP packet"
  (let [id (:from packet)
        nodeinfo (:payload (:decoded (:last-nodeinfo-packet (get nodes id))))
        long-name (:longName nodeinfo)]
    [:div {:key (:rowId packet)} 
     [:span {:class "chat-ts"}
      [:a {:on-click #(re-frame/dispatch [::events/switch-packet packet])}
       (utils/ts-to-str (:rxTime packet))]]
     [:span {:class "chat-name"} (if (nil? long-name) id long-name) ]
     [:span {:class "chat-text"} (:payload (:decoded packet))]]))

(defn nodes-chat []
  "Returns a display of the chat messages in the loaded packets"
  [:div {:class "nodes-chat"}
   [:h5 {:class "title is-5"} "Chat Messages"]
   (let [packets (re-frame/subscribe [::subs/loaded-packets])
         nodes (re-frame/subscribe [::subs/nodes])
         text-packets (filter #(= (:port %) "TEXT_MESSAGE_APP") @packets)]
     (doall (map #(nodes-chat-row @nodes %) text-packets)))])

(defn nodes []
  [:div
    [:div {:class "columns"}
     [:div {:class "column is-half"} (nodes-table)]
     [:div {:class "column is-half"} (nodes-map)]]
    (nodes-chat)])
