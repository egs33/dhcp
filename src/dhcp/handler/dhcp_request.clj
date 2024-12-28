(ns dhcp.handler.dhcp-request
  (:require
   [clojure.tools.logging :as log]
   [dhcp.components.socket]
   [dhcp.const.dhcp-type :refer [DHCPREQUEST DHCPACK DHCPNAK]]
   [dhcp.core.lease :as core.lease]
   [dhcp.handler :as h]
   [dhcp.protocol.database :as p.db]
   [dhcp.records.config :as r.config]
   [dhcp.records.dhcp-message :as r.dhcp-message]
   [dhcp.records.ip-address :as r.ip-address]
   [dhcp.util.bytes :as u.bytes])
  (:import
   (dhcp.protocol.database
    IDatabase)
   (dhcp.records.dhcp_packet
    DhcpPacket)
   (java.time
    Instant)
   (java.time.temporal
    ChronoUnit)))

(defn- now [] (Instant/now))

(defn- create-dhcp-nak [message ^bytes l-addr]
  (r.dhcp-message/map->DhcpMessage
   {:op :BOOTREPLY
    :htype (:htype message)
    :hlen (:hlen message)
    :hops (byte 0)
    :xid (:xid message)
    :secs 0
    :flags (:flags message)
    :ciaddr (r.ip-address/->IpAddress 0)
    :yiaddr (r.ip-address/->IpAddress 0)
    :siaddr (r.ip-address/->IpAddress 0)
    :giaddr (:giaddr message)
    :chaddr (:chaddr message)
    :sname ""
    :file ""
    :options [{:code 53, :type :dhcp-message-type, :length 1, :value [DHCPNAK]}
              {:code 54, :type :dhcp-server-id
               :length 4, :value (vec l-addr)}]}))

