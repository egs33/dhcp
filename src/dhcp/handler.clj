(ns dhcp.handler
  (:require
   [clojure.tools.logging :as log]
   [dhcp.records.dhcp-message :as r.dhcp-message])
  (:import
   (dhcp.components.config
    Config)
   (dhcp.records.dhcp_message
    DhcpMessage)
   (java.net
    DatagramSocket)))

(defmulti handler
  (fn [^DatagramSocket _
       ^Config _
       ^DhcpMessage message]
    (r.dhcp-message/getType message)))

(defmethod handler :default
  [^DatagramSocket _
   ^Config _
   ^DhcpMessage message]
  (log/warnf "undefined message type:%s"
             (r.dhcp-message/getType message)))
