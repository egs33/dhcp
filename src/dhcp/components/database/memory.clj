(ns dhcp.components.database.memory
  (:require
   [dhcp.components.database.common :as db.common]
   [dhcp.protocol.database :as p.db]
   [dhcp.util.bytes :as u.bytes])
  (:import
   (clojure.lang
    Atom)))

(defrecord ^{:doc "Database Implementation for development. Clear data after restart."
             :private true}
  MemoryDatabase
  [^Atom state]
  p.db/IDatabase
  (add-reservations [_ reservations]
    (doseq [reservation reservations]
      (db.common/assert-reservation reservation))
    (let [reservations (mapv #(assoc % :id (rand-int Integer/MAX_VALUE)) reservations)]
      (swap! state #(update % :reservation into reservations))))
  (get-all-reservations [_]
    (:reservation @state))
  (find-reservation-by-id [_ id]
    (->> (:reservation @state)
         (filter #(= (:id %) id))
         first))
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
  (delete-reservations-by-source [_ source]
    (swap! state (fn [current]
                   (update current
                           :reservation
                           (fn [coll]
                             (remove #(= (:source %) source)
                                     coll))))))

  (add-lease [_ lease]
    (db.common/assert-lease lease)
    (let [lease (assoc lease :id (rand-int Integer/MAX_VALUE))]
      (swap! state #(update % :lease conj lease))
      lease))
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
  (find-lease-by-id [_ lease-id]
    (->> (:lease @state)
         (filter #(= (:id %) lease-id))
         first))
  (update-lease [_  hw-address ip-address values]
    (swap! state
           (fn [current]
             (update current
                     :lease
                     (fn [coll]
                       (map #(if (and (u.bytes/equal? (:hw-address %)
                                                      hw-address)
                                      (u.bytes/equal? (:ip-address %)
                                                      ip-address))
                               (merge % values)
                               %)
                            coll))))))
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

(defn new-memory-database []
  (->MemoryDatabase (atom {:reservation []
                           :lease []})))
