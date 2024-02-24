(ns dhcp.core
  (:gen-class)
  (:require
   [dhcp.components.udp-server :as c.udp-server]
   [dhcp.system :as system]))

(defn -main
  [& _args]
  (let [{:keys [:udp-server]} (system/start)]
    ;; block until the server is stopped
    (c.udp-server/blocks-until-close udp-server)))
