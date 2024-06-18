(ns dhcp.handler.dhcp-decline
  (:require
   [clojure.tools.logging :as log]
   [dhcp.components.socket]
   [dhcp.const.dhcp-type :refer [DHCPDECLINE]]
   [dhcp.handler :as h])
  (:import
   (dhcp.components.database
    IDatabase)
   (dhcp.components.socket
    ISocket)
   (dhcp.records.config
    Config)
   (dhcp.records.dhcp_packet
    DhcpPacket)))

(defmethod h/handler DHCPDECLINE
  [^ISocket _socket
   ^IDatabase _db
   ^Config _config
   ^DhcpPacket packet]
  (log/debugf "DHCPDECLINE %s" (:message packet)))
