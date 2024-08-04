(ns dhcp.system
  (:require
   [aero.core :as aero]
   [clojure.java.io :as io]
   [clojure.tools.logging :as log]
   [com.stuartsierra.component :as component]
   [dhcp.components.database.memory :as db.mem]
   [dhcp.components.database.postgres :as db.pg]
   [dhcp.components.handler :as c.handler]
   [dhcp.components.udp-server :as c.udp-server]
   [dhcp.protocol.database :as p.db]
   [dhcp.records.config :as r.config]
   [unilog.config :as unilog]))

(defn- new-system [{:keys [:dry-run]} {:as server-config :keys [:config]}]
  (component/system-map
   :db (case (get-in config [:database :type])
         "memory" (db.mem/new-memory-database)
         "postgresql" (db.pg/new-postgres-database (get-in config [:database :postgresql-option]))
         (throw (IllegalArgumentException. (str "Unsupported database type: " type))))
   :handler (component/using
             (c.handler/map->Handler {:config server-config})
             [:db])
   :udp-server (component/using (c.udp-server/map->UdpServer {:config config
                                                              :dry-run? dry-run})
                                [:handler])))

(defn start [options]
  (let [config (-> (aero/read-config (io/resource "config.edn") {:profile :prod})
                   (assoc :dry-run (:dry-run options)))
        _ (unilog/start-logging! (cond-> (:logging config)
                                   (:debug options) (assoc :level :debug)))
        server-config (r.config/load-config (:config options))]
    (when (:dry-run config)
      (log/info "dry-run mode enabled"))
    (when server-config
      (let [system (component/start (new-system config server-config))]
        (p.db/add-reservations (:db system) (r.config/reservations server-config))
        system))))

(defn stop [system]
  (component/stop system))
