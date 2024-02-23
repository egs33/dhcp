(ns dhcp.components.handler
  (:require
   [com.stuartsierra.component :as component]
   [dhcp.handler :as h])
  (:import
   (dhcp.records.dhcp_message
    DhcpMessage)))

(defn make-handler [_]
  (fn [^DhcpMessage message]
    (h/handler message)))

(defrecord Handler [handler]
  component/Lifecycle
  (start [this]
    (assoc this :handler (make-handler this)))
  (stop [this]
    (assoc this :handler nil)))
