(ns dhcp.handler
  (:require
   [clojure.tools.logging :as log]
   [dhcp.components.socket]
   [dhcp.protocol.database :as p.db]
   [dhcp.records.config]
   [dhcp.records.dhcp-message :as r.dhcp-message]
   [dhcp.records.dhcp-packet])
  (:import
   (dhcp.components.socket
    ISocket)
   (dhcp.protocol.database
    IDatabase)
   (dhcp.records.config
    Config)
   (dhcp.records.dhcp_packet
    DhcpPacket)))

(defmulti handler
  (fn [^ISocket _
       ^IDatabase _
       ^Config _
       ^DhcpPacket {:keys [:message]}]
    (r.dhcp-message/get-type message)))

(defmethod handler :default
  [^ISocket _
   ^IDatabase _
   ^Config _
   ^DhcpPacket {:keys [:message]}]
  (log/warnf "undefined message type:%s"
             (r.dhcp-message/get-type message)))
