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

(defn- select-pool-by-ip-address [subnet ^bytes ip-address]
  (->> (:pools subnet)
       (filter #(<= (r.ip-address/->int (:start-address %))
                    (u.bytes/bytes->number ip-address)
                    (r.ip-address/->int (:end-address %))))
       first))

(defn- select-reservation-and-pool [subnet reservations]
  (when-let [reservation (->> reservations
                              (filter #(<= (r.ip-address/->int (:start-address subnet))
                                           (u.bytes/bytes->number (:ip-address %))
                                           (r.ip-address/->int (:end-address subnet))))
                              first)]
    (when-let [pool (select-pool-by-ip-address subnet (:ip-address reservation))]
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
   ^bytes requested-addr                                    ; nilable
   ]
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
      (let [host-lease (->> (c.database/find-leases-by-hw-address db hw-address)
                            (filter #(<= (r.ip-address/->int (:start-address subnet))
                                         (u.bytes/bytes->number (:ip-address %))
                                         (r.ip-address/->int (:end-address subnet))))
                            first)]
        (cond
          (and host-lease
               (.isBefore (Instant/now) (:expired-at host-lease)))
          {:pool (select-pool-by-ip-address subnet (:ip-address host-lease))
           :ip-address (:ip-address host-lease)
           :lease-time (.between ChronoUnit/SECONDS (Instant/now) (:expired-at host-lease))}

          ;; lease is expired and not used by other host
          (and host-lease
               (->> (c.database/find-leases-by-ip-address-range
                     db (:ip-address host-lease) (:ip-address host-lease))
                    (some #(and (.isBefore (Instant/now) (:expired-at %))
                                (not (u.bytes/equal? hw-address (:hw-address %)))))
                    not))
          (let [pool (select-pool-by-ip-address subnet (:ip-address host-lease))]
            {:pool pool
             :ip-address (:ip-address host-lease)
             :lease-time (:lease-time pool)})

          :else
          (let [pool-for-requested-addr (when requested-addr
                                          (select-pool-by-ip-address subnet requested-addr))]
            (if (and pool-for-requested-addr
                     (not (:only-reserved-lease pool-for-requested-addr))
                     (->> (c.database/find-leases-by-ip-address-range
                           db requested-addr requested-addr)
                          (filter #(.isBefore (Instant/now) (:expired-at %)))
                          empty?))
              (let [pool (select-pool-by-ip-address subnet requested-addr)]
                {:pool pool
                 :ip-address requested-addr
                 :lease-time (:lease-time pool)})
              (let [current-leases-ips (->> (c.database/find-leases-by-ip-address-range
                                             db (r.ip-address/->byte-array (:start-address subnet))
                                             (r.ip-address/->byte-array (:end-address subnet)))
                                            (map (comp u.bytes/bytes->number :ip-address))
                                            set)
                    available-ip (->> (:pools subnet)
                                      (remove :only-reserved-lease)
                                      (mapcat (fn [{:keys [:start-address :end-address]}]
                                                (range (r.ip-address/->int start-address)
                                                       (inc (r.ip-address/->int end-address)))))
                                      (remove current-leases-ips)
                                      first)
                    available-ip (when available-ip
                                   (-> (r.ip-address/->IpAddress available-ip)
                                       r.ip-address/->byte-array))
                    pool (when available-ip
                           (select-pool-by-ip-address subnet available-ip))]
                (when available-ip
                  {:pool pool
                   :ip-address available-ip
                   :lease-time (:lease-time pool)})))))))))
