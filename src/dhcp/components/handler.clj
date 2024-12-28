(ns dhcp.components.handler
  (:require
   [com.stuartsierra.component :as component]
   [dhcp.components.socket]
   [dhcp.handler :as h]
   [dhcp.handler.dhcp-decline]
   [dhcp.handler.dhcp-discover]
   [dhcp.handler.dhcp-inform]
   [dhcp.handler.dhcp-release]
   [dhcp.handler.dhcp-request]
   [dhcp.protocol.database]
   [dhcp.records.config])
  (:import
   (dhcp.protocol.database
    IDatabase)
   (dhcp.records.config
    Config)
   (dhcp.records.dhcp_packet
    DhcpPacket)))

(defn make-handler [^IDatabase db ^Config config]
  (fn [^DhcpPacket message]
    (h/handler {:db db
                :config config}
               message)))

(defrecord Handler [handler config db]
  component/Lifecycle
  (start [this]
    (assoc this :handler (make-handler db config)))
  (stop [this]
    (assoc this :handler nil)))
