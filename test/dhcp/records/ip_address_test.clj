(ns dhcp.records.ip-address-test
  (:require
   [clojure.test :refer [deftest testing are]]
   [dhcp.records.ip-address :as r.ip-address]))

(deftest IpAddress-test
  (testing "toString-test"
    (are [expected number] (= expected (str (r.ip-address/->IpAddress number)))
      "0.0.0.0" 0
      "0.0.0.100" 100
      "1.1.1.1" 16843009
      "127.0.0.1" 2130706433
      "192.168.0.1" 3232235521
      "255.255.255.255" 4294967295))
  (testing "->int-test"
    (are [expected number] (= expected
                              (r.ip-address/->int (r.ip-address/->IpAddress number)))
      0 0
      100 100
      16843009 16843009
      2130706433 2130706433
      3232235521 3232235521
      4294967295 4294967295))
  (testing "->bytes-test"
    (are [expected number] (= expected
                              (r.ip-address/->bytes (r.ip-address/->IpAddress number)))
      [0 0 0 0] 0
      [0 0 0 100] 100
      [1 1 1 1] 16843009
      [127 0 0 1] 2130706433
      [192 168 0 1] 3232235521
      [255 255 255 255] 4294967295))
  (testing "->byte-array-test"
    (are [expected number] (= expected
                              (map #(Byte/toUnsignedInt %)
                                   (r.ip-address/->byte-array
                                    (r.ip-address/->IpAddress number))))
      [0 0 0 0] 0
      [0 0 0 100] 100
      [1 1 1 1] 16843009
      [127 0 0 1] 2130706433
      [192 168 0 1] 3232235521
      [255 255 255 255] 4294967295)))

(deftest bytes->ip-address-test
  (are [expected bytes] (= expected
                           (r.ip-address/->int
                            (r.ip-address/bytes->ip-address
                             (byte-array bytes))))
    0 [0 0 0 0]
    100 [0 0 0 100]
    16843009 [1 1 1 1]
    2130706433 [127 0 0 1]
    3232235521 [192 168 0 1]
    4294967295 [255 255 255 255]))

(deftest str->ip-address-test
  (are [expected str] (= expected
                         (r.ip-address/->int (r.ip-address/str->ip-address str)))
    0 "0.0.0.0"
    100 "0.0.0.100"
    16843009 "1.1.1.1"
    2130706433 "127.0.0.1"
    3232235521 "192.168.0.1"
    4294967295 "255.255.255.255"))
