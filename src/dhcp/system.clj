(ns dhcp.system
  (:require
   [aero.core :as aero]
   [clojure.java.io :as io]
   [com.stuartsierra.component :as component]
   [dhcp.components.config :as c.config]
   [dhcp.components.database :as c.database]
   [dhcp.components.handler :as c.handler]
   [dhcp.components.udp-server :as c.udp-server]
   [unilog.config :as unilog]))

(defn- new-system [_config server-config]
  (component/system-map
   :db (c.database/create-database (get-in server-config [:database :type]))
   :handler (component/using
             (c.handler/map->Handler {:config server-config})
             [:db])
   :udp-server (component/using (c.udp-server/map->UdpServer {:config server-config})
                                [:handler])))

(defn start []
  (let [config (aero/read-config (io/resource "config.edn") {:profile :prod})
        _ (unilog/start-logging! (:logging config))
        server-config (c.config/load-config (.getPath (io/resource "sample-config.yml")))]
    (when server-config
      (component/start (new-system config (:config server-config))))))

(defn stop [system]
  (component/stop system))
