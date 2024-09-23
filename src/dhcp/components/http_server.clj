(ns dhcp.components.http-server
  (:require
   [com.stuartsierra.component :as component]
   [ring.adapter.jetty9 :as jetty9])
  (:import
   (org.eclipse.jetty.server
    Server)))

(defrecord HttpServer [http-handler enabled option ^Server server]
  component/Lifecycle
  (start [this]
    (if (and server (not enabled))
      this
      (assoc this :server (jetty9/run-jetty (:handler http-handler) option))))
  (stop [this]
    (when server
      (jetty9/stop-server server))
    (assoc this :server nil)))
