(ns dhcp.system
  (:require
   [aero.core :as aero]
   [clojure.java.io :as io]
   [com.stuartsierra.component :as component]
   [dhcp.components.config :as c.config]
   [dhcp.components.handler :as c.handler]
   [dhcp.components.udp-server :as c.udp-server]
   [unilog.config :as unilog]))

(defn- new-system [config-path]
  (component/system-map
   :config (c.config/load-config config-path)
   :handler (component/using (c.handler/map->Handler {})
                             [:config])
   :udp-server (component/using (c.udp-server/map->UdpServer {})
                                [:handler :config])))

(defn start []
  (let [config (aero/read-config (io/resource "config.edn") {:profile :prod})
        _ (unilog/start-logging! (:logging config))
        system (component/start (new-system (.getPath (io/resource "sample-config.yml"))))]
    system))

(defn stop [system]
  (component/stop system))
