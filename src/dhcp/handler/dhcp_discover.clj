(ns dhcp.handler.dhcp-discover
  (:require
   [clojure.tools.logging :as log]
   [dhcp.components.socket]
   [dhcp.const.dhcp-type :refer [DHCPDISCOVER DHCPOFFER]]
   [dhcp.core.lease :as core.lease]
   [dhcp.core.packet :as core.packet]
   [dhcp.handler :as h]
   [dhcp.protocol.database :as p.db]
   [dhcp.records.config :as r.config]
   [dhcp.records.dhcp-message :as r.dhcp-message]
   [dhcp.records.ip-address :as r.ip-address]
   [dhcp.util.bytes :as u.bytes])
  (:import
   (dhcp.components.socket
    ISocket)
   (dhcp.protocol.database
    IDatabase)
   (dhcp.records.config
    Config)
   (dhcp.records.dhcp_packet
    DhcpPacket)
   (java.nio.charset
    Charset)
   (java.time
    Instant)))

(def ^Charset US-ASCII (Charset/forName "US-ASCII"))

(defn- bytes->str
  ^String [byte-coll]
  (String. (byte-array byte-coll)
           0
           (int (count byte-coll))
           US-ASCII))

(defmethod h/handler DHCPDISCOVER
  [^ISocket socket
   ^IDatabase db
   ^Config config
   ^DhcpPacket packet]
  (log/debugf "DHCPDISCOVER %s" (:message packet))
  (if-let [subnet (r.config/select-subnet config (:local-ip-address packet))]
    (let [message (:message packet)
          requested-addr (some->> (r.dhcp-message/get-option message 50)
                                  byte-array)
          requested-addr (when (= (count requested-addr) 4)
                           requested-addr)
          client-id (r.dhcp-message/get-option message 61)]
      (if-let [{:keys [:pool :ip-address :status :lease-time]} (core.lease/choose-ip-address
                                                                subnet db (byte-array (:chaddr message)) requested-addr)]
        (let [lease-time-opt (some-> (r.dhcp-message/get-option message 51)
                                     byte-array
                                     u.bytes/bytes->number)
              lease-time (cond-> lease-time
                           lease-time-opt (min lease-time-opt))
              now (Instant/now)
              _ (when (= status :new)
                  (p.db/delete-lease db
                                     (byte-array (:chaddr message))
                                     (r.ip-address/->byte-array (:start-address subnet))
                                     (r.ip-address/->byte-array (:end-address subnet)))
                  (p.db/add-lease db {:client-id (byte-array client-id)
                                      :hw-address (byte-array (:chaddr message))
                                      :ip-address ip-address
                                      :hostname (bytes->str (r.dhcp-message/get-option
                                                             message 12))
                                      :lease-time lease-time
                                      :status "offer"
                                      :offered-at now
                                      :leased-at nil
                                      :expired-at (.plusSeconds now lease-time)}))
              options-by-code (reduce #(assoc %1 (:code %2) %2) {} (:options pool))
              requested-params (->> (r.dhcp-message/get-option message 55)
                                    (map #(Byte/toUnsignedInt %))
                                    distinct
                                    (keep #(get options-by-code %)))
              options (concat [{:code 53, :type :dhcp-message-type, :length 1, :value [DHCPOFFER]}
                               {:code 51, :type :address-time
                                :length 4, :value (u.bytes/number->byte-coll lease-time 4)}
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
                      :yiaddr (r.ip-address/bytes->ip-address ip-address)
                      :siaddr (r.ip-address/->IpAddress 0)
                      :giaddr (:giaddr message)
                      :chaddr (:chaddr message)
                      :sname ""
                      :file ""
                      :options options})]
          (core.packet/send-packet socket packet reply))
        (log/infof "no address for leasing in %s to %s" (:local-ip-address packet) (str (:chaddr message)))))
    (log/infof "no subnet found for %s" (:local-ip-address packet))))
