(ns dhcp.components.udp-server
  (:require
   [clojure.core.async :as as]
   [clojure.stacktrace :as trace]
   [clojure.tools.logging :as log]
   [com.stuartsierra.component :as component]
   [dhcp.components.socket :as c.socket]
   [dhcp.const.network :as c.network]
   [dhcp.core.packet :as core.packet]
   [dhcp.records.dhcp-message :as r.dhcp-message]
   [dhcp.records.dhcp-packet :as r.packet]
   [dhcp.records.ip-address :as r.ip-address]
   [dhcp.util.bytes :as u.bytes])
  (:import
   (clojure.lang
    ExceptionInfo
    IFn)
   (java.net
    Inet4Address
    NetworkInterface
    SocketException)
   (java.util
    Arrays)))

(defn- list-local-interfaces
  "Return network interfaces on local machine"
  []
  (->> (NetworkInterface/getNetworkInterfaces)
       enumeration-seq
       (keep (fn [interface]
               (when (.isUp interface)
                 {:name (.getName interface)
                  :hw-address (.getHardwareAddress interface)
                  :ip-addresses (->> (.getInetAddresses interface)
                                     (enumeration-seq)
                                     (filter #(instance? Inet4Address %)))})))
       (remove #(= (:name %) "lo"))))

(defn- parse-udp-packet
  "Parse UDP packet"
  [^bytes packet]
  (let [source-port (u.bytes/bytes->number (Arrays/copyOfRange packet 0 2))
        destination-port (u.bytes/bytes->number (Arrays/copyOfRange packet 2 4))
        payload (Arrays/copyOfRange packet 8 (count packet))]
    {:source-port source-port
     :destination-port destination-port
     :payload payload}))

(defn- parse-ip-packet
  "Parse IP packet"
  [^bytes packet]
  (let [version (bit-shift-right (aget packet 0) 4)
        ihl (bit-and (aget packet 0) 0x0f)
        total-length (u.bytes/bytes->number (Arrays/copyOfRange packet 2 4))
        protocol (aget packet 9)
        source-ip (Arrays/copyOfRange packet 12 16)
        destination-ip (Arrays/copyOfRange packet 16 20)
        payload (Arrays/copyOfRange packet (int (* 4 ihl)) (int total-length))]
    {:version version
     :protocol protocol
     :source-ip (r.ip-address/bytes->ip-address source-ip)
     :destination-ip (r.ip-address/bytes->ip-address destination-ip)
     :udp-payload (when (= protocol 17)
                    (parse-udp-packet payload))}))

(defn- parse-ethernet-frame
  "Parse ethernet frame"
  [^bytes packet]
  (let [destination-mac (Arrays/copyOfRange packet 0 6)
        source-mac (Arrays/copyOfRange packet 6 12)
        ethernet-type (Arrays/copyOfRange packet 12 14)
        payload (Arrays/copyOfRange packet 14 (count packet))]
    {:destination-mac destination-mac
     :source-mac source-mac
     :ethernet-type ethernet-type
     :ip-payload (parse-ip-packet payload)}))

(defprotocol ^:private IUdpServerSocket
  (open [this])
  (close [this]))

(defprotocol IBlockUntilClose
  (blocks-until-close [this]))

(defrecord ^:private UdpServerSocket [^String device-name
                                      ^Inet4Address ip-address
                                      ^bytes hw-address
                                      socket-atom
                                      ^IFn handler
                                      listen-only?]
  IUdpServerSocket
  (open [_]
    (when @socket-atom
      (c.socket/close @socket-atom))
    (let [socket (c.socket/newEthSocket device-name listen-only?)]
      (c.socket/open socket)
      (as/go-loop []
        (if (c.socket/open? socket)
          (let [packet (as/<! (as/thread
                                (try
                                  (c.socket/receive socket)
                                  (catch SocketException e
                                    (log/infof "socket exception %s" e))
                                  (catch ExceptionInfo e
                                    (log/errorf "udp-server exception-info %s %s"
                                                (ex-message e) (ex-data e)))
                                  (catch Exception e
                                    (log/errorf "udp-server exception %s"
                                                e)))))]
            (when packet
              (let [parsed (parse-ethernet-frame packet)]
                (when-let [udp-payload (get-in parsed [:ip-payload :udp-payload])]
                  (when (= (:destination-port udp-payload) c.network/UDP-SERVER-PORT)
                    (try
                      (let [message (r.dhcp-message/parse-message (:payload udp-payload))
                            dhcp-packet (r.packet/->DhcpPacket hw-address
                                                               (:destination-mac parsed)
                                                               (:source-mac parsed)
                                                               ip-address
                                                               (= (r.ip-address/->bytes (get-in parsed [:ip-payload :destination-ip]))
                                                                  [255 255 255 255])
                                                               message)]
                        (try
                          (when-let [reply (handler @socket-atom dhcp-packet)]
                            (core.packet/send-packet @socket-atom packet reply))
                          (catch ExceptionInfo e
                            (log/errorf "handler (type: %s) exception-info %s %s"
                                        (r.dhcp-message/get-type message)
                                        (ex-message e) (ex-data e))
                            (trace/print-stack-trace e))
                          (catch Exception e
                            (log/errorf "handler exception (type: %s) %s"
                                        (r.dhcp-message/get-type message)
                                        e)
                            (trace/print-stack-trace e))))
                      (catch ExceptionInfo e
                        (log/errorf "parse-message exception-info %s %s"
                                    (ex-message e) (ex-data e))
                        (trace/print-stack-trace e))
                      (catch Exception e
                        (log/errorf "parse-message exception %s"
                                    e)
                        (trace/print-stack-trace e)))))))
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
                (when (c.socket/open? socket)
                  (as/<! (as/timeout 1000))
                  (recur)))))))

(defrecord UdpServer [sockets
                      handler
                      config
                      listen-only?]
  component/Lifecycle
  (start [this]
    (log/info "UdpServer start")
    (let [interfaces (cond->> (list-local-interfaces)
                       (seq (:interfaces config))
                       (filter (comp (set (:interfaces config)) :name)))
          sockets (->> interfaces
                       (mapcat (fn [{:keys [:name :ip-addresses :hw-address]}]
                                 (map #(map->UdpServerSocket {:device-name name
                                                              :ip-address %
                                                              :hw-address hw-address
                                                              :socket-atom (atom nil)
                                                              :handler (:handler handler)
                                                              :listen-only? listen-only?})
                                      ip-addresses)))
                       vec)]
      (log/infof "Listening interfaces:%s" (vec interfaces))
      (mapv open sockets)
      (assoc this :sockets sockets)))
  (stop [this]
    (log/info "UdpServer stop")
    (mapv close sockets)
    (assoc this :sockets nil))

  IBlockUntilClose
  (blocks-until-close [_]
    (doseq [s sockets]
      (when s
        (blocks-until-close s)))))
