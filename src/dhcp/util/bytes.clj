(ns dhcp.util.bytes
  (:require
   [clojure.string :as str])
  (:import
   (java.util
    HexFormat)))

(defn bytes->number [^bytes bytes]
  (reduce (fn [acc b]
            (+ (* acc 256) (Byte/toUnsignedInt b)))
          0
          bytes))

(defn number->byte-coll [num len]
  (loop [rest num
         bytes '()]
    (if (>= (count bytes) len)
      bytes
      (let [b (mod rest 256)]
        (recur (quot rest 256) (conj bytes b))))))

(defn equal? [^bytes ba1 ^bytes ba2]
  (and (= (count ba1) (count ba2))
       (every? identity
               (map = ba1 ba2))))

(def ^:private hex (.withUpperCase (HexFormat/of)))

(defn ->str
  "Converts a byte array to a hex string."
  [^bytes b]
  (.formatHex hex b))

(defn parse
  [s]
  (-> s
      (str/replace ":" "")
      str/upper-case
      (->> (.parseHex hex))))

(def ^:private hex-colon (.withUpperCase (HexFormat/ofDelimiter ":")))

(defn ->colon-str
  "Converts a byte array to a colon-separated string."
  [^bytes b]
  (.formatHex hex-colon b))
