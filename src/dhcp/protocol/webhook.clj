(ns dhcp.protocol.webhook)

(defprotocol IWebhook
  (send-lease [this lease]))

(extend-protocol IWebhook
  nil
  (send-lease [_ _]))

(defrecord NopWebhook []
  IWebhook
  (send-lease [_ _]))