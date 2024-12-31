(ns dhcp.components.webhook
  (:require
   [clojure.tools.logging :as log]
   [com.stuartsierra.component :as component]
   [dhcp.protocol.webhook :as p.webhook]
   [jsonista.core :as j])
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
          target-event? (if events
                          (set events)
                          any?)]
      (assoc this
             :client client
             :uri uri
             :target-event? target-event?)))
  (stop [this]
    (when client
      (.close client))
    (assoc this :client nil :uri nil))

  p.webhook/IWebhook
  (send-lease [_ lease]
    (when (target-event? "lease")
      (send-webhook client uri "lease" (j/write-value-as-string lease)))))
