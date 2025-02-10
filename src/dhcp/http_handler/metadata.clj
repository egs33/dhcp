(ns dhcp.http-handler.metadata
  (:require
   [dhcp.http-handler :as h]
   [dhcp.protocol.webhook :as p.webhook]
   [dhcp.version :refer [version]]
   [malli.json-schema :as json-schema]))

(defmethod h/handler :get-server-version
  [_]
  {:status 200
   :body {:version version}})

(defmethod h/handler :get-webhook-event-schema
  [_]
  {:status 200
   :body (malli.json-schema/transform p.webhook/webhook-event-schema)})
