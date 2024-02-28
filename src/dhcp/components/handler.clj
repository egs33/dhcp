(ns dhcp.components.handler
  (:require
   [com.stuartsierra.component :as component]
   [dhcp.handler :as h])
  (:import
   (dhcp.records.dhcp_message
    DhcpMessage)
   (java.net
    DatagramSocket)))

(defn make-handler [_config]
  (fn [^DatagramSocket socket
       ^DhcpMessage message]
    (h/handler socket message)))

(defrecord Handler [handler config]
  component/Lifecycle
  (start [this]
    (assoc this :handler (make-handler (:config config))))
  (stop [this]
    (assoc this :handler nil)))
