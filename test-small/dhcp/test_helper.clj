(ns dhcp.test-helper
  (:import
   (com.savarese.rocksaw.net
    RawSocket)
   (java.net
    InetAddress)))

(defn array->vec-recursively
  "Converts all arrays in a map to vectors for comparison"
  [m]
  (cond
    (some-> (class m) (.isArray))
    (vec m)

    (map? m)
    (update-vals m array->vec-recursively)

    (coll? m)
    (mapv array->vec-recursively m)

    :else
    m))

(defn byte-vec
  [^bytes numbers]
  (vec (byte-array numbers)))

(def socket-mock
  (proxy [RawSocket] []
    (write [^InetAddress _address ^bytes _data]
      (throw (ex-info "should not be called" {})))))

(defn create-random-mock
  ([]
   (create-random-mock 0))
  ([start-value]
   (let [counter (atom start-value)]
     (fn [_]
       (swap! counter inc)))))
