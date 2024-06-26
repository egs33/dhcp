(ns dhcp.handler.dhcp-release
  (:require
   [clojure.tools.logging :as log]
   [dhcp.components.socket]
   [dhcp.const.dhcp-type :refer [DHCPRELEASE]]
   [dhcp.handler :as h]
   [dhcp.protocol.database])
  (:import
   (dhcp.components.socket
    ISocket)
   (dhcp.protocol.database
    IDatabase)
   (dhcp.records.config
    Config)
   (dhcp.records.dhcp_packet
    DhcpPacket)))

(defmethod h/handler DHCPRELEASE
  [^ISocket _socket
   ^IDatabase _db
   ^Config _config
   ^DhcpPacket packet]
  (log/debugf "DHCPRELEASE %s" (:message packet)))
