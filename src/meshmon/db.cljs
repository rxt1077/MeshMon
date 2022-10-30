(ns meshmon.db)

(def default-db
  {:active-tab-name "Nodes"
   :loaded-packets nil
   :active-packet nil
   :packets-sorted-by [:rxTime :ascending]
   :nodes {}
   :active-node nil})
