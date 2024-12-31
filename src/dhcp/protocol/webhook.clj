(ns dhcp.protocol.webhook)

(defprotocol IWebhook
  (send-lease [this lease]))

(defrecord NopWebhook []
  IWebhook
  (send-lease [_ _]))
