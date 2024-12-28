(ns dhcp.handler.dhcp-inform
  (:require
   [clojure.tools.logging :as log]
   [dhcp.components.socket]
   [dhcp.const.dhcp-type :refer [DHCPINFORM DHCPACK]]
   [dhcp.handler :as h]
   [dhcp.records.config :as r.config]
   [dhcp.records.dhcp-message :as r.dhcp-message]
   [dhcp.records.ip-address :as r.ip-address])
  (:import
   (dhcp.components.socket
    ISocket)
   (dhcp.protocol.database
    IDatabase)
   (dhcp.records.config
    Config)
   (dhcp.records.dhcp_packet
    DhcpPacket)))

(defmethod h/handler DHCPINFORM
  [^ISocket _socket
   ^IDatabase _db
   ^Config config
   ^DhcpPacket packet]
  (let [{:keys [:message]} packet]
    (log/debugf "DHCPINFORM %s" message)
    (if-let [subnet (r.config/select-subnet config (:ciaddr message))]
      (let [options (or (some->> (:pools subnet)
                                 (filter #(<= (r.ip-address/->int (:start-address %))
                                              (r.ip-address/->int (:ciaddr message))
                                              (r.ip-address/->int (:end-address %))))
                                 first
                                 :options)
                        (:options subnet))
            options-by-code (reduce #(assoc %1 (:code %2) %2) {} options)
            requested-params (->> (r.dhcp-message/get-option message 55)
                                  (map #(Byte/toUnsignedInt %))
                                  distinct
                                  (keep #(get options-by-code %)))
            options (concat [{:code 53, :type :dhcp-message-type, :length 1, :value [DHCPACK]}
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
          :yiaddr (:ciaddr message)
          :siaddr (r.ip-address/->IpAddress 0)
          :giaddr (:giaddr message)
          :chaddr (:chaddr message)
          :sname ""
          :file ""
          :options options}))
      (log/infof "no subnet found (DHCPINFORM) for %s" (:ciaddr message)))))
