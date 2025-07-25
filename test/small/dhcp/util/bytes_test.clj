(ns dhcp.util.bytes-test
  (:require
   [clojure.test :refer [deftest are]]
   [dhcp.util.bytes :as u.bytes]))

(deftest bytes->number-test
  (are [expected bytes] (= expected (u.bytes/bytes->number (byte-array bytes)))
    0 []
    0 [0]
    100 [100]
    257 [1 1]
    2130706433 [127 0 0 1]
    3232235521 [192 168 0 1]
    4294967295 [255 255 255 255]))

(deftest number->byte-coll-test
  (are [expected num len] (= expected
                             (u.bytes/number->byte-coll num len))
    [0] 0 1
    [100] 100 1
    [0 1 1] 257 3
    [127 0 0 1] 2130706433 4
    [192 168 0 1] 3232235521 4
    [255 255 255 255] 4294967295 4
    [0 1 0 0 0 0] 4294967296 6))

(deftest equal?-test
  (are [expected ba1 ba2] (= expected
                             (u.bytes/equal? ba1 ba2))
    true (byte-array []) (byte-array [])
    true (byte-array [100]) (byte-array [100])
    true (byte-array [128]) (byte-array [-128])
    true (byte-array [255 0 255]) (byte-array [255 0 255])
    false (byte-array []) (byte-array [1])
    false (byte-array [2]) (byte-array [1])
    false (byte-array [10 20 30]) (byte-array [10 21 31])
    false (byte-array [1]) (byte-array [0 1])))
