(ns dhcp.components.handler
  (:require
   [com.stuartsierra.component :as component]
   [dhcp.components.database]
   [dhcp.handler :as h]
   [dhcp.handler.dhcp-discover]
   [dhcp.handler.dhcp-request]
   [dhcp.records.config])
  (:import
   (com.savarese.rocksaw.net
    RawSocket)
   (dhcp.components.database
    IDatabase)
   (dhcp.records.config
    Config)
   (dhcp.records.dhcp_packet
    DhcpPacket)))

(defn make-handler [^IDatabase db ^Config config]
  (fn [^RawSocket socket
       ^DhcpPacket message]
    (h/handler socket db config message)))

(defrecord Handler [handler config db]
  component/Lifecycle
  (start [this]
    (assoc this :handler (make-handler db config)))
  (stop [this]
    (assoc this :handler nil)))
