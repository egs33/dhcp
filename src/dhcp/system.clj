(ns dhcp.system
  (:require
   [aero.core :as aero]
   [clojure.java.io :as io]
   [com.stuartsierra.component :as component]
   [dhcp.components.database.memory :as db.mem]
   [dhcp.components.database.postgres :as db.pg]
   [dhcp.components.handler :as c.handler]
   [dhcp.components.udp-server :as c.udp-server]
   [dhcp.records.config :as r.config]
   [unilog.config :as unilog]))

(defn- new-system [_config server-config]
  (component/system-map
   :db (case (get-in server-config [:database :type])
         "memory" (db.mem/new-memory-database)
         "postgresql" (db.pg/new-postgres-database (get-in server-config [:database :postgresql-option]))
         (throw (IllegalArgumentException. (str "Unsupported database type: " type))))
   :handler (component/using
             (c.handler/map->Handler {:config server-config})
             [:db])
   :udp-server (component/using (c.udp-server/map->UdpServer {:config server-config})
                                [:handler])))

(defn start [options]
  (let [config (aero/read-config (io/resource "config.edn") {:profile :prod})
        _ (unilog/start-logging! (cond-> (:logging config)
                                   (:debug options) (assoc :level :debug)))
        server-config (r.config/load-config (:config options))]
    (when server-config
      (component/start (new-system config (:config server-config))))))

(defn stop [system]
  (component/stop system))
