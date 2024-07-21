(ns dhcp.handler.dhcp-decline
  (:require
   [clojure.tools.logging :as log]
   [dhcp.components.socket]
   [dhcp.const.dhcp-type :refer [DHCPDECLINE]]
   [dhcp.handler :as h]
   [dhcp.protocol.database :as p.db]
   [dhcp.records.dhcp-message :as r.dhcp-message])
  (:import
   (dhcp.components.socket
    ISocket)
   (dhcp.protocol.database
    IDatabase)
   (dhcp.records.config
    Config)
   (dhcp.records.dhcp_packet
    DhcpPacket)
   (java.time
    Instant)
   (java.time.temporal
    ChronoUnit)))

(defmethod h/handler DHCPDECLINE
  [^ISocket _socket
   ^IDatabase db
   ^Config _config
   ^DhcpPacket packet]
  (log/debugf "DHCPDECLINE %s" (:message packet))
  (let [message (:message packet)]
    (when-let [requested-addr (some->> (r.dhcp-message/get-option message 50)
                                       byte-array)]
      (log/warnf "The ip address %s is declined." requested-addr)
      (p.db/update-lease db
                         (:chaddr message)
                         requested-addr
                         {:status "declined"
                          ;; pseudo client hw address for not available ip address
                          :hw-address (byte-array [255 255 255 255 255 255])
                          :expired-at (.plus (Instant/now) ChronoUnit/HOURS 12)}))))
