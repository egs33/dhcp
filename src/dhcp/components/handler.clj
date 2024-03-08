(ns dhcp.components.handler
  (:require
   [com.stuartsierra.component :as component]
   [dhcp.handler :as h])
  (:import
   (dhcp.components.database
    IDatabase)
   (dhcp.records.dhcp_message
    DhcpMessage)
   (java.net
    DatagramSocket)))

(defn make-handler [config ^IDatabase db]
  (fn [^DatagramSocket socket
       ^DhcpMessage message]
    (h/handler socket db config message)))

(defrecord Handler [handler config db]
  component/Lifecycle
  (start [this]
    (assoc this :handler (make-handler (:config config) db)))
  (stop [this]
    (assoc this :handler nil)))
