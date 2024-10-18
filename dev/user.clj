(ns user
  (:require
   [dhcp.system :as system]))

(defonce system (atom nil))

(defn go []
  (when @system
    (system/stop @system))
  (reset! system (system/start {:config "config.yml"
                                :debug true})))
