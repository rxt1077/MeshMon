(ns meshmon.db
  (:require
    [meshmon.utils :as utils])) 

(def default-db
  {:active-tab-name "Packets"
   :loaded-packets nil
   :active-packet nil
   :packets-sorted-by [:rxTime :ascending]
   :nodes {}
   :active-node nil
   :start-ts 0
   :end-ts (int (/ (.getTime (new js/Date)) 1000))})