(defn- request-in-selecting
  [^IDatabase db
   subnet
   ^DhcpPacket packet
   s-id
   requested-addr]
  (let [message (:message packet)
        requested (some-> requested-addr
                          byte-array
                          u.bytes/bytes->number)
        l-addr (.getAddress (:local-ip-address packet))]
    (if (or (not (u.bytes/equal? (byte-array s-id) l-addr))
            (nil? requested)
            (not (<= (r.ip-address/->int (:start-address subnet))
                     requested
                     (r.ip-address/->int (:end-address subnet)))))
      (log/debug "DHCPREQUEST received, but not for this server")
      (let [leases (->> (p.db/find-leases-by-hw-address db (byte-array (:chaddr message)))
                        (filter #(and (= requested (u.bytes/bytes->number (:ip-address %)))
                                      (.isBefore (Instant/now) (:expired-at %)))))]
        (if (empty? leases)
          (let [_ (log/debug "DHCPREQUEST received, but no lease found")]
            (create-dhcp-nak message l-addr))
          (let [req-ip-bytes (byte-array (u.bytes/number->byte-coll requested 4))
                pool (core.lease/select-pool-by-ip-address subnet req-ip-bytes)
                _ (p.db/update-lease db
                                     (byte-array (:chaddr message))
                                     req-ip-bytes
                                     {:status "lease"
                                      :leased-at (now)
                                      :expired-at (.plusSeconds (now) (:lease-time pool))})
                _ (log/debugf "DHCPREQUEST lease updated %s" (str (:chaddr message)))
                options-by-code (reduce #(assoc %1 (:code %2) %2) {} (:options pool))
                requested-params (->> (r.dhcp-message/get-option message 55)
                                      (map #(Byte/toUnsignedInt %))
                                      distinct
                                      (keep #(get options-by-code %)))
                options (concat [{:code 53, :type :dhcp-message-type, :length 1, :value [DHCPACK]}
                                 {:code 51, :type :address-time
                                  :length 4, :value (u.bytes/number->byte-coll (:lease-time pool) 4)}
                                 {:code 54, :type :dhcp-server-id
                                  :length 4, :value (->> (.getAddress (:local-ip-address packet))
                                                         (map #(Byte/toUnsignedInt %)))}]
                                requested-params
                                [{:code 255, :type :end, :length 0, :value []}])]
            (r.dhcp-message/map->DhcpMessage
             {:op :BOOTREPLY
              :htype (:htype message)
              :hlen (:hlen message)
              :hops (byte 0)
              :xid (:xid message)
              :secs 0
              :flags (:flags message)
              :ciaddr (r.ip-address/->IpAddress 0)
              :yiaddr (r.ip-address/->IpAddress requested)
              :siaddr (r.ip-address/->IpAddress 0)
              :giaddr (:giaddr message)
              :chaddr (:chaddr message)
              :sname ""
              :file ""
              :options options})))))))

(defn- request-in-init-reboot
  [^IDatabase db
   subnet
   ^DhcpPacket packet
   requested-addr]
  (let [requested-addr (some-> requested-addr
                               byte-array
                               u.bytes/bytes->number)
        message (:message packet)]
    (if (or (nil? subnet)
            (nil? requested-addr)
            (not (<= (r.ip-address/->int (:start-address subnet))
                     requested-addr
                     (r.ip-address/->int (:end-address subnet)))))
      (let [l-addr (.getAddress (:local-ip-address packet))
            _ (log/debug "DHCPREQUEST received, but invalid subnet or requested address")]
        (create-dhcp-nak message l-addr))
      (let [leases (->> (p.db/find-leases-by-hw-address db (byte-array (:chaddr message)))
                        (filter #(and (.isBefore (Instant/now) (:expired-at %))
                                      (= (:status %) "lease"))))]
        (if (empty? leases)
          (log/infof "no lease found for %s" (str (:chaddr message)))
          (if (->> leases
                   (filter #(= requested-addr (u.bytes/bytes->number (:ip-address %))))
                   seq)
            (let [req-ip-bytes (byte-array (u.bytes/number->byte-coll requested-addr 4))
                  pool (core.lease/select-pool-by-ip-address subnet req-ip-bytes)
                  options-by-code (reduce #(assoc %1 (:code %2) %2) {} (:options pool))
                  requested-params (->> (r.dhcp-message/get-option message 55)
                                        (map #(Byte/toUnsignedInt %))
                                        distinct
                                        (keep #(get options-by-code %)))
                  options (concat [{:code 53, :type :dhcp-message-type, :length 1, :value [DHCPACK]}
                                   {:code 51, :type :address-time
                                    :length 4, :value (u.bytes/number->byte-coll (:lease-time pool) 4)}
                                   {:code 54, :type :dhcp-server-id
                                    :length 4, :value (->> (.getAddress (:local-ip-address packet))
                                                           (map #(Byte/toUnsignedInt %)))}]
                                  requested-params
                                  [{:code 255, :type :end, :length 0, :value []}])]
              (r.dhcp-message/map->DhcpMessage
               {:op :BOOTREPLY
                :htype (:htype message)
                :hlen (:hlen message)
                :hops (byte 0)
                :xid (:xid message)
                :secs 0
                :flags (:flags message)
                :ciaddr (r.ip-address/->IpAddress 0)
                :yiaddr (r.ip-address/->IpAddress requested-addr)
                :siaddr (r.ip-address/->IpAddress 0)
                :giaddr (:giaddr message)
                :chaddr (:chaddr message)
                :sname ""
                :file ""
                :options options}))
            (let [l-addr (.getAddress (:local-ip-address packet))
                  _ (log/debug "DHCPREQUEST received, but lease record mismatch")]
              (create-dhcp-nak message l-addr))))))))

(defn- request-in-renewing
  [^IDatabase db
   subnet
   ^DhcpPacket packet]
  (let [{:keys [:ciaddr :chaddr] :as message} (:message packet)
        lease-time-opt (some-> (r.dhcp-message/get-option message 51)
                               byte-array
                               u.bytes/bytes->number)
        leases (->> (p.db/find-leases-by-hw-address db (byte-array chaddr))
                    (filter #(and (.isBefore (Instant/now) (:expired-at %))
                                  (= (:status %) "lease"))))]
    (if (empty? leases)
      (let [_ (log/info "DHCPREQUEST received, but no lease found")
            l-addr (.getAddress (:local-ip-address packet))]
        (create-dhcp-nak message l-addr))
      (let [pool (core.lease/select-pool-by-ip-address subnet (r.ip-address/->byte-array ciaddr))
            lease-time (if lease-time-opt
                         (min lease-time-opt (:lease-time pool))
                         (:lease-time pool))
            lease-time (-> (first leases)
                           ^Instant (:expired-at)
                           (.until (now) ChronoUnit/SECONDS)
                           (+ lease-time))
            lease-time (max lease-time 600)
            _ (p.db/update-lease db
                                 (byte-array chaddr)
                                 (r.ip-address/->byte-array ciaddr)
                                 {:expired-at (.plusSeconds (now) lease-time)})
            _ (log/debugf "DHCPREQUEST lease updated %s" (str chaddr))
            options-by-code (reduce #(assoc %1 (:code %2) %2) {} (:options pool))
            requested-params (->> (r.dhcp-message/get-option message 55)
                                  (map #(Byte/toUnsignedInt %))
                                  distinct
                                  (keep #(get options-by-code %)))
            options (concat [{:code 53, :type :dhcp-message-type, :length 1, :value [DHCPACK]}
                             {:code 51, :type :address-time
                              :length 4, :value (u.bytes/number->byte-coll (:lease-time pool) 4)}
                             {:code 54, :type :dhcp-server-id
                              :length 4, :value (->> (.getAddress (:local-ip-address packet))
                                                     (map #(Byte/toUnsignedInt %)))}]
                            requested-params
                            [{:code 255, :type :end, :length 0, :value []}])]
        (r.dhcp-message/map->DhcpMessage
         {:op :BOOTREPLY
          :htype (:htype message)
          :hlen (:hlen message)
          :hops (byte 0)
          :xid (:xid message)
          :secs 0
          :flags (:flags message)
          :ciaddr (r.ip-address/->IpAddress 0)
          :yiaddr ciaddr
          :siaddr (r.ip-address/->IpAddress 0)
          :giaddr (:giaddr message)
          :chaddr (:chaddr message)
          :sname ""
          :file ""
          :options options})))))

(defn- request-in-rebinding
  [^IDatabase db
   subnet
   ^DhcpPacket packet]
  (request-in-renewing db subnet packet))

(defmethod h/handler DHCPREQUEST
  [{:keys [:db :config]}
   ^DhcpPacket packet]
  (log/debugf "DHCPREQUEST %s" (:message packet))
  (let [subnet (r.config/select-subnet config (:local-ip-address packet))
        message (:message packet)
        s-id (r.dhcp-message/get-option message 54)
        requested (r.dhcp-message/get-option message 50)]
    (cond
      s-id
      (if subnet
        (do (log/debug "DHCPREQUEST (selecting)")
            (request-in-selecting db subnet packet s-id requested))
        (log/infof "no subnet found for %s" (:local-ip-address packet)))

      requested
      (do (log/debug "DHCPREQUEST (init reboot)")
          (request-in-init-reboot db subnet packet requested))

      (zero? (r.ip-address/->int (:ciaddr message)))
      (log/info "invalid DHCPREQUEST")

      (:is-broadcast packet)
      (do (log/debug "DHCPREQUEST (rebinding)")
          (request-in-rebinding db subnet packet))

      :else
      (do (log/debug "DHCPREQUEST (renewing)")
          (request-in-renewing db subnet packet)))))
