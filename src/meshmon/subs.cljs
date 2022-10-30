(ns meshmon.subs
  (:require
   [re-frame.core :as re-frame]))

(re-frame/reg-sub
 ::active-tab-name
 (fn [db]
   (:active-tab-name db)))

(re-frame/reg-sub
 ::loaded-packets
 (fn [db]
   (:loaded-packets db)))

(re-frame/reg-sub
 ::active-packet
 (fn [db]
   (:active-packet db)))

(re-frame/reg-sub
 ::packets-sorted-by
 (fn [db]
   (:packets-sorted-by db)))

(re-frame/reg-sub
 ::nodes
 (fn [db]
   (:nodes db)))

(re-frame/reg-sub
 ::active-node
 (fn [db]
   (:active-node db)))
