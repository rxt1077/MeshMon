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
        active-node (re-frame/subscribe [::subs/active-node])]
    [:tr {:class (if (= id @active-node) "active-node-row" "node-row")
          :key id
          :on-click #(re-frame/dispatch [::events/toggle-node id])}
     [:td (:shortName nodeinfo-payload)]
     [:td (:longName nodeinfo-payload)]
     [:td (utils/latlon-to-str (:latitudeI position-payload)) ", " (utils/latlon-to-str (:longitudeI position-payload))]
     [:td "Battery"]
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

(defn total-latlon [nodes]
  "Returns a vector with the sum of all the integer latitudes and longitudes in
  the nodes list."
  (reduce
    (fn [total [id node]]
      (if (some? (:last-position-packet node))
        (let [position-payload (:payload (:decoded (:last-position-packet node)))]
          [(+ (first total) (:latitudeI position-payload)) (+ (second total) (:longitudeI position-payload))])
        total))
    [0 0]
    nodes))

(defn nodes-center [nodes]
  "Returns the average latitude and longitude for a map of nodes."
  (let [total (total-latlon nodes)
        count-nodes (count nodes)]
    [(* (/ (first total) count-nodes) 1e-7)
     (* (/ (second total) count-nodes) 1e-7)]))

(defn center-map! [nodes]
  "Used as a react component inside a MapContainer this function uses useMap
  to center the map on the nodes if there are any or on [0 0]."
 (let [center (if (not-empty nodes) (nodes-center nodes) [0 0])
       _ (.setView (useMap) (clj->js center) 13)]
    [:div])) ;; have to return something I guess :)

(defn nodes-marker [[id node]]
  "This function is mean to be called by map with the nodes map. It creates
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
      [:f> center-map! @nodes]]))

(defn nodes []
  [:div
    [:div
      [:div  "Actions (loading packets, etc.)"]
      [:button {:class "button" :on-click #(re-frame/dispatch [::events/get-packets])} "Get Packets"]]
    [:div {:class "columns"}
     [:div {:class "column is-third"} (nodes-table)]
     [:div {:class "column is-third"} (nodes-map)]]
    [:div {:class "column is-third"} "Chat"]])

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
      (let [packets (re-frame/subscribe [::subs/loaded-packets])]
        (doall (map packet-row @packets)))]]])

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

(defn packet-info []
  (let [packet (re-frame/subscribe [::subs/active-packet])]
    (if (nil? @packet)
      [:div {:class "box"} "Click on a packet to see more"]
      [:div {:class "box"}
       (mesh-packet-info @packet)
       (data-info (:decoded @packet))
       (case (:portnum (:decoded @packet))
         "NODEINFO_APP" (user-info (:payload (:decoded @packet)))
         "POSITION_APP" (position-info (:payload (:decoded @packet))))])))

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

(defn app []
 [:div
  [:h1 {:class "title"} "MeshMon"]
  (tabs)
  (panel)])
