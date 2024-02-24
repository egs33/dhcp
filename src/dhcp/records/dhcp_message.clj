(ns dhcp.records.dhcp-message
  (:require
   [clojure.pprint]
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
  (getType [this]))

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
         first)))

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
             (Charset/forName "US-ASCII"))))

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
        sname (bytes->str (Arrays/copyOfRange data 44 108))
        file (bytes->str (Arrays/copyOfRange data 108 236))
        rest (Arrays/copyOfRange data 236 (.getLength datagram))
        options (option/parse-options rest)]
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
      :options options})))
