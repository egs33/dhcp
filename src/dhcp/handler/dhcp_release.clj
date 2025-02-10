(ns dhcp.handler.dhcp-release
  (:require
   [clojure.tools.logging :as log]
   [dhcp.components.socket]
   [dhcp.const.dhcp-type :refer [DHCPRELEASE]]
   [dhcp.handler :as h]
   [dhcp.protocol.database :as p.db]
   [dhcp.protocol.webhook :as p.webhook])
  (:import
   (dhcp.records.dhcp_packet
    DhcpPacket)
   (java.time
    Instant)))

(defmethod h/handler DHCPRELEASE
  [{:keys [:db :webhook]}
   ^DhcpPacket packet]
  (log/debugf "DHCPRELEASE %s" (:message packet))
  (let [message (:message packet)]
    (when-let [lease (p.db/update-lease db
                                        (byte-array (:chaddr message))
                                        (:ciaddr message)
                                        {:expired-at (Instant/now)})]
      (p.webhook/send-release webhook lease))
    nil))
