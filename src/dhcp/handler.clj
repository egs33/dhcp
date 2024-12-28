(ns dhcp.handler
  (:require
   [clojure.tools.logging :as log]
   [dhcp.components.socket]
   [dhcp.records.config]
   [dhcp.records.dhcp-message :as r.dhcp-message]
   [dhcp.records.dhcp-packet])
  (:import
   (dhcp.records.dhcp_packet
    DhcpPacket)))

(defmulti handler
  (fn [_states
       ^DhcpPacket {:keys [:message]}]
    (r.dhcp-message/get-type message)))

(defmethod handler :default
  [_
   ^DhcpPacket {:keys [:message]}]
  (log/warnf "undefined message type:%s"
             (r.dhcp-message/get-type message)))
