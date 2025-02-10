(ns dhcp.components.webhook
  (:require
   [clojure.tools.logging :as log]
   [com.stuartsierra.component :as component]
   [dhcp.core.lease :as c.lease]
   [dhcp.protocol.webhook :as p.webhook]
   [dhcp.records.ip-address :as r.ip-address]
   [dhcp.util.bytes :as u.bytes]
   [jsonista.core :as j]
   [malli.core :as m])
  (:import
   (clojure.lang
    IFn)
   (java.net
    URI)
   (java.net.http
    HttpClient
    HttpRequest
    HttpRequest$BodyPublishers
    HttpResponse
    HttpResponse$BodyHandlers)
   (java.time
    Duration)))

(defn ^:private send-webhook
  [^HttpClient client
   ^URI uri
   ^String event
   ^String content]
  (let [r (-> (HttpRequest/newBuilder)
              (.timeout (Duration/ofSeconds 60))
              (.uri uri)
              (.method "POST" (HttpRequest$BodyPublishers/ofString content))
              (.header "Content-Type" "application/json; charset=utf-8")
              (.header "User-Agent" "dhcp-server webhook")
              (.build))]
    (-> (.sendAsync client r (HttpResponse$BodyHandlers/discarding))
        (.whenComplete (fn [^HttpResponse res ^Throwable ex]
                         (when res
                           (if (<= 200 (.statusCode res) 299)
                             (log/infof "webhook response event: %s status-code:%s" event (.statusCode res))
                             (log/warnf "webhook response event: %s status-code:%s" event (.statusCode res))))
                         (when ex
                           (log/errorf "webhook error event: %s err:%s" event ex)))))))

(defrecord Webhook [events url ^HttpClient client ^URI uri ^IFn target-event?]
  component/Lifecycle
  (start [this]
    (let [uri (URI/create url)
          _ (when (not (#{"https" "http"} (.getScheme uri)))
              (throw (IllegalArgumentException. "invalid url")))
          client (-> (HttpClient/newBuilder)
                     (.connectTimeout (Duration/ofSeconds 60))
                     (.build))
          target-event? (if (contains? (set events) "all")
                          any?
                          (set events))]
      (assoc this
             :client client
             :uri uri
             :target-event? target-event?)))
  (stop [this]
    (when client
      (.close client))
    (assoc this :client nil :uri nil))

  p.webhook/IWebhook
  (send-offer [_ offer]
    (when (target-event? "offer")
      (let [event-data (-> offer
                           (update :client-id #(when % (u.bytes/->colon-str %)))
                           (update :hw-address u.bytes/->colon-str)
                           (update :ip-address (comp str r.ip-address/bytes->ip-address))
                           (assoc :event "offer"))]
        (m/assert p.webhook/offer-event-schema event-data)
        (send-webhook client uri "offer" (j/write-value-as-string event-data)))))
  (send-lease [_ lease]
    (when (target-event? "lease")
      (let [event-data (-> (c.lease/format-lease lease)
                           (assoc :event "lease"))]
        (m/assert p.webhook/lease-event-schema event-data)
        (send-webhook client uri "lease" (j/write-value-as-string event-data)))))
  (send-renew [_ lease]
    (when (target-event? "renew")
      (let [event-data (-> (c.lease/format-lease lease)
                           (assoc :event "renew"))]
        (m/assert p.webhook/lease-event-schema event-data)
        (send-webhook client uri "renew" (j/write-value-as-string event-data)))))
  (send-rebind [_ lease]
    (when (target-event? "rebind")
      (let [event-data (-> (c.lease/format-lease lease)
                           (assoc :event "rebind"))]
        (m/assert p.webhook/lease-event-schema event-data)
        (send-webhook client uri "rebind" (j/write-value-as-string event-data)))))
  (send-release [_ lease]
    (when (target-event? "release")
      (let [event-data (-> (c.lease/format-lease lease)
                           (assoc :event "release"))]
        (m/assert p.webhook/lease-event-schema event-data)
        (send-webhook client uri "release" (j/write-value-as-string event-data))))))
