(ns dhcp.protocol.webhook
  (:require
   [dhcp.util.schema :as us]))

(defprotocol IWebhook
  (send-offer [this lease])
  (send-lease [this lease])
  (send-renew [this lease])
  (send-rebind [this lease])
  (send-release [this lease]))

(extend-protocol IWebhook
  nil
  (send-offer [_ _])
  (send-lease [_ _])
  (send-renew [_ _])
  (send-rebind [_ _])
  (send-release [_ _]))

(defrecord NopWebhook []
  IWebhook
  (send-offer [_ _])
  (send-lease [_ _])
  (send-renew [_ _])
  (send-rebind [_ _])
  (send-release [_ _]))

(def lease-event-schema
  [:map {:closed true}
   [:event [:enum "offer" "lease" "renew" "rebind" "release"]]
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
