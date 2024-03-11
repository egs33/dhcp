(ns dhcp.components.database
  (:require
   [clojure.tools.logging :as log]
   [dhcp.util.bytes :as u.bytes]
   [malli.core :as m]
   [malli.error :as me]
   [malli.experimental.time :as met]
   [malli.registry :as mr])
  (:import
   (clojure.lang
    Atom)))

(mr/set-default-registry!
 (mr/composite-registry
  (m/default-schemas)
  (met/schemas)))

(defprotocol IDatabase
  (add-reservations [this reservations])
  (get-all-reservations [this])
  (find-reservations-by-hw-address [this ^bytes hw-address])
  (find-reservations-by-ip-address-range [this ^bytes start-address ^bytes end-address])
  (delete-reservation [this ^bytes hw-address])

  (add-lease [this lease])
  (get-all-leases [this])
  (find-leases-by-hw-address [this ^bytes hw-address])
  (find-leases-by-ip-address-range [this ^bytes start-address ^bytes end-address])
  (delete-lease [this ^bytes hw-address ^bytes start-address ^bytes end-address]))

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

(defrecord ^{:doc "Database Implementation for development. Clear data after restart."
             :private true}
  MemoryDatabase
  [^Atom state]
  IDatabase
  (add-reservations [_ reservations]
    (doseq [reservation reservations]
      (assert-reservation reservation))
    (swap! state #(update % :reservation into reservations)))
  (get-all-reservations [_]
    (:reservation @state))
  (find-reservations-by-hw-address [_  hw-address]
    (let [value (u.bytes/bytes->number hw-address)]
      (->> (:reservation @state)
           (filter #(= (u.bytes/bytes->number (:hw-address %))
                       value)))))
  (find-reservations-by-ip-address-range [_ start-address end-address]
    (let [start (u.bytes/bytes->number start-address)
          end (u.bytes/bytes->number end-address)]
      (->> (:reservation @state)
           (filter #(<= start (u.bytes/bytes->number (:ip-address %)) end)))))
  (delete-reservation [_ hw-address]
    (let [value (u.bytes/bytes->number hw-address)]
      (swap! state (fn [current]
                     (update current
                             :reservation
                             (fn [coll]
                               (remove #(= (u.bytes/bytes->number (:hw-address %))
                                           value)
                                       coll)))))))

  (add-lease [_ lease]
    (assert-lease lease)
    (swap! state #(update % :lease conj lease)))
  (get-all-leases [_]
    (:lease @state))
  (find-leases-by-hw-address [_ hw-address]
    (let [value (u.bytes/bytes->number hw-address)]
      (->> (:lease @state)
           (filter #(= (u.bytes/bytes->number (:hw-address %))
                       value)))))
  (find-leases-by-ip-address-range [_ start-address end-address]
    (let [start (u.bytes/bytes->number start-address)
          end (u.bytes/bytes->number end-address)]
      (->> (:lease @state)
           (filter #(<= start (u.bytes/bytes->number (:ip-address %)) end)))))
  (delete-lease [_ hw-address start-address end-address]
    (let [s-ip-value (u.bytes/bytes->number start-address)
          e-ip-value (u.bytes/bytes->number end-address)]
      (swap! state
             (fn [current]
               (update current
                       :lease
                       (fn [coll]
                         (remove #(and (u.bytes/equal? (:hw-address %)
                                                       hw-address)
                                       (<= s-ip-value
                                           (u.bytes/bytes->number (:ip-address %))
                                           e-ip-value))
                                 coll))))))))

(defn- new-memory-database []
  (->MemoryDatabase (atom {:reservation []
                           :lease []})))

(defn create-database [type]
  (case type
    "memory" (new-memory-database)
    (throw (IllegalArgumentException. (str "Unsupported database type: " type)))))
