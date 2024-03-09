(ns dhcp.test-helper)

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
