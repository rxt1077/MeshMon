(ns meshmon.utils)

(defn ts-to-str [ts]
  "Converts a timestamp in seconds since the epoch to the ISO string version."
  (.toISOString (new js/Date (* ts 1000))))

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
