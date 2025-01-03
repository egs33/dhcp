(ns dhcp.core.packet
  (:require
   [dhcp.components.socket :as c.socket]
   [dhcp.const.dhcp-type :refer [DHCPNAK DHCPOFFER DHCPACK]]
   [dhcp.const.network :refer [UDP-SERVER-PORT]]
   [dhcp.records.dhcp-message :as r.dhcp-message]
   [dhcp.records.dhcp-packet]
   [dhcp.records.ip-address :as r.ip-address]
   [dhcp.util.bytes :as u.bytes])
  (:import
   (dhcp.components.socket
    ISocket)
   (dhcp.records.dhcp_message
    DhcpMessage)
   (dhcp.records.dhcp_packet
    DhcpPacket)
   (java.net
    Inet4Address
    InetAddress)))

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
  [^bytes source-hw
   ^bytes dest-hw
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

(defn- get-packet-info [^DhcpPacket request
                        ^DhcpMessage reply]
  (let [request-message (:message request)
        message-type (first (r.dhcp-message/get-option reply 53))]
    (cond
      (not= (r.ip-address/->int (:giaddr request-message)) 0)
      {:dest-ip-addr (InetAddress/getByAddress (r.ip-address/->byte-array (:giaddr request-message)))
       :dest-mac-addr (:remote-hw-address request)
       :dest-port 67}

      (= message-type DHCPNAK)
      {:dest-ip-addr (InetAddress/getByAddress (byte-array [255 255 255 255]))
       :dest-mac-addr (byte-array [255 255 255 255 255 255])
       :dest-port 68}

      (and (not= (r.ip-address/->int (:ciaddr request-message)) 0)
           (#{DHCPOFFER DHCPACK} message-type))
      {:dest-ip-addr (InetAddress/getByAddress (r.ip-address/->byte-array (:ciaddr request-message)))
       :dest-mac-addr (:remote-hw-address request)
       :dest-port 68}

      (and (not (zero? (bit-and 0x80 (:flags request-message))))
           (#{DHCPOFFER DHCPACK} message-type))
      {:dest-ip-addr (InetAddress/getByAddress (byte-array [255 255 255 255]))
       :dest-mac-addr (byte-array [255 255 255 255 255 255])
       :dest-port 68}

      :else
      {:dest-ip-addr (InetAddress/getByAddress (r.ip-address/->byte-array (:yiaddr reply)))
       :dest-mac-addr (:remote-hw-address request)
       :dest-port 68})))

(defn- create-datagram [^DhcpPacket request
                        ^DhcpMessage reply]
  (let [^bytes data (r.dhcp-message/->bytes reply)
        {:keys [:dest-ip-addr :dest-mac-addr :dest-port]} (get-packet-info request reply)]
    (->> (create-udp-datagram (:local-ip-address request)
                              dest-ip-addr
                              dest-port
                              data)
         (create-ip-packet (:local-ip-address request) dest-ip-addr)
         (create-ethernet-frame (:local-hw-address request) dest-mac-addr))))

(defn send-packet
  [^ISocket socket
   ^DhcpPacket request
   ^DhcpMessage reply]
  (let [eth-frame (create-datagram request reply)]
    (c.socket/send socket (byte-array eth-frame))))
