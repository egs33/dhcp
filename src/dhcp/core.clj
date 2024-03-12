(ns dhcp.core
  (:gen-class)
  (:require
   [clojure.tools.cli :as cli]
   [dhcp.components.udp-server :as c.udp-server]
   [dhcp.system :as system]))

(def cli-options
  [["-c" "--config PATH" "path to the configuration file"
    :default "config.yml"]
   [nil "--debug" nil
    :default false]])

(defn -main
  [& args]
  (let [{:keys [:options :errors :summary]} (cli/parse-opts args cli-options)]
    (if (seq errors)
      (do (println errors)
          (println "\nUsage:")
          (println summary)
          1)
      (when-let [{:keys [:udp-server]} (system/start options)]
        ;; block until the server is stopped
        (c.udp-server/blocks-until-close udp-server)))))
