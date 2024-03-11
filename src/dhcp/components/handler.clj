(ns dhcp.components.handler
  (:require
   [com.stuartsierra.component :as component]
   [dhcp.components.database]
   [dhcp.handler :as h]
   [dhcp.handler.dhcp-discover]
   [dhcp.records.config])
  (:import
   (dhcp.components.database
    IDatabase)
   (dhcp.records.config
    Config)
   (dhcp.records.dhcp_message
    DhcpMessage)
   (java.net
    DatagramSocket)))

(defn make-handler [^IDatabase db ^Config config]
  (fn [^DatagramSocket socket
       ^DhcpMessage message]
    (h/handler socket db config message)))

(defrecord Handler [handler config db]
  component/Lifecycle
  (start [this]
    (assoc this :handler (make-handler db config)))
  (stop [this]
    (assoc this :handler nil)))
