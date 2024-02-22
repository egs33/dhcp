(ns dhcp.components.udp-server
  (:require
   [clojure.core.async :as as]
   [clojure.pprint]
   [clojure.tools.logging :as log]
   [com.stuartsierra.component :as component]
   [dhcp.core.option :as option]
   [dhcp.records.ip-address :as r.ip-address]
   [dhcp.util.bytes :as u.bytes])
  (:import
   (clojure.lang
    ExceptionInfo)
   (java.net
    DatagramPacket
    DatagramSocket
    SocketException)
   (java.util
    Arrays)))

(def UDP-SERVER-PORT 67)

(defn- parse-bytes [^DatagramPacket datagram]
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
        chaddr (Arrays/copyOfRange data 28 44)
        sname (Arrays/copyOfRange data 44 108)
        file (Arrays/copyOfRange data 108 236)
        rest (Arrays/copyOfRange data 236 (.getLength datagram))]
    {:op op
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
     :options (option/parse-options rest)}))

(defrecord UdpServer [^DatagramSocket socket]
  component/Lifecycle
  (start [this]
    (log/info "UdpServer start")
    (when socket
      (.close socket))
    (let [socket (DatagramSocket. (int UDP-SERVER-PORT))]
      (as/go-loop []
        (if-not (.isClosed socket)
          (let [packet (as/<! (as/thread
                                (try
                                  (let [buf (byte-array 1024)
                                        packet (DatagramPacket. buf (count buf))]
                                    (.receive socket packet)
                                    packet)
                                  (catch SocketException e
                                    (log/infof "socket exception %s" e))
                                  (catch ExceptionInfo e
                                    (log/error "udp-server exception-info %s %s"
                                               (ex-message e) (ex-data e)))
                                  (catch Exception e
                                    (log/error "udp-server exception %s"
                                               e)))))]
            (when packet
              (parse-bytes packet))
            (recur))
          (log/infof "udp-server is closed")))
      (assoc this :socket socket)))
  (stop [this]
    (log/info "UdpServer stop")
    (when socket
      (.close socket))
    (assoc this :socket nil)))
