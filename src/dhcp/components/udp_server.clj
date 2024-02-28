(ns dhcp.components.udp-server
  (:require
   [clojure.core.async :as as]
   [clojure.pprint]
   [clojure.tools.logging :as log]
   [com.stuartsierra.component :as component]
   [dhcp.records.dhcp-message :as r.dhcp-message])
  (:import
   (clojure.lang
    ExceptionInfo
    IFn)
   (java.net
    DatagramPacket
    DatagramSocket
    Inet4Address
    NetworkInterface
    SocketException)))

(def UDP-SERVER-PORT 67)

(defn- list-local-ip-addresses
  "Return all ipv4 addresses on local machine"
  []
  (->> (NetworkInterface/getNetworkInterfaces)
       enumeration-seq
       (mapcat (fn [interface]
                 (when (.isUp interface)
                   (enumeration-seq (.getInetAddresses interface)))))
       (filter #(instance? Inet4Address %))))

(defprotocol ^:private IUdpServerSocket
  (open [this])
  (close [this]))

(defprotocol IBlockUntilClose
  (blocks-until-close [this]))

(defrecord ^:private UdpServerSocket [^Inet4Address ip-address
                                      socket-atom
                                      ^IFn handler]
  IUdpServerSocket
  (open [_]
    (when @socket-atom
      (.close @socket-atom))
    (let [socket (DatagramSocket. (int UDP-SERVER-PORT) ip-address)]
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
              (try
                (let [parsed (r.dhcp-message/parse-message ip-address packet)]
                  (handler @socket-atom parsed))
                (catch ExceptionInfo e
                  (log/error "parse-message exception-info %s %s"
                             (ex-message e) (ex-data e)))
                (catch Exception e
                  (log/error "parse-message exception %s"
                             e))))
            (recur))
          (log/infof "udp-server is closed")))
      (reset! socket-atom socket)))
  (close [_]
    (swap! socket-atom (fn [s]
                         (when s
                           (.close s))
                         nil)))

  IBlockUntilClose
  (blocks-until-close [_]
    (as/<!! (as/go-loop []
              (when-let [socket @socket-atom]
                (when (not (.isClosed socket))
                  (as/<! (as/timeout 1000))
                  (recur)))))))

(defrecord UdpServer [sockets
                      handler
                      config]
  component/Lifecycle
  (start [this]
    (log/info "UdpServer start")
    (let [ips (list-local-ip-addresses)
          _ (log/infof "Listening Ip addresses:%s" (mapv str ips))
          ;; If the socket listen on "0.0.0.0", it will not know which interface received the datagram,
          ;; so it listen on each IP address.
          sockets (mapv (fn [ip]
                          (map->UdpServerSocket {:ip-address ip
                                                 :socket-atom (atom nil)
                                                 :handler (:handler handler)}))
                        ips)]
      (mapv open sockets)
      (assoc this :sockets sockets)))
  (stop [this]
    (log/info "UdpServer stop")
    (mapv close sockets)
    (assoc this :sockets nil))

  IBlockUntilClose
  (blocks-until-close [_]
    (when-first [s sockets]
      (blocks-until-close s))))
