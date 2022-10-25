(ns meshmon.events
  (:require
   [ajax.core :as ajax]
   [day8.re-frame.http-fx]
   [day8.re-frame.tracing :refer-macros [fn-traced]]
   [meshmon.db :as db]
   [re-frame.core :as re-frame]
   ))

(re-frame/reg-event-db
 ::initialize-db
 (fn-traced [_ _]
   db/default-db))

(re-frame/reg-event-db
 ::switch-tab
 (fn-traced [db [_ tab-name]]
   (assoc db :active-tab-name tab-name)))

(re-frame/reg-event-db
 ::switch-packet
 (fn-traced [db [_ packet]]
   (assoc db :active-packet packet)))

(defn switch-direction [direction]
  (if (= direction :ascending) :descending :ascending))

(defn sort-by-packets [packet-key direction loaded-packets]
  (if (= direction :ascending)
    (sort-by packet-key loaded-packets)
    (sort-by packet-key #(compare %2 %1) loaded-packets)))

(re-frame/reg-event-db
 ::sort-packets
 (fn-traced [db [_ packet-key]]
   (let [[prev-key prev-direction] (:packets-sorted-by db)
         loaded-packets (:loaded-packets db)]
     (if (= prev-key packet-key)
       (assoc db
              :packets-sorted-by [packet-key (switch-direction prev-direction)]
              :loaded-packets (sort-by-packets packet-key (switch-direction prev-direction) loaded-packets))
       (assoc db
              :packets-sorted-by [packet-key :ascending]
              :loaded-packets (sort-by-packets packet-key :ascending loaded-packets))))))

(re-frame/reg-event-db
 :process-packets
 (fn-traced [db [_ response]]
   (assoc db :loaded-packets response)))

(re-frame/reg-event-db
 :bad-response
 (fn-traced [db [_ response]]
   (assoc db :errors response)))

(re-frame/reg-event-fx
 ::get-packets
 (fn-traced [_ _]
   {:http-xhrio {:method :get
                 :uri "http://localhost:5000/packets"
                 :response-format (ajax/json-response-format {:keywords? true})
                 :on-success [:process-packets]
                 :on-failure [:bad-response]}}))
