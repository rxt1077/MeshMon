(ns meshmon.views.packets
  (:require
   [re-frame.core :as re-frame]
   [reagent.core :as reagent]
   [meshmon.subs :as subs]
   [meshmon.events :as events]
   [meshmon.utils :as utils]
   ))

(defn packet-row [packet]
 "Takes a `packet`, the `active-packet`, and the `nodes` map and returns a <tr>
 for a single packet" 
  (let [active-packet (re-frame/subscribe [::subs/active-packet])
        nodes @(re-frame/subscribe [::subs/nodes])
        to (utils/id-with-short-name nodes (:to packet))
        from (utils/id-with-short-name nodes (:from packet))]
    [:tr
     {:class (if (= @active-packet packet) "active-packet-row" "packet-row")
      :on-click #(re-frame/dispatch [::events/switch-packet packet])
      :key (:rowId packet)}
     [:td (utils/ts-to-str (:rxTime packet))]
     [:td to]
     [:td from]
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
  (reagent/create-class
    {:display-name "packets-table"
     :component-did-mount
     ;; make sure active packet is visible, we may come here from elsewhere
     (fn [this]
       (when-let [el (first (js/document.getElementsByClassName "active-packet-row"))]
         (.scrollIntoView el)))
     :reagent-render
     (fn []
       [:div {:class "table-container packets-table"}
         [:table {:class "table is-bordered is-narrow is-fullwidth"}
          [:thead
           [:tr
            (packets-table-th "rx_time" :rxTime)
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
             (doall (map packet-row sorted-packets)))]]])}))

(defn packet-info-col [label data units]
  [:div {:class "column is-3"}
   [:span {:class "packet-info-title"} label] " "
   (if (boolean? data) (if data "True" "False") data) units ])

(defn mesh-packet-info [nodes packet]
  [:div {:class "mesh-packet-info"}
   [:div {:class "packet-info-title"} "MeshPacket"]
   [:div {:class "columns is-multiline is-gapless"}
    (packet-info-col "To:" (utils/id-with-short-name nodes (:to packet)) "") 
    (packet-info-col "From:" (utils/id-with-short-name nodes (:from packet)) "") 
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
  (let [packet @(re-frame/subscribe [::subs/active-packet])
        nodes @(re-frame/subscribe [::subs/nodes])]
    (if (nil? packet)
      [:div {:class "box"} "Click on a packet to see more"]
      [:div {:class "box"}
       (mesh-packet-info nodes packet)
       (data-info (:decoded packet))
       (case (:portnum (:decoded packet))
         "NODEINFO_APP" (user-info (:payload (:decoded packet)))
         "POSITION_APP" (position-info (:payload (:decoded packet)))
         "TELEMETRY_APP" (telemetry-info (:payload (:decoded packet)))
         "TEXT_MESSAGE_APP" (text-info (:payload (:decoded packet))))])))

(defn packets []
  [:div
    (reagent/as-element [packets-table])
    (packet-info)
  ])
