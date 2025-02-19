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
   [dhcp.protocol.webhook]
   [dhcp.records.config])
  (:import
   (dhcp.protocol.database
    IDatabase)
   (dhcp.protocol.webhook
    IWebhook)
   (dhcp.records.config
    Config)
   (dhcp.records.dhcp_packet
    DhcpPacket)))

(defn make-handler [^IDatabase db
                    ^Config config
                    ^IWebhook webhook]
  (fn [^DhcpPacket message]
    (h/handler {:db db
                :config config
                :webhook webhook}
               message)))

(defrecord Handler [handler config db webhook]
  component/Lifecycle
  (start [this]
    (assoc this :handler (make-handler db config webhook)))
  (stop [this]
    (assoc this :handler nil)))
