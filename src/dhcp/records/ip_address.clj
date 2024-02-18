(ns dhcp.records.ip-address
  "IPv4"
  (:require
   [clojure.string :as str]
   [dhcp.util.bytes :as u.bytes]))

(def ip-address-bytes 4)

(defprotocol IIpAddress
  (->int [this])
  (->bytes [this])
  (->byte-array [this]))

(defrecord IpAddress [value]
  Object
  (toString [this]
    (->> (->bytes this)
         (map str)
         (str/join ".")))

  IIpAddress
  (->int [_this] value)
  (->bytes [_this]
    (u.bytes/number->byte-coll value ip-address-bytes))
  (->byte-array ^bytes [this]
    (byte-array (->bytes this))))

(defn bytes->ip-address ^IpAddress [^bytes bytes]
  (IpAddress. (u.bytes/bytes->number bytes)))

(defn str->ip-address ^IpAddress [^String s]
  (let [elements (->> (str/split s #"\.")
                      (keep (fn [e]
                              (when-let [l (parse-long e)]
                                (when (<= 0 l 255)
                                  l)))))]
    (when (not= (count elements) ip-address-bytes)
      (throw (IllegalArgumentException. "Invalid IP Address format")))
    (bytes->ip-address (byte-array elements))))
