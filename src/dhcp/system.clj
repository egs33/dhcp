(ns dhcp.system
  (:require
   [aero.core :as aero]
   [clojure.java.io :as io]
   [com.stuartsierra.component :as component]
   [unilog.config :as unilog]))

(defn- new-system [_config]
  (component/system-map))

(defn start []
  (let [config (aero/read-config (io/resource "config.edn") {:profile :prod})
        system (component/start (new-system config))]
    (unilog/start-logging! (:logging config))
    system))

(defn stop [system]
  (component/stop system))
