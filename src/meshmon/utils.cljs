(ns meshmon.utils)

(defn latlon-to-str [x]
  "Converts a latitude of longitude represented as an int to a string with the degree symbol."
  (str (.toFixed (* x 1e-7) 5) "°"))

(defn latitudes [nodes]
  "Returns a vector of all the latitudes in the nodes list"
  (reduce
    (fn [lats [_ node]]
      (if (some? (:last-position-packet node))
        (let [position-payload (:payload (:decoded (:last-position-packet node)))
              latitude (* (:latitudeI position-payload) 1e-7)]
          (conj lats latitude))
        lats))
    []
    nodes))

(defn longitudes [nodes]
  "Returns a vector of all the longitudes in the nodes list"
  (reduce
    (fn [lons [_ node]]
      (if (some? (:last-position-packet node))
        (let [position-payload (:payload (:decoded (:last-position-packet node)))
              longitude (* (:longitudeI position-payload) 1e-7)]
          (conj lons longitude))
        lons))
    []
    nodes))

(defn get-bounds [nodes]
  "Returns a vector with the top-left and bottom-right latitude and longitude
  for the nodes in the nodes list"
  (let [lats (latitudes nodes)
        lons (longitudes nodes)
        max-lat (apply max lats)
        max-lon (apply max lons)
        min-lat (apply min lats)
        min-lon (apply min lons)]
    [[min-lat min-lon] [max-lat max-lon]]))

(defn get-short-name [nodes id]
  "Returns the short name of a node if it is know. Returns nil otherwise."
  (:shortName (:payload (:decoded (:last-nodeinfo-packet (get nodes id))))))

(defn id-with-short-name [nodes id]
  "Returns a string that is the id with the short name in parenthesis if it is
  available."
  (let [short-name (get-short-name nodes id)]
   (if (some? short-name) (str id " (" short-name ")") (str id))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;; Timestamp Utils ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn ts-to-str [ts]
  "Converts a timestamp in seconds since the epoch to the ISO string version."
  (.toLocaleString (new js/Date (* ts 1000))))

(defn js-to-datetime-local [js-date]
  "Takes a JS Date and returns a string that can be used in an HTML
  datetime-local input.
  Source: https://dev.to/mendyberger/input-and-js-dates-2lhc"
  (clojure.string/replace
    (.toLocaleString
      js-date "sv-SE" (clj->js {:year "numeric" :month "2-digit" :day "2-digit"
                                :hour "2-digit" :minute "2-digit"
                                :second "2-digit"})) " " "T"))

(defn datetime-local-to-ts [datetime-local-date]
  "Takes the value of a datetime-local HTML input and converts it to seconds
  since the Epoch in UTC."
  (int (/ (.getTime (new js/Date datetime-local-date)) 1000)))

(defn ts-to-datetime-local [ts]
  "Takes a timestamp in seconds since the Epoch UTIC and converts it to a
  string that can be used by a datetime-local HTML input."
  (js-to-datetime-local (new js/Date (* ts 1000))))
