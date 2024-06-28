(ns dhcp.components.database.common
  (:require
   [clojure.tools.logging :as log]
   [malli.core :as m]
   [malli.error :as me]
   [malli.experimental.time :as met]
   [malli.registry :as mr]))

(mr/set-default-registry!
 (mr/composite-registry
  (m/default-schemas)
  (met/schemas)))

(def ^:private ReservationSchema
  [:map {:closed true}
   [:hw-address [:and
                 bytes?
                 [:fn #(pos? (count %))]]]
   [:ip-address [:and
                 bytes?
                 [:fn #(= (count %) 4)]]]
   [:source [:enum "config" "api"]]])

(defn assert-reservation [reservation]
  (when-not (m/validate ReservationSchema reservation)
    (log/debugf "Invalid reservation: %s"
                (me/humanize (m/explain ReservationSchema reservation)))
    (throw (IllegalArgumentException. "Invalid reservation"))))

(def ^:private LeaseSchema
  [:and
   [:map {:closed true}
    [:client-id [:and
                 bytes?
                 [:fn #(pos? (count %))]]]
    [:hw-address [:and
                  bytes?
                  [:fn #(pos? (count %))]]]
    [:ip-address [:and
                  bytes?
                  [:fn #(= (count %) 4)]]]
    [:hostname string?]
    [:lease-time pos-int?]
    [:status [:enum "offer" "lease"]]
    [:offered-at :time/instant]
    [:leased-at [:maybe :time/instant]]
    [:expired-at :time/instant]]
   [:fn (fn [{:keys [:status :leased-at]}]
          (or (= status "offer")
              (some? leased-at)))]])

(defn assert-lease [lease]
  (when-not (m/validate LeaseSchema lease)
    (log/debugf "Invalid lease: %s" (me/humanize (m/explain LeaseSchema lease)))
    (throw (IllegalArgumentException. "Invalid lease"))))
