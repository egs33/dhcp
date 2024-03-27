(ns dhcp.core.packet
  (:require
   [dhcp.const.dhcp-type :refer [DHCPNAK DHCPOFFER DHCPACK]]
   [dhcp.records.dhcp-message :as r.dhcp-message]
   [dhcp.records.ip-address :as r.ip-address])
  (:import
   (com.savarese.rocksaw.net
    RawSocket)
   (dhcp.records.dhcp_message
    DhcpMessage)
   (java.net
    DatagramPacket
    InetAddress
    InetSocketAddress)))

(defn create-datagram ^DatagramPacket [^DhcpMessage request
                                       ^DhcpMessage reply]
  (let [^bytes data (r.dhcp-message/->bytes reply)
        message-type (first (r.dhcp-message/get-option reply 53))
        address (cond
                  (not= (r.ip-address/->int (:giaddr request)) 0)
                  (InetSocketAddress. (InetAddress/getByAddress (r.ip-address/->bytes (:giaddr request)))
                                      67)

                  (= message-type DHCPNAK)
                  (InetSocketAddress. (InetAddress/getByAddress (byte-array [255 255 255 255]))
                                      68)

                  (and (not= (r.ip-address/->int (:ciaddr request)) 0)
                       (#{DHCPOFFER DHCPACK} message-type))
                  (InetSocketAddress. (InetAddress/getByAddress (r.ip-address/->bytes (:ciaddr request)))
                                      68)

                  (and (not (zero? (bit-and 0x80 (:flags request))))
                       (#{DHCPOFFER DHCPACK} message-type))
                  (InetSocketAddress. (InetAddress/getByAddress (byte-array [255 255 255 255]))
                                      68)

                  :else
                  ;; TODO
                  ;; If the broadcast bit is not set and 'giaddr' is zero and
                  ;;   'ciaddr' is zero, then the server unicasts DHCPOFFER and DHCPACK
                  ;;   messages to the client's hardware address and 'yiaddr' address.
                  (throw (ex-info "not implemented" {})))]
    (DatagramPacket. data
                     (int (count data))
                     ^InetSocketAddress address)))

(defn send-packet
  [^RawSocket _socket
   ^DhcpMessage _request
   ^DhcpMessage _reply]
  ;; TODO
  )
