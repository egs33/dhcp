(ns dhcp.core
  (:gen-class)
  (:require
   [clojure.core.async :as as]
   [dhcp.system :as system]))

(defn -main
  [& _args]
  (let [{:keys [:udp-server]} (system/start)]
    ;; block until the server is stopped
    (as/<!! (as/go-loop []
              (let [{:keys [:socket]} udp-server]
                (when (and socket
                           (not (.isClosed socket)))
                  (as/<! (as/timeout 1000))
                  (recur)))))))
