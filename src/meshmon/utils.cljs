(ns meshmon.utils)

(defn ts-to-str [ts]
  "Converts a timestamp in seconds since the epoch to the ISO string version."
  (.toISOString (new js/Date (* ts 1000))))

(defn latlon-to-str [x]
  "Converts a latitude of longitude represented as an int to a string with the degree symbol."
  (str (.toFixed (* x 1e-7) 5) "°"))
