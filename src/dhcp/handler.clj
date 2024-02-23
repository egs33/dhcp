(ns dhcp.handler
  (:require
   [clojure.tools.logging :as log]
   [dhcp.records.dhcp-message :as r.dhcp-message])
  (:import
   (dhcp.records.dhcp_message
    DhcpMessage)))

(defmulti handler
  (fn [^DhcpMessage message]
    (r.dhcp-message/getType message)))

(defmethod handler :default
  [^DhcpMessage message]
  (log/warnf "undefined message type:%s"
             (r.dhcp-message/getType message)))
