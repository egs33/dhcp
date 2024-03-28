(ns dhcp.core.packet-test
  (:require
   [clojure.test :refer [deftest is]]
   [dhcp.core.packet :as sut])
  (:import
   (java.net
    Inet4Address)
   (java.util
    HexFormat)))

(def ^:private hex (HexFormat/of))

(deftest create-udp-datagram-test
  (let [source (Inet4Address/getByAddress (byte-array [8 8 8 8]))
        dest (Inet4Address/getByAddress (byte-array [192 168 0 100]))
        payload (.parseHex hex "456029955d8c94f592b2ceb5b3417f5910b7ac4da5")]
    (is (= "00430044001dd690456029955d8c94f592b2ceb5b3417f5910b7ac4da5"
           (.formatHex hex (byte-array (#'sut/create-udp-datagram source dest 68 payload)))))))
