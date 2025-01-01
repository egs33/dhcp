(ns dhcp.protocol.webhook
  (:require
   [dhcp.util.schema :as us]))

(defprotocol IWebhook
  (send-lease [this lease]))

(extend-protocol IWebhook
  nil
  (send-lease [_ _]))

(defrecord NopWebhook []
  IWebhook
  (send-lease [_ _]))

(def lease-event-schema
  [:map {:closed true}
   [:event [:enum "lease"]]
   [:id pos-int?]
   [:client-id string?]
   [:hw-address string?]
   [:ip-address string?]
   [:hostname string?]
   [:lease-time pos-int?]
   [:status [:enum "offer" "lease"]]
   [:offered-at us/instant-json-schema :time/instant]
   [:leased-at {:json-schema/oneOf [{:type "string"} {:type "null"}]} [:maybe :time/instant]]
   [:expired-at us/instant-json-schema :time/instant]])
