(ns dhcp.util.bytes)

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
