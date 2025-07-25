(ns dhcp.core.option-test
  (:require
   [clojure.test :refer [deftest testing is]]
   [dhcp.core.option :as option]))

(deftest parse-options-test
  (testing "pad-option"
    (is (= [{:code 0, :type :pad, :length 0, :value []}]
           (option/parse-options
            (byte-array [0])))))
  (testing "end-option"
    (is (= [{:code 255, :type :end, :length 0, :value []}]
           (option/parse-options
            (byte-array [255])))))
  (testing "requested-ip-address"
    (is (= [{:code 50, :type :requested-ip-address, :length 4, :value [-64 -88 0 1]}]
           (option/parse-options
            (byte-array [50 4 192 168 0 1])))))
  (testing "multi options"
    (is (= [{:code 53, :type :dhcp-message-type, :length 1, :value [3]}
            {:code 61, :type :client-identifier, :length 6, :value [10 20 30 40 50 60]}
            {:code 50, :type :requested-ip-address, :length 4, :value [-64 -88 0 1]}
            {:code 255, :type :end, :length 0, :value []}]
           (option/parse-options
            (byte-array [53 1 3
                         61 6 10 20 30 40 50 60
                         50 4 192 168 0 1
                         255]))))))
