(ns dhcp.system
  (:require
   [aero.core :as aero]
   [clojure.java.io :as io]
   [clojure.tools.logging :as log]
   [com.stuartsierra.component :as component]
   [dhcp.components.database.memory :as db.mem]
   [dhcp.components.database.postgres :as db.pg]
   [dhcp.components.handler :as c.handler]
   [dhcp.components.http-handler :as c.http-handler]
   [dhcp.components.http-server :as c.http-server]
   [dhcp.components.udp-server :as c.udp-server]
   [dhcp.protocol.database :as p.db]
   [dhcp.records.config :as r.config]
   [unilog.config :as unilog]))

(defn- new-system [{:keys [:listen-only]} {:as server-config :keys [:config]}]
  (component/system-map
   :db (case (get-in config [:database :type])
         "memory" (db.mem/new-memory-database)
         "postgresql" (db.pg/new-postgres-database (get-in config [:database :postgresql-option]))
         (throw (IllegalArgumentException. (str "Unsupported database type: " type))))
   :handler (component/using
             (c.handler/map->Handler {:config server-config})
             [:db])
   :udp-server (component/using (c.udp-server/map->UdpServer {:config config
                                                              :listen-only? listen-only})
                                [:handler])
   :http-handler (component/using (c.http-handler/map->HttpHandler {})
                                  [:db])
   :http-server (component/using (c.http-server/map->HttpServer {:enabled (boolean (get-in config [:http-api :enabled]))
                                                                 :option {:port (get-in config [:http-api :port])
                                                                          :join? false
                                                                          :min-threads 1}})
                                 [:http-handler])))

(defn start [options]
  (let [config (-> (aero/read-config (io/resource "config.edn") {:profile :prod})
                   (assoc :listen-only (:listen-only options)))
        _ (unilog/start-logging! (cond-> (:logging config)
                                   (:debug options) (assoc :level :debug)))
        server-config (r.config/load-config (:config options))]
    (when (:listen-only config)
      (log/info "listen only mode enabled"))
    (when server-config
      (let [system (component/start (new-system config server-config))]
        (p.db/delete-reservations-by-source (:db system) "config")
        (p.db/add-reservations (:db system) (r.config/reservations server-config))
        system))))

(defn stop [system]
  (component/stop system))
