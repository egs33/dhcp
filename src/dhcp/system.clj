(ns dhcp.system
  (:require
   [aero.core :as aero]
   [clojure.java.io :as io]
   [com.stuartsierra.component :as component]
   [dhcp.components.udp-server :as c.udp-server]
   [unilog.config :as unilog]))

(defn- new-system [_config]
  (component/system-map
   :udp-server (c.udp-server/->UdpServer nil)))

(defn start []
  (let [config (aero/read-config (io/resource "config.edn") {:profile :prod})
        _ (unilog/start-logging! (:logging config))
        system (component/start (new-system config))]
    system))

(defn stop [system]
  (component/stop system))
