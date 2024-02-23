(ns dhcp.components.udp-server
  (:require
   [clojure.core.async :as as]
   [clojure.pprint]
   [clojure.tools.logging :as log]
   [com.stuartsierra.component :as component]
   [dhcp.records.dhcp-message :as r.dhcp-message])
  (:import
   (clojure.lang
    ExceptionInfo)
   (java.net
    DatagramPacket
    DatagramSocket
    SocketException)))

(def UDP-SERVER-PORT 67)

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
              (r.dhcp-message/parse-message packet))
            (recur))
          (log/infof "udp-server is closed")))
      (assoc this :socket socket)))
  (stop [this]
    (log/info "UdpServer stop")
    (when socket
      (.close socket))
    (assoc this :socket nil)))
