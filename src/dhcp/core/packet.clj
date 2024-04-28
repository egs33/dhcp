(ns dhcp.core.packet
  (:require
   [dhcp.components.udp-server :refer [UDP-SERVER-PORT]]
   [dhcp.const.dhcp-type :refer [DHCPNAK DHCPOFFER DHCPACK]]
   [dhcp.records.dhcp-message :as r.dhcp-message]
   [dhcp.records.ip-address :as r.ip-address]
   [dhcp.util.bytes :as u.bytes])
  (:import
   (com.savarese.rocksaw.net
    RawSocket)
   (dhcp.records.dhcp_message
    DhcpMessage)
   (java.net
    DatagramPacket
    Inet4Address
    InetAddress
    InetSocketAddress)))

(defn- sum-ones-complement-by-16bits
  [data]
  (let [to-pos (fn [num]
                 (if (instance? Byte num)
                   (Byte/toUnsignedInt num)
                   num))
        sum (->> (partition-all 2 data)
                 vec
                 (map (fn [[b1 b2]]
                        (->> (+ (* 0x100 (to-pos b1))
                                (if b2
                                  (to-pos b2)
                                  0))
                             (- 0xffff))))
                 (reduce + 0))
        overflow (int (/ sum 0x10000))]
    (mod (+ sum overflow) 0x10000)))

(defn- create-ethernet-frame
  [^bytes dest-hw
   ^bytes source-hw
   ^bytes payload]
  (concat dest-hw
          source-hw
          (u.bytes/number->byte-coll 0x0800 2)  ; type
          payload))

(defn- create-ip-packet
  [^Inet4Address source
   ^Inet4Address dest
   payload]
  (let [header (concat (u.bytes/number->byte-coll 0x45 1)                      ; version and IHL
                       (u.bytes/number->byte-coll 0 1)                         ; TOS
                       (u.bytes/number->byte-coll (+ (count payload) 20) 2)    ; total length
                       (u.bytes/number->byte-coll 0 2)                         ; identification
                       (u.bytes/number->byte-coll 0 2)                         ; flags and fragment offset
                       (u.bytes/number->byte-coll 0x3a 1)                      ; TTL
                       (u.bytes/number->byte-coll 17 1)                        ; protocol (UDP)
                       (u.bytes/number->byte-coll 0 2)                         ; checksum
                       (.getAddress source)                                    ; source IP
                       (.getAddress dest)                                      ; destination IP
                       )
        checksum (-> (sum-ones-complement-by-16bits header)
                     (u.bytes/number->byte-coll 2))]
    (-> (concat header payload)
        vec
        (assoc 10 (nth checksum 0)
               11 (nth checksum 1)))))

(defn- create-udp-datagram
  [^Inet4Address source
   ^Inet4Address dest
   dest-port
   ^bytes payload]
  (let [tentative-datagram (concat (u.bytes/number->byte-coll UDP-SERVER-PORT 2)
                                   (u.bytes/number->byte-coll dest-port 2)
                                   (u.bytes/number->byte-coll (+ (count payload) 8) 2)
                                   (u.bytes/number->byte-coll 0 2)                          ; checksum
                                   payload)
        pseudo-ip-header (concat (.getAddress source)
                                 (.getAddress dest)
                                 [(byte 0) (byte 17)]
                                 (u.bytes/number->byte-coll (+ (count payload) 8) 2))
        checksum (-> (sum-ones-complement-by-16bits (concat pseudo-ip-header tentative-datagram))
                     (u.bytes/number->byte-coll 2))]
    (-> (vec tentative-datagram)
        (assoc 6 (nth checksum 0)
               7 (nth checksum 1)))))

(defn create-datagram ^DatagramPacket [^DhcpMessage request
                                       ^DhcpMessage reply]
  (let [^bytes data (r.dhcp-message/->bytes reply)
        message-type (first (r.dhcp-message/get-option reply 53))
        _ (create-udp-datagram (InetAddress/getByAddress (r.ip-address/->bytes (:giaddr request)))
                               (InetAddress/getByAddress (r.ip-address/->bytes (:ciaddr request)))
                               68
                               data)
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
