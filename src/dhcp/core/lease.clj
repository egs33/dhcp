(ns dhcp.core.lease
  (:require
   [clojure.tools.logging :as log]
   [dhcp.protocol.database :as p.db]
   [dhcp.records.ip-address :as r.ip-address]
   [dhcp.util.bytes :as u.bytes])
  (:import
   (dhcp.protocol.database
    IDatabase)
   (java.time
    Instant)
   (java.time.temporal
    ChronoUnit)))

(defn select-pool-by-ip-address [subnet ^bytes ip-address]
  (->> (:pools subnet)
       (filter #(<= (r.ip-address/->int (:start-address %))
                    (u.bytes/bytes->number ip-address)
                    (r.ip-address/->int (:end-address %))))
       first))

(defn- choose-ip-address-from-reservation
  [db
   subnet
   ^bytes hw-address]
  (when-let [reservation (->> (p.db/find-reservation db hw-address
                                                     (r.ip-address/->byte-array (:start-address subnet))
                                                     (r.ip-address/->byte-array (:end-address subnet)))
                              first)]
    (when-let [pool (select-pool-by-ip-address subnet (:ip-address reservation))]
      (let [reservation-addr (:ip-address reservation)
            current-lease (first (p.db/find-leases-by-ip-address db reservation-addr))]
        (if (or (nil? current-lease)
                ;; TODO: consider client-identifier
                (u.bytes/equal? (:hw-address current-lease) hw-address))
          (let [lifetime (when current-lease
                           (.between ChronoUnit/SECONDS (Instant/now) (:expired-at current-lease)))]
            (if (and lifetime (pos? lifetime))
              {:pool pool
               :ip-address reservation-addr
               :status :leasing
               :lease-time lifetime}
              {:pool pool
               :ip-address reservation-addr
               :status :new
               :lease-time (:lease-time pool)}))
          (log/warnf "reservation address %s is already used by another host" reservation-addr))))))

(defn- choose-ip-address-from-existing-lease
  "RFC2131 4.3.1.
  The client's current address as recorded in the client's current
  binding, ELSE
  The client's previous address as recorded in the client's (now
  expired or released) binding, if that address is in the server's
  pool of available addresses and not already allocated"
  [db
   subnet
   ^bytes hw-address]
  (when-let [host-lease (->> (p.db/find-leases-by-hw-address db hw-address)
                             (filter #(<= (r.ip-address/->int (:start-address subnet))
                                          (u.bytes/bytes->number (:ip-address %))
                                          (r.ip-address/->int (:end-address subnet))))
                             first)]
    (cond
      (.isBefore (Instant/now) (:expired-at host-lease))
      {:pool (select-pool-by-ip-address subnet (:ip-address host-lease))
       :ip-address (:ip-address host-lease)
       :status :leasing
       :lease-time (.between ChronoUnit/SECONDS (Instant/now) (:expired-at host-lease))}

      ;; lease is expired and not used by other host
      (->> (p.db/find-leases-by-ip-address db (:ip-address host-lease))
           (some #(and (.isBefore (Instant/now) (:expired-at %))
                       (not (u.bytes/equal? hw-address (:hw-address %)))))
           not)
      (let [pool (select-pool-by-ip-address subnet (:ip-address host-lease))]
        {:pool pool
         :ip-address (:ip-address host-lease)
         :status :new
         :lease-time (:lease-time pool)}))))

(defn- choose-ip-address-by-requested-addr
  "RFC2131 4.3.1.
  The address requested in the 'Requested IP Address' option, if that
  address is valid and not already allocated"
  [^IDatabase db
   subnet
   ^bytes requested-addr]
  (when-let [pool (when requested-addr
                    (select-pool-by-ip-address subnet requested-addr))]
    (when (and (not (:only-reserved-lease pool))
               (->> (p.db/find-reservations-by-ip-address-range db requested-addr requested-addr)
                    empty?)
               (->> (p.db/find-leases-by-ip-address db requested-addr)
                    (filter #(.isBefore (Instant/now) (:expired-at %)))
                    empty?))
      {:pool pool
       :ip-address requested-addr
       :status :new
       :lease-time (:lease-time pool)})))

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
  (or (choose-ip-address-from-reservation db subnet hw-address)
      (choose-ip-address-from-existing-lease db subnet hw-address)
      (choose-ip-address-by-requested-addr db subnet requested-addr)
      (let [current-leases-ips (->> (p.db/find-leases-by-ip-address-range
                                     db (r.ip-address/->byte-array (:start-address subnet))
                                     (r.ip-address/->byte-array (:end-address subnet)))
                                    (map (comp u.bytes/bytes->number :ip-address))
                                    set)
            reservation-ips (->> (p.db/find-reservations-by-ip-address-range
                                  db
                                  (r.ip-address/->byte-array (:start-address subnet))
                                  (r.ip-address/->byte-array (:end-address subnet)))
                                 (map (comp u.bytes/bytes->number :ip-address))
                                 set)
            available-ip (->> (:pools subnet)
                              (remove :only-reserved-lease)
                              (mapcat (fn [{:keys [:start-address :end-address]}]
                                        (range (r.ip-address/->int start-address)
                                               (inc (r.ip-address/->int end-address)))))
                              (remove current-leases-ips)
                              (remove reservation-ips)
                              first)]
        (when available-ip
          (let [available-ip (-> (r.ip-address/->IpAddress available-ip)
                                 r.ip-address/->byte-array)
                pool (select-pool-by-ip-address subnet available-ip)]
            {:pool pool
             :ip-address available-ip
             :status :new
             :lease-time (:lease-time pool)})))))

(defn format-lease [lease]
  (-> lease
      (update :client-id u.bytes/->colon-str)
      (update :hw-address u.bytes/->colon-str)
      (update :ip-address (comp str r.ip-address/bytes->ip-address))))
