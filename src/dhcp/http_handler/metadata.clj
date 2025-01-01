(ns dhcp.http-handler.metadata
  (:require
   [dhcp.http-handler :as h]
   [dhcp.version :refer [version]]))

(defmethod h/handler :get-server-version
  [_]
  {:status 200
   :body {:version version}})
