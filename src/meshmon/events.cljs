(ns meshmon.events
  (:require
   [ajax.core :as ajax]
   [day8.re-frame.http-fx]
   [day8.re-frame.tracing :refer-macros [fn-traced]]
   [meshmon.db :as db]
   [meshmon.utils :as utils]
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
;; makes a new packet active in the loaed-packet table
 ::switch-packet
 (fn-traced [db [_ packet]]
   (assoc db :active-packet packet :active-tab-name "Packets")))

(defn switch-direction [direction]
  (if (= direction :ascending) :descending :ascending))

(defn sort-by-packets [packet-key direction loaded-packets]
  (if (= direction :ascending)
    (sort-by packet-key loaded-packets)
    (sort-by packet-key #(compare %2 %1) loaded-packets)))

(re-frame/reg-event-db
 ::sort-packets
 (fn-traced [db [_ packet-key]]
   (let [[prev-key prev-direction] (:packets-sorted-by db)]
     (if (= prev-key packet-key)
       (assoc db
              :packets-sorted-by [packet-key (switch-direction prev-direction)])
       (assoc db
              :packets-sorted-by [packet-key :ascending])))))

(defn decode-packets [packets]
  "Takes a collection of packets and converts the :decoded field from text to
  JSON to EDN"
  (map
    #(assoc % :decoded
            (js->clj (.parse js/JSON (:decoded %)) :keywordize-keys true))
    packets))

(defn packets->sorted-set [packets]
  "Takes a collection of packets and puts them in a sorted-set based on rowId"
  (apply sorted-set-by #(compare (:rowId %1) (:rowId %2)) packets))

(defn create-nodes [decoded-packets]
  "Returns an `nodes` map based on a set of decoded packets."
  doall(
    reduce
      (fn [new-nodes packet]
        (let [id (:from packet)
              node (get new-nodes id
                        {:last-nodeinfo-packet nil
                         :last-position-packet nil
                         :last-telemetry-packet nil
                         :last-heard nil
                         :selected false})]
          (assoc new-nodes id
                 (case (:port packet)
                   "NODEINFO_APP"
                   (assoc node
                          :last-heard (:rxTime packet)
                          :last-nodeinfo-packet packet)
                  "POSITION_APP"
                   (assoc node
                          :last-heard (:rxTime packet)
                          :last-position-packet packet)
                  "TELEMETRY_APP"
                   (assoc node
                          :last-heard (:rxTime packet)
                          :last-telemetry-packet packet)
                   "TEXT_MESSAGE_APP"
                   (assoc node
                          :last-heard (:rxTime packet)
                          :last-telemetry-packet packet)))))
      {}
      decoded-packets))

(defn update-db [db packets]
  "Returns a new db with `loaded-packets` and `nodes` updated"
   (assoc db
          :loaded-packets (packets->sorted-set packets)
          :nodes (create-nodes packets)))

(re-frame/reg-event-db
;; Replaces the current `:loaded-packets` in the db with a decoded, sorted-set
;; of packets from the response.
 :process-packets-replace
 (fn-traced [db [_ response]]
            (update-db db (decode-packets response))))

(re-frame/reg-event-db
 :process-packets-append
 (fn-traced [db [_ response]]
            (update-db db (into (:loaded-packets db) (decode-packets response)))))

(re-frame/reg-event-db
 :bad-response
 (fn-traced [db [_ response]]
   (assoc db :errors response)))

(re-frame/reg-event-fx
 ::get-all-packets
 (fn-traced [_ _]
   {:http-xhrio {:method :get
                 :uri "http://localhost:5000/packets"
                 :response-format (ajax/json-response-format {:keywords? true})
                 :on-success [:process-packets-replace]
                 :on-failure [:bad-response]}}))

(re-frame/reg-event-fx
 ::get-next-packet
 (fn-traced [cofx _]
   {:http-xhrio {:method :get
                 :uri (str "http://localhost:5000/packets/one-after/"
                           (:rowId (last (:loaded-packets (:db cofx)))))
                 :response-format (ajax/json-response-format {:keywords? true})
                 :on-success [:process-packets-append]
                 :on-failure [:bad-response]}}))

(re-frame/reg-event-fx
 ::get-new-packets
 (fn-traced [cofx _]
   {:http-xhrio {:method :get
                 :uri (str "http://localhost:5000/packets/after/"
                           (:rowId (last (:loaded-packets (:db cofx)))))
                 :response-format (ajax/json-response-format {:keywords? true})
                 :on-success [:process-packets-append]
                 :on-failure [:bad-response]}}))

(re-frame/reg-event-fx
 ::get-range-packets
 (fn-traced [cofx _]
   (let [db (:db cofx)
         start-ts (:start-ts db)
         end-ts (:end-ts db)]
     {:http-xhrio {:method :get
                   :uri (str "http://localhost:5000/packets/range/" start-ts "-"
                             end-ts)
                   :response-format (ajax/json-response-format {:keywords? true})
                   :on-success [:process-packets-replace]
                   :on-failure [:bad-response]}})))

(re-frame/reg-event-db
 ::toggle-node
 (fn-traced [db [_ id]]
   (assoc db :active-node (if (= (:active-node db) id) nil id))))

(re-frame/reg-event-db
 ::set-start-ts
 (fn-traced [db [_ value]]
   (assoc db :start-ts (utils/datetime-local-to-ts value))))

(re-frame/reg-event-db
 ::set-end-ts
 (fn-traced [db [_ value]]
   (assoc db :end-ts (utils/datetime-local-to-ts value))))

(re-frame/reg-event-db
 ::drop-packets
 ;; Drops packets before the selected packet, taking into account how the
 ;; packets are sorted
 (fn-traced [db [_ value]]
   (let [loaded-packets (:loaded-packets db)
         [sort-key sort-dir] (:packets-sorted-by db)
         selected-value (sort-key (:active-packet db))
         comp-fun ;; comparator function
         (if (= value :before)
           ;; drop packets before
           (if (= sort-dir :ascending)
             ;; what to keep
             #(>= (sort-key %) selected-value) #(<= (sort-key %) selected-value))
           ;; drop packets after
           (if (= sort-dir :ascending)
             ;; what to keep
             #(<= (sort-key %) selected-value) #(>= (sort-key %) selected-value)))
         new-loaded-packets (filter comp-fun loaded-packets)]
     (update-db db new-loaded-packets))))
