(ns dhcp.core
  (:gen-class)
  (:require
   [clojure.tools.cli :as cli]
   [dhcp.system :as system]))

(def cli-options
  [["-c" "--config PATH" "path to the configuration file"
    :default "config.yml"]
   ["-h" "--help" "show this help message"
    :default false]
   [nil "--listen-only" "listen packets and process but do not send responses"
    :default false]
   [nil "--debug" nil
    :default false]])

(defn -main
  [& args]
  (let [{:keys [:options :errors :summary]} (cli/parse-opts args cli-options)]
    (cond
      (:help options)
      (do (println "\nUsage:")
          (println summary))

      (seq errors)
      (do (println errors)
          (println "\nUsage:")
          (println summary)
          1)

      :else
      (system/start options))))
