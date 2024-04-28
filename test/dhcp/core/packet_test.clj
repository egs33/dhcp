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

(deftest create-ip-packet-test
  (let [source (Inet4Address/getByAddress (byte-array [0 0 0 0]))
        dest (Inet4Address/getByAddress (byte-array [192 168 0 100]))
        payload (.parseHex hex "01bcd02b002218d3531f6d49ad9f6667e92f22a834b4d1aabab2d16ebd05bc5efc")]
    (is (= "45000035000000003a11bfac00000000c0a8006401bcd02b002218d3531f6d49ad9f6667e92f22a834b4d1aabab2d16ebd05bc5efc"
           (.formatHex hex (byte-array (#'sut/create-ip-packet source dest payload)))))))
