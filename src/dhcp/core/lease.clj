(ns dhcp.core.lease
  (:require
   [dhcp.components.database :as c.database]
   [dhcp.records.ip-address :as r.ip-address]
   [dhcp.util.bytes :as u.bytes])
  (:import
   (dhcp.components.database
    IDatabase)
   (java.time
    Instant)
   (java.time.temporal
    ChronoUnit)))

(defn- select-reservation-and-pool [subnet reservations]
  (when-let [reservation (->> reservations
                              (filter #(<= (r.ip-address/->int (:start-address subnet))
                                           (u.bytes/bytes->number (:ip-address %))
                                           (r.ip-address/->int (:end-address subnet))))
                              first)]
    (when-let [pool (->> (:pools subnet)
                         (filter #(<= (r.ip-address/->int (:start-address %))
                                      (u.bytes/bytes->number (:ip-address reservation))
                                      (r.ip-address/->int (:end-address %))))
                         first)]
      {:reservation reservation
       :pool pool})))

(defn choose-ip-address
  "Choose an IP address for the client.
  If hw-address is registered in the reservation, prefer the ip address.
  It is not registered or reserved ip address is not available,
  choose a new ip address from the pool according to RFC2131 4.3.1."
  [subnet
   ^IDatabase db
   ^bytes hw-address
   ^bytes requested-addr]
  (let [rp (select-reservation-and-pool subnet (c.database/find-reservations-by-hw-address db hw-address))
        reservation-addr (get-in rp [:reservation :ip-address])
        current-lease (when reservation-addr
                        (first (c.database/find-leases-by-ip-address-range db reservation-addr reservation-addr)))]
    (if (and rp
             (or (nil? current-lease)
                 ;; TODO: consider client-identifier
                 (u.bytes/equal? (:hw-address current-lease) hw-address)))
      {:pool (:pool rp)
       :ip-address reservation-addr
       :lease-time (if current-lease
                     (.between ChronoUnit/SECONDS (Instant/now) (:expired-at current-lease))
                     (get-in rp [:pool :lease-time]))}
      (comment "TODO"))))
