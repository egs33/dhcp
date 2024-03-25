(ns dhcp.handler
  (:require
   [clojure.tools.logging :as log]
   [dhcp.records.config]
   [dhcp.records.dhcp-message :as r.dhcp-message]
   [dhcp.records.dhcp-packet])
  (:import
   (com.savarese.rocksaw.net
    RawSocket)
   (dhcp.components.database
    IDatabase)
   (dhcp.records.config
    Config)
   (dhcp.records.dhcp_packet
    DhcpPacket)))

(defmulti handler
  (fn [^RawSocket _
       ^IDatabase _
       ^Config _
       ^DhcpPacket {:keys [:message]}]
    (r.dhcp-message/getType message)))

(defmethod handler :default
  [^RawSocket _
   ^IDatabase _
   ^Config _
   ^DhcpPacket {:keys [:message]}]
  (log/warnf "undefined message type:%s"
             (r.dhcp-message/getType message)))
