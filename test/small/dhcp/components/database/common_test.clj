(ns dhcp.components.database.common-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [dhcp.components.database.common :as sut])
  (:import
   (java.time
    Instant)))

(deftest assert-reservation-test
  (testing "valid reservation"
    (testing "case1"
      (is (nil? (sut/assert-reservation {:hw-address (byte-array [1 2 3 4 5 6])
                                         :ip-address (byte-array [192 168 0 1])
                                         :source "config"}))))
    (testing "case2"
      (is (nil? (sut/assert-reservation {:hw-address (byte-array [1])
                                         :ip-address (byte-array [192 168 10 10])
                                         :source "api"})))))
  (testing "invalid reservation"
    (testing "empty map"
      (is (thrown? IllegalArgumentException
            (sut/assert-reservation {}))))
    (testing "empty hw-address"
      (is (thrown? IllegalArgumentException
            (sut/assert-reservation {:hw-address (byte-array [])
                                     :ip-address (byte-array [192 168 0 1])
                                     :source "config"}))))
    (testing "too short ip-address"
      (is (thrown? IllegalArgumentException
            (sut/assert-reservation {:hw-address (byte-array [1 2 3 4 5 6])
                                     :ip-address (byte-array [192 168 1])
                                     :source "config"}))))
    (testing "too long ip-address"
      (is (thrown? IllegalArgumentException
            (sut/assert-reservation {:hw-address (byte-array [1 2 3 4 5 6])
                                     :ip-address (byte-array [192 168 0 1 1])
                                     :source "config"}))))
    (testing "invalid source"
      (is (thrown? IllegalArgumentException
            (sut/assert-reservation {:hw-address (byte-array [1 2 3 4 5 6])
                                     :ip-address (byte-array [192 168 0 1])
                                     :source "invalid"}))))
    (testing "invalid type"
      (is (thrown? IllegalArgumentException
            (sut/assert-reservation {:hw-address "00:01:02:03:04:05"
                                     :ip-address (byte-array [192 168 0 1])
                                     :source "invalid"}))))))

(deftest assert-lease-test
  (testing "valid lease"
    (testing "case1"
      (is (nil? (sut/assert-lease {:client-id (byte-array [1 2 3 4 5 6])
                                   :hw-address (byte-array [1 2 3 4 5 6])
                                   :ip-address (byte-array [192 168 0 1])
                                   :hostname "host1"
                                   :lease-time 86400
                                   :status "lease"
                                   :offered-at (Instant/now)
                                   :leased-at (Instant/now)
                                   :expired-at (Instant/now)}))))
    (testing "case2"
      (is (nil? (sut/assert-lease {:client-id (byte-array [1 2 3 4 5 6 7 8 9 10])
                                   :hw-address (byte-array [1 2 3 4])
                                   :ip-address (byte-array [192 168 0 1])
                                   :hostname "smartphone-1"
                                   :lease-time 86400
                                   :status "offer"
                                   :offered-at (Instant/now)
                                   :leased-at nil
                                   :expired-at (Instant/now)})))))
  (testing "invalid lease"
    (let [base {:client-id (byte-array [1 2 3 4 5 6])
                :hw-address (byte-array [1 2 3 4 5 6])
                :ip-address (byte-array [192 168 0 1])
                :hostname "host1"
                :lease-time 86400
                :status "lease"
                :offered-at (Instant/now)
                :leased-at (Instant/now)
                :expired-at (Instant/now)}]
      (testing "empty map"
        (is (thrown? IllegalArgumentException
              (sut/assert-lease {}))))
      (testing "empty client-id"
        (is (thrown? IllegalArgumentException
              (sut/assert-lease (assoc base :client-id (byte-array []))))))
      (testing "empty hw-address"
        (is (thrown? IllegalArgumentException
              (sut/assert-lease (assoc base :hw-address (byte-array []))))))
      (testing "too short ip-address"
        (is (thrown? IllegalArgumentException
              (sut/assert-lease (assoc base :ip-address (byte-array [192 168 1]))))))
      (testing "too long ip-address"
        (is (thrown? IllegalArgumentException
              (sut/assert-lease (assoc base :ip-address (byte-array [192 168 0 0 1]))))))
      (testing "invalid status"
        (is (thrown? IllegalArgumentException
              (sut/assert-lease (assoc base :status "expired")))))
      (testing "conditional requirement"
        (is (thrown? IllegalArgumentException
              (sut/assert-lease (assoc base :status "lease" :leased-at nil))))))))
