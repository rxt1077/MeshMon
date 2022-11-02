(ns meshmon.db
  (:require
    [meshmon.utils :as utils])) 

(def default-db
  {:active-tab-name "Nodes"
   :loaded-packets nil
   :active-packet nil
   :packets-sorted-by [:rxTime :ascending]
   :nodes {}
   :active-node nil
   :start-ts 0
   :end-ts (/ (.getTime (new js/Date)) 1000)})
