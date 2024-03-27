(ns dhcp.handler.dhcp-request
  (:require
   [clojure.tools.logging :as log]
   [dhcp.components.database :as c.database]
   [dhcp.const.dhcp-type :refer [DHCPREQUEST DHCPACK DHCPNAK]]
   [dhcp.core.lease :as core.lease]
   [dhcp.core.packet :as core.packet]
   [dhcp.handler :as h]
   [dhcp.records.config :as r.config]
   [dhcp.records.dhcp-message :as r.dhcp-message]
   [dhcp.records.ip-address :as r.ip-address]
   [dhcp.util.bytes :as u.bytes])
  (:import
   (com.savarese.rocksaw.net
    RawSocket)
   (dhcp.components.database
    IDatabase)
   (dhcp.records.config
    Config)
   (dhcp.records.dhcp_packet
    DhcpPacket)
   (java.time
    Instant)))

(defn- now [] (Instant/now))

(defn- request-in-selecting
  [^RawSocket socket
   ^IDatabase db
   subnet
   ^DhcpPacket packet
   s-id]
  (let [message (:message packet)
        requested (some-> (r.dhcp-message/get-option message 50)
                          byte-array
                          u.bytes/bytes->number)
        l-addr (.getAddress (:local-ip-address packet))]
    (if (or (not (u.bytes/equal? (byte-array s-id) l-addr))
            (nil? requested)
            (not (<= (r.ip-address/->int (:start-address subnet))
                     requested
                     (r.ip-address/->int (:end-address subnet)))))
      (log/debug "DHCPREQUEST received, but not for this server")
      (let [leases (->> (c.database/find-leases-by-hw-address db (:chaddr message))
                        (filter #(and (= requested (u.bytes/bytes->number (:ip-address %)))
                                      (.isBefore (Instant/now) (:expired-at %)))))]
        (if (empty? leases)
          (let [_ (log/debug "DHCPREQUEST received, but no lease found")
                reply (r.dhcp-message/map->DhcpMessage
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
                                   :length 4, :value (vec l-addr)}]})]
            (core.packet/send-packet socket message reply))
          (let [req-ip-bytes (byte-array (u.bytes/number->byte-coll requested 4))
                pool (core.lease/select-pool-by-ip-address subnet req-ip-bytes)
                _ (c.database/update-lease db
                                           (:chaddr message)
                                           req-ip-bytes
                                           {:status "lease"
                                            :leased-at (now)
                                            :expired-at (.plusSeconds (now) (:lease-time pool))})
                _ (log/debugf "DHCPREQUEST lease updated %s" (u.bytes/->str (byte-array (:chaddr message))))
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
                                [{:code 255, :type :end, :length 0, :value []}])
                reply (r.dhcp-message/map->DhcpMessage
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
                        :options options})]
            (core.packet/send-packet socket message reply)))))))

(defmethod h/handler DHCPREQUEST
  [^RawSocket socket
   ^IDatabase db
   ^Config config
   ^DhcpPacket packet]
  (log/debugf "DHCPDISCOVER %s" (:message packet))
  (if-let [subnet (r.config/select-subnet config (:local-ip-address packet))]
    (let [message (:message packet)
          s-id (r.dhcp-message/get-option message 54)
          #_#_requested (r.dhcp-message/get-option message 50)
          #_#_l-addr (vec (.getAddress (:local-address message)))]
      (cond
        s-id
        (request-in-selecting socket db subnet packet s-id)))
    (log/infof "no subnet found for %s" (:local-ip-address packet))))
