(ns meshmon.views
  (:require
   [re-frame.core :as re-frame]
   [reagent.core :as reagent]
   [react-leaflet :refer (MapContainer TileLayer Marker Tooltip useMap)]
   [meshmon.subs :as subs]
   [meshmon.events :as events]
   [meshmon.utils :as utils]
   ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;; Nodes View ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn nodes-row [[id node]]
  (let [nodeinfo-payload (:payload (:decoded (:last-nodeinfo-packet node)))
        position-payload (:payload (:decoded (:last-position-packet node)))
        telemetry-payload (:payload (:decoded (:last-telemetry-packet node)))
        active-node (re-frame/subscribe [::subs/active-node])]
    [:tr {:class (if (= id @active-node) "active-node-row" "node-row")
          :key id
          :on-click #(re-frame/dispatch [::events/toggle-node id])}
     [:td (:shortName nodeinfo-payload)]
     [:td (:longName nodeinfo-payload)]
     [:td (utils/latlon-to-str (:latitudeI position-payload)) ", " (utils/latlon-to-str (:longitudeI position-payload))]
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

(defn fit-map! [nodes]
  "Used as a react component inside a MapContainer this function uses useMap
  to fit the map on the nodes if there are any or on [[-90 -180] [90 180]]."
 (let [bounds (if (not-empty nodes) (utils/get-bounds nodes) [[-90 -180] [90 180]])
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
     [:span {:class "chat-ts"} (utils/ts-to-str (:rxTime packet))]
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

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;; Packets View ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn packet-row [packet]
  (let [active-packet (re-frame/subscribe [::subs/active-packet])]
    [:tr
     {:class (if (= @active-packet packet) "active-packet-row" "packet-row")
      :on-click #(re-frame/dispatch [::events/switch-packet packet])
      :key (:rowId packet)}
     [:td (utils/ts-to-str (:rxTime packet))]
     [:td (:id packet)]
     [:td (:to packet)]
     [:td (:from packet)]
     [:td (:port packet)]
     [:td (:channel packet)]
     [:td (:hopLimit packet)]
     [:td (:priority packet)]
     [:td (if (= (:wantAck packet) 1) "True" "False")]
     [:td (:delayed packet)]
     [:td (:rxRssi packet)]
     [:td (:rxSnr packet)]]))

(defn packets-table-th [label packet-key]
  (let [packets-sorted-by (re-frame/subscribe [::subs/packets-sorted-by])
        [sorted-key direction] @packets-sorted-by]
    [:th {:on-click #(re-frame/dispatch [::events/sort-packets packet-key])}
     label " "
     (if (= sorted-key packet-key)
       (if (= direction :ascending)
         [:i {:class "fa-solid fa-caret-up"}]
         [:i {:class "fa-solid fa-caret-down"}]))]))

(defn packets-table []
  [:div {:class "table-container packets-table"}
    [:table {:class "table is-bordered is-narrow is-fullwidth"}
     [:thead
      [:tr
       (packets-table-th "rx_time" :rxTime)
       (packets-table-th "id" :id)
       (packets-table-th "to" :to)
       (packets-table-th "from" :from)
       (packets-table-th "port" :port)
       (packets-table-th "channel" :channel)
       (packets-table-th "hop_limit" :hopLimit)
       (packets-table-th "priority" :priority)
       (packets-table-th "want_ack" :wantAck)
       (packets-table-th "delayed" :delayed)
       (packets-table-th "rx_rssi" :rxRssi)
       (packets-table-th "rx_snr" :rxSnr)]]
     [:tbody
      (let [[sort-key sort-dir] @(re-frame/subscribe [::subs/packets-sorted-by])
            packets @(re-frame/subscribe [::subs/loaded-packets])
            sorted-packets (if (= sort-dir :ascending)
                             (sort-by sort-key packets)
                             (sort-by sort-key #(compare %2 %1) packets))]
        (doall (map packet-row sorted-packets)))]]])

(defn packet-info-col [label data units]
  [:div {:class "column is-3"}
   [:span {:class "packet-info-title"} label] " "
   (if (boolean? data) (if data "True" "False") data) units ])


(defn mesh-packet-info [packet]
  [:div {:class "mesh-packet-info"}
   [:div {:class "packet-info-title"} "MeshPacket"]
   [:div {:class "columns is-multiline is-gapless"}
    (packet-info-col "To:" (:to packet) "") 
    (packet-info-col "From:" (:from packet) "") 
    (packet-info-col "ID:" (:id packet) "") 
    (packet-info-col "rx Time:" (utils/ts-to-str (:rxTime packet)) "")
    (packet-info-col "rx RSSI:" (:rxRssi packet) " dBm")
    (packet-info-col "rx SNR:" (:rxSnr packet) "")
    (packet-info-col "Channel:" (:channel packet) "")
    (packet-info-col "Hop Limit:" (:hopLimit packet) "")
    (packet-info-col "Want ACK?:" (:wantAck packet) "")
    (packet-info-col "Priority:" (:priority packet) "")
    (packet-info-col "Delayed" (:delayed packet) "")]])

(defn data-info [decoded]
  [:div {:class "data-info"}
   [:div {:class "packet-info-title"} "Data"]
   [:div {:class "columns is-multiline is-gapless"}
    (packet-info-col "PortNum:" (:portnum decoded) "") 
    (packet-info-col "Destination:" (:dest decoded) "") 
    (packet-info-col "Source:" (:source decoded) "") 
    (packet-info-col "Want Response?" (:wantResponse decoded) "")
    (packet-info-col "Request ID:" (:requestId decoded) "")
    (packet-info-col "Reply ID:" (:replyId decoded) "")
    (packet-info-col "Emoji:" (:emoji decoded) "")]])

(defn user-info [payload]
  [:div {:class "user-info"}
   [:div {:class "packet-info-title"} "User"]
   [:div {:class "columns is-multiline is-gapless"}
    (packet-info-col "ID:" (:id payload) "") 
    (packet-info-col "Long Name:" (:longName payload) "") 
    (packet-info-col "Short Name:" (:shortName payload) "") 
    (packet-info-col "MAC Address:" (:macaddr payload) "")
    (packet-info-col "Hardware Model:" (:hwModel payload) "")
    (packet-info-col "Is Licensed?" (:isLicensed payload) "")]])

(defn position-info [payload]
  [:div {:class "position-info"}
   [:div {:class "packet-info-title"} "Position"]
   [:div {:class "columns is-multiline is-gapless"}
    (packet-info-col "Latitude: " (utils/latlon-to-str (:latitudeI payload)) "")
    (packet-info-col "Longitude: " (utils/latlon-to-str (:longitudeI payload)) "")
    (packet-info-col "Altitude: " (:altitude payload) " m")
    (packet-info-col "Time: " (utils/ts-to-str (:time payload)) "")
    (packet-info-col "Location Source: " (:locationSource payload) "")
    (packet-info-col "Altitude Source: " (:altitudeSource payload) "")
    (packet-info-col "Timestamp: " (:timestamp payload) " s")
    (packet-info-col "Timestamp Adjust: " (:timestampMillisAdjust payload) " ms")
    (packet-info-col "HAE Altitude: " (:altitudeHae payload) " m")
    (packet-info-col "Geoidal Separation: " (:altitudeGeoidalSeparation payload) " m")
    (packet-info-col "PDOP: " (:PDOP payload) "")
    (packet-info-col "HDOP: " (:HDOP payload) "")
    (packet-info-col "VDOP: " (:VDOP payload) "")
    (packet-info-col "GPS Accuracy: " (:gpsAccuracy payload) " mm")
    (packet-info-col "Ground Speed: " (:groundSpeed payload) " m/s")
    (packet-info-col "Ground Track: " (:groundTrack payload) "")
    (packet-info-col "Fix Quality: " (:fixQuality payload) "")
    (packet-info-col "Fix Type: " (:fixType payload) "")
    (packet-info-col "Satellites in View: " (:satsInView payload) "")
    (packet-info-col "Sensor ID: " (:sensorId payload) "")
    (packet-info-col "Next Update: " (:nextUpdate payload) " s")
    (packet-info-col "Sequence Number: " (:seqNumber payload) "")]])

(defn device-metrics-info [device-metrics]
  [:div {:class "device-metrics-info"}
   [:div {:class "packet-info-title"} "Device Metrics"]
   [:div {:class "columns is-multiline is-gapless"}
    (packet-info-col "Battery Level: " (:batteryLevel device-metrics) "%")
    (packet-info-col "Voltage: " (:voltage device-metrics) "V")
    (packet-info-col "Channel Utilization: " (:channelUtilization device-metrics) "%")
    (packet-info-col "Tx Airtime Utilization: " (:airUtilTx device-metrics) "%")]])

(defn telemetry-info [payload]
  [:div {:class "telemetry-info"}
   [:div {:class "packet-info-title"} "Telemetry"]
   [:div {:class "columns is-multiline is-gapless"}
    (packet-info-col "Time: " (utils/ts-to-str (:time payload)) "")]]
  (if (contains? payload :deviceMetrics)
    (device-metrics-info (:deviceMetrics payload))))

(defn text-info [payload]
  [:div {:class "text-info"} payload])

(defn packet-info []
  (let [packet (re-frame/subscribe [::subs/active-packet])]
    (if (nil? @packet)
      [:div {:class "box"} "Click on a packet to see more"]
      [:div {:class "box"}
       (mesh-packet-info @packet)
       (data-info (:decoded @packet))
       (case (:portnum (:decoded @packet))
         "NODEINFO_APP" (user-info (:payload (:decoded @packet)))
         "POSITION_APP" (position-info (:payload (:decoded @packet)))
         "TELEMETRY_APP" (telemetry-info (:payload (:decoded @packet)))
         "TEXT_MESSAGE_APP" (text-info (:payload (:decoded @packet))))])))

(defn packets []
  [:div
    (packets-table)
    (packet-info)
  ])

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;; Main View ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn tab-item [tab-name]
  (let [active-tab-name (re-frame/subscribe [::subs/active-tab-name])]
    [:li
     (if (= tab-name @active-tab-name)
       {:class "is-active"}
       {})
     [:a {:on-click #(re-frame/dispatch [::events/switch-tab tab-name])} tab-name]]))

(defn tabs []
  [:div {:class "tabs"}
   [:ul
    (tab-item "Nodes")
    (tab-item "Packets")]])

(defn panel []
  (let [active-tab-name (re-frame/subscribe [::subs/active-tab-name])]
    (case @active-tab-name
      "Nodes" (nodes)
      "Packets" (packets))))

(defn actions []
  "Returns the list of actions a user can take: load packets, etc."
  (let [start-ts @(re-frame/subscribe [::subs/start-ts])
        end-ts @(re-frame/subscribe [::subs/end-ts])]
    [:div {:class "actions"}
     [:h5 {:class "title is-5"} "Actions"]
     [:div {:class "field is-horizontal"}
      [:button {:class "button"
                :on-click #(re-frame/dispatch [::events/get-all-packets])} "Get All Packets"]
      [:button {:class "button"
                :on-click #(re-frame/dispatch [::events/get-next-packet])} "Get Next Packet"]
      [:button {:class "button"
                :on-click #(re-frame/dispatch [::events/get-new-packets])} "Get New Packets"]
      [:button {:class "button"} "Get Packets in Range"]
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
               :on-change #(re-frame/dispatch [::events/set-end-ts (-> % .-target .-value)])}]]]))

(defn app []
 [:div
  [:h1 {:class "title"} "MeshMon"]
  (actions)
  (tabs)
  (panel)])
