(ns dhcp.records.dhcp-message
  (:require
   [clojure.pprint]
   [clojure.tools.logging :as log]
   [dhcp.core.option :as option]
   [dhcp.records.ip-address :as r.ip-address]
   [dhcp.util.bytes :as u.bytes])
  (:import
   (clojure.lang
    Keyword)
   (dhcp.records.ip_address
    IpAddress)
   (java.net
    DatagramPacket
    Inet4Address)
   (java.nio.charset
    Charset)
   (java.util
    Arrays)))

(defprotocol IDhcpMessage
  (getType [this])
  (->bytes [this]))

(def ^Charset US-ASCII (Charset/forName "US-ASCII"))

(defn- str->bytes
  "convert string to null terminated bytes.
  if len is nil, no padding and no trim"
  ([^String s] (str->bytes s nil))
  ([^String s
    len]
   (if len
     (let [value (->> (.getBytes s US-ASCII)
                      (take (dec len)))]
       (->> (concat value (repeat 0))
            (take len)))
     (concat (.getBytes s US-ASCII) [0]))))

(defrecord DhcpMessage [^Inet4Address local-address
                        ^Keyword op
                        ^byte htype
                        ^byte hlen
                        ^byte hops
                        ^long xid
                        ^long secs
                        ^long flags
                        ^IpAddress ciaddr
                        ^IpAddress yiaddr
                        ^IpAddress siaddr
                        ^IpAddress giaddr
                        chaddr
                        ^String sname
                        ^String file
                        options]
  IDhcpMessage
  (getType [_]
    (->> options
         (filter #(= (:type %) :dhcp-message-type))
         first
         :value
         first))
  (->bytes ^bytes [_]
    (byte-array (concat [(case op :BOOTREQUEST 1 :BOOTREPLY 2)
                         htype
                         hlen
                         hops]
                        (u.bytes/number->byte-coll xid 4)
                        (u.bytes/number->byte-coll secs 2)
                        (u.bytes/number->byte-coll flags 2)
                        (r.ip-address/->bytes ciaddr)
                        (r.ip-address/->bytes yiaddr)
                        (r.ip-address/->bytes siaddr)
                        (r.ip-address/->bytes giaddr)
                        chaddr
                        (str->bytes sname 64)
                        (str->bytes file 128)
                        (option/options->bytes options)))))

(defn- bytes->str
  "convert null terminated bytes to string"
  ^String [^bytes bytes]
  (let [null-idx (->> bytes
                      (keep-indexed (fn [idx value]
                                      (when (= value 0)
                                        idx)))
                      first)]
    (String. bytes
             0
             (int (if (= null-idx -1)
                    (count bytes)
                    null-idx))
             US-ASCII)))

(defn parse-message
  ^DhcpMessage [^Inet4Address local-address
                ^DatagramPacket datagram]
  (let [data (.getData datagram)
        op (case (first data)
             1 :BOOTREQUEST
             2 :BOOTREPLY
             nil)
        htype (nth data 1)
        hlen (nth data 2)
        hops (nth data 3)
        xid (u.bytes/bytes->number (Arrays/copyOfRange data 4 8))
        secs (u.bytes/bytes->number (Arrays/copyOfRange data 8 10))
        flags (u.bytes/bytes->number (Arrays/copyOfRange data 10 12))
        ciaddr (r.ip-address/bytes->ip-address (Arrays/copyOfRange data 12 16))
        yiaddr (r.ip-address/bytes->ip-address (Arrays/copyOfRange data 16 20))
        siaddr (r.ip-address/bytes->ip-address (Arrays/copyOfRange data 20 24))
        giaddr (r.ip-address/bytes->ip-address (Arrays/copyOfRange data 24 28))
        chaddr (vec (Arrays/copyOfRange data 28 44))
        rest (Arrays/copyOfRange data 236 (.getLength datagram))
        options (if (option/start-with-magic-cookie? rest)
                  (option/parse-options (drop 4 rest))
                  (log/warn "magic cookie not found" {:4octets (take 4 rest)
                                                      :len (count rest)}))
        overload-option-value (some-> (filter #(= (:code %) 52) options)
                                      first
                                      :value
                                      first
                                      long)
        sname-byte (Arrays/copyOfRange data 44 108)
        sname (if (#{2 3} overload-option-value)
                ""
                (bytes->str sname-byte))
        options-in-sname (when (#{1 3} overload-option-value)
                           (->> (option/parse-options sname-byte)
                                (take-while #(not= (:code %) 255))))
        file-bytes (Arrays/copyOfRange data 108 236)
        file (if (#{1 3} overload-option-value)
               ""
               (bytes->str file-bytes))
        options-in-file (when (#{1 3} overload-option-value)
                          (->> (option/parse-options file-bytes)
                               (take-while #(not= (:code %) 255))))]
    (map->DhcpMessage
     {:local-address local-address
      :op op
      :htype htype
      :hlen hlen
      :hops hops
      :xid xid
      :secs secs
      :flags flags
      :ciaddr ciaddr
      :yiaddr yiaddr
      :siaddr siaddr
      :giaddr giaddr
      :chaddr chaddr
      :sname sname
      :file file
      :options (concat options
                       options-in-sname
                       options-in-file)})))
