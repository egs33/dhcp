(ns dhcp.components.socket
  (:refer-clojure :exclude [send])
  (:import
   (com.savarese.rocksaw.net
    RawSocket)
   (java.util
    Arrays)))

(defprotocol ISocket
  (open [this])
  (close [this])
  (open? [this])
  (receive [this])
  (send [this ^bytes data]))

(defrecord ^:private EthSocket [^String device-name
                                socket-atom
                                listen-only?]
  ISocket
  (open [_]
    (let [socket (RawSocket.)]
      (.open socket RawSocket/AF_PACKET RawSocket/ETH_P_IP)
      (.bindAfPacket socket device-name)
      (reset! socket-atom socket)))
  (close [_]
    (if @socket-atom
      (.close @socket-atom)
      (throw (IllegalStateException.))))
  (open? [_]
    (when @socket-atom
      (.isOpen @socket-atom)))
  (receive [_]
    (if @socket-atom
      (let [buf (byte-array 2048)
            len (.read @socket-atom buf)]
        (Arrays/copyOfRange buf 0 (int len)))
      (throw (IllegalStateException.))))
  (send [_ data]
    (when-not listen-only?
      (.writeEth ^RawSocket @socket-atom device-name data 0 (count data)))))

(defn newEthSocket
  ([^String device-name]
   (newEthSocket device-name false))
  ([^String device-name
    listen-only?]
   (->EthSocket device-name (atom nil) listen-only?)))
