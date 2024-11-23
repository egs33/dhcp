(ns dhcp.components.database.memory-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [dhcp.components.database.memory :as sut]
   [dhcp.protocol.database :as p.db]
   [dhcp.test-helper :as th])
  (:import
   (java.time
    Instant)))

(deftest memory-database-test
  (testing "reservation-tests"
    (testing "add-and-get-reservations-test"
      (let [db (sut/new-memory-database)]
        (testing "add 2 reservations"
          (let [ret (p.db/add-reservations db [{:hw-address (byte-array [1 2 3 4 5 6])
                                                :ip-address (byte-array [192 168 0 1])
                                                :source "config"}
                                               {:hw-address (byte-array [10 20 30])
                                                :ip-address (byte-array [172 16 0 20])
                                                :source "api"}])]
            (is (= [{:hw-address (th/byte-vec [1 2 3 4 5 6])
                     :ip-address (th/byte-vec [192 168 0 1])
                     :source "config"}
                    {:hw-address (th/byte-vec [10 20 30])
                     :ip-address (th/byte-vec [172 16 0 20])
                     :source "api"}]
                   (->> ret
                        (map #(dissoc % :id))
                        th/array->vec-recursively))))
          (is (= [{:hw-address (th/byte-vec [1 2 3 4 5 6])
                   :ip-address (th/byte-vec [192 168 0 1])
                   :source "config"}
                  {:hw-address (th/byte-vec [10 20 30])
                   :ip-address (th/byte-vec [172 16 0 20])
                   :source "api"}]
                 (->> (p.db/get-all-reservations db)
                      (map #(dissoc % :id))
                      th/array->vec-recursively))))
        (testing "add no reservations"
          (p.db/add-reservations db [])
          (is (= [{:hw-address (th/byte-vec [1 2 3 4 5 6])
                   :ip-address (th/byte-vec [192 168 0 1])
                   :source "config"}
                  {:hw-address (th/byte-vec [10 20 30])
                   :ip-address (th/byte-vec [172 16 0 20])
                   :source "api"}]
                 (->> (p.db/get-all-reservations db)
                      (map #(dissoc % :id))
                      th/array->vec-recursively))))
        (testing "add more reservations"
          (p.db/add-reservations db [{:hw-address (byte-array [7 8 9 10 11 12])
                                      :ip-address (byte-array [192 168 0 5])
                                      :source "config"}
                                     {:hw-address (byte-array [10 20 31])
                                      :ip-address (byte-array [172 16 0 21])
                                      :source "api"}])
          (is (= [{:hw-address (th/byte-vec [1 2 3 4 5 6])
                   :ip-address (th/byte-vec [192 168 0 1])
                   :source "config"}
                  {:hw-address (th/byte-vec [10 20 30])
                   :ip-address (th/byte-vec [172 16 0 20])
                   :source "api"}
                  {:hw-address (th/byte-vec [7 8 9 10 11 12])
                   :ip-address (th/byte-vec [192 168 0 5])
                   :source "config"}
                  {:hw-address (th/byte-vec [10 20 31])
                   :ip-address (th/byte-vec [172 16 0 21])
                   :source "api"}]
                 (->> (p.db/get-all-reservations db)
                      (map #(dissoc % :id))
                      th/array->vec-recursively))))
        (testing "throw exception when adding invalid reservation"
          (testing "empty hw-address"
            (is (thrown? IllegalArgumentException
                  (p.db/add-reservations db [{:hw-address (byte-array [])
                                              :ip-address (byte-array [192 168 0 1])
                                              :source "config"}])))))))
    (testing "find-tests"
      (let [db (sut/new-memory-database)]
        (p.db/add-reservations db [{:hw-address (byte-array [1 2 3 4 5 6])
                                    :ip-address (byte-array [192 168 0 1])
                                    :source "config"}
                                   {:hw-address (byte-array [10 20 30])
                                    :ip-address (byte-array [172 16 0 20])
                                    :source "api"}
                                   {:hw-address (byte-array [1 2 3 4 5 6])
                                    :ip-address (byte-array [172 16 1 1])
                                    :source "config"}])
        (testing "find-reservation-by-id"
          (testing "hit no entry"
            (is (nil? (p.db/find-reservation-by-id db 0))))
          (testing "hit"
            (let [id (-> (p.db/find-reservations-by-hw-address db (byte-array [10 20 30]))
                         first
                         :id)]
              (is (= {:hw-address (th/byte-vec [10 20 30])
                      :ip-address (th/byte-vec [172 16 0 20])
                      :source "api"
                      :id id}
                     (->> (p.db/find-reservation-by-id db id)
                          th/array->vec-recursively))))))
        (testing "find-reservations-by-hw-address-test"
          (testing "hit no entry"
            (is (= []
                   (p.db/find-reservations-by-hw-address db (byte-array [10 20 30 40 50 60])))))
          (testing "hit 1 entry"
            (is (= [{:hw-address (th/byte-vec [10 20 30])
                     :ip-address (th/byte-vec [172 16 0 20])
                     :source "api"}]
                   (->> (p.db/find-reservations-by-hw-address db (byte-array [10 20 30]))
                        (map #(dissoc % :id))
                        th/array->vec-recursively))))
          (testing "hit 2 entry"
            (is (= [{:hw-address (th/byte-vec [1 2 3 4 5 6])
                     :ip-address (th/byte-vec [192 168 0 1])
                     :source "config"}
                    {:hw-address (th/byte-vec [1 2 3 4 5 6])
                     :ip-address (th/byte-vec [172 16 1 1])
                     :source "config"}]
                   (->> (p.db/find-reservations-by-hw-address db (byte-array [1 2 3 4 5 6]))
                        (map #(dissoc % :id))
                        th/array->vec-recursively)))))
        (testing "find-reservations-by-ip-address-range-test"
          (testing "hit no entry"
            (is (= []
                   (p.db/find-reservations-by-ip-address-range
                    db (byte-array [127 0 0 0]) (byte-array [127 255 255 255])))))
          (testing "hit 1 entry"
            (is (= [{:hw-address (th/byte-vec [1 2 3 4 5 6])
                     :ip-address (th/byte-vec [192 168 0 1])
                     :source "config"}]
                   (->> (p.db/find-reservations-by-ip-address-range
                         db (byte-array [192 168 0 0]) (byte-array [192 168 0 255]))
                        (map #(dissoc % :id))
                        th/array->vec-recursively))))
          (testing "hit 2 entry"
            (is (= [{:hw-address (th/byte-vec [10 20 30])
                     :ip-address (th/byte-vec [172 16 0 20])
                     :source "api"}
                    {:hw-address (th/byte-vec [1 2 3 4 5 6])
                     :ip-address (th/byte-vec [172 16 1 1])
                     :source "config"}]
                   (->> (p.db/find-reservations-by-ip-address-range
                         db (byte-array [172 16 0 0]) (byte-array [172 16 15 255]))
                        (map #(dissoc % :id))
                        th/array->vec-recursively))))
          (testing "start and end are inclusive"
            (is (= [{:hw-address (th/byte-vec [1 2 3 4 5 6])
                     :ip-address (th/byte-vec [192 168 0 1])
                     :source "config"}
                    {:hw-address (th/byte-vec [1 2 3 4 5 6])
                     :ip-address (th/byte-vec [172 16 1 1])
                     :source "config"}]
                   (->> (p.db/find-reservations-by-ip-address-range
                         db (byte-array [172 16 1 1]) (byte-array [192 168 0 1]))
                        (map #(dissoc % :id))
                        th/array->vec-recursively)))))))
    (testing "delete-reservation-by-id"
      (let [db (sut/new-memory-database)]
        (p.db/add-reservations db [{:hw-address (byte-array [1 2 3 4 5 6])
                                    :ip-address (byte-array [192 168 0 1])
                                    :source "config"}
                                   {:hw-address (byte-array [10 20 30])
                                    :ip-address (byte-array [172 16 0 20])
                                    :source "api"}
                                   {:hw-address (byte-array [1 2 3 4 5 6])
                                    :ip-address (byte-array [172 16 1 1])
                                    :source "config"}])
        (testing "delete no entry"
          (p.db/delete-reservation-by-id db 0)
          (is (= 3
                 (count (p.db/get-all-reservations db)))))
        (testing "delete 1 entry"
          (let [id (-> (p.db/find-reservations-by-hw-address db (byte-array [10 20 30]))
                       first
                       :id)]
            (p.db/delete-reservation db id)
            (is (nil? (p.db/find-reservation-by-id db id)))
            (is (= 2
                   (count (p.db/get-all-reservations db))))))))
    (testing "delete-reservation-tests"
      (let [db (sut/new-memory-database)]
        (p.db/add-reservations db [{:hw-address (byte-array [1 2 3 4 5 6])
                                    :ip-address (byte-array [192 168 0 1])
                                    :source "config"}
                                   {:hw-address (byte-array [10 20 30])
                                    :ip-address (byte-array [172 16 0 20])
                                    :source "api"}
                                   {:hw-address (byte-array [1 2 3 4 5 6])
                                    :ip-address (byte-array [172 16 1 1])
                                    :source "config"}])
        (testing "delete no entry"
          (p.db/delete-reservation
           db (byte-array [10 20 30 40 50 60]))
          (is (= 3
                 (count (p.db/get-all-reservations db)))))
        (testing "delete 1 entry"
          (p.db/delete-reservation
           db (byte-array [10 20 30]))
          (is (= [{:hw-address (th/byte-vec [1 2 3 4 5 6])
                   :ip-address (th/byte-vec [192 168 0 1])
                   :source "config"}
                  {:hw-address (th/byte-vec [1 2 3 4 5 6])
                   :ip-address (th/byte-vec [172 16 1 1])
                   :source "config"}]
                 (->> (p.db/get-all-reservations db)
                      (map #(dissoc % :id))
                      th/array->vec-recursively))))))
    (testing "delete-reservations-by-source"
      (let [db (sut/new-memory-database)]
        (p.db/add-reservations db [{:hw-address (byte-array [1 2 3 4 5 6])
                                    :ip-address (byte-array [192 168 0 1])
                                    :source "config"}
                                   {:hw-address (byte-array [10 20 30])
                                    :ip-address (byte-array [172 16 0 20])
                                    :source "api"}
                                   {:hw-address (byte-array [1 2 3 4 5 6])
                                    :ip-address (byte-array [172 16 1 1])
                                    :source "config"}])
        (testing "delete source=config"
          (p.db/delete-reservations-by-source db "config")
          (is (= [{:hw-address (th/byte-vec [10 20 30])
                   :ip-address (th/byte-vec [172 16 0 20])
                   :source "api"}]
                 (->> (p.db/get-all-reservations db)
                      (map #(dissoc % :id))
                      th/array->vec-recursively)))))))
  (testing "lease-tests"
    (testing "add-and-get-leases-test"
      (let [db (sut/new-memory-database)
            now (Instant/now)]
        (testing "add lease"
          (p.db/add-lease db {:client-id (byte-array [1 2 3 4 5 6])
                              :hw-address (byte-array [1 2 3 4 5 6])
                              :ip-address (byte-array [192 168 0 1])
                              :hostname "host1"
                              :lease-time 86400
                              :status "lease"
                              :offered-at now
                              :leased-at now
                              :expired-at now})
          (is (= [{:client-id (th/byte-vec [1 2 3 4 5 6])
                   :hw-address (th/byte-vec [1 2 3 4 5 6])
                   :ip-address (th/byte-vec [192 168 0 1])
                   :hostname "host1"
                   :lease-time 86400
                   :status "lease"
                   :offered-at now
                   :leased-at now
                   :expired-at now}]
                 (->> (p.db/get-all-leases db)
                      (map #(dissoc % :id))
                      (th/array->vec-recursively)))))
        (testing "add more leases"
          (let [now2 (Instant/now)]
            (p.db/add-lease db {:client-id (byte-array [10 20 30])
                                :hw-address (byte-array [10 20 30])
                                :ip-address (byte-array [172 16 0 19])
                                :hostname "host2"
                                :lease-time 3600
                                :status "offer"
                                :offered-at now2
                                :leased-at nil
                                :expired-at now2})
            (is (= [{:client-id (th/byte-vec [1 2 3 4 5 6])
                     :hw-address (th/byte-vec [1 2 3 4 5 6])
                     :ip-address (th/byte-vec [192 168 0 1])
                     :hostname "host1"
                     :lease-time 86400
                     :status "lease"
                     :offered-at now
                     :leased-at now
                     :expired-at now}
                    {:client-id (th/byte-vec [10 20 30])
                     :hw-address (th/byte-vec [10 20 30])
                     :ip-address (th/byte-vec [172 16 0 19])
                     :hostname "host2"
                     :lease-time 3600
                     :status "offer"
                     :offered-at now2
                     :leased-at nil
                     :expired-at now2}]
                   (->> (p.db/get-all-leases db)
                        (map #(dissoc % :id))
                        (th/array->vec-recursively))))))
        (testing "throw exception when adding invalid lease"
          (testing "empty hw-address"
            (is (thrown? IllegalArgumentException
                  (p.db/add-lease db {})))))))
    (testing "find-tests"
      (let [db (sut/new-memory-database)
            now (Instant/now)
            lease1 (p.db/add-lease db {:client-id (byte-array [1 2 3 4 5 6])
                                       :hw-address (byte-array [1 2 3 4 5 6])
                                       :ip-address (byte-array [192 168 0 1])
                                       :hostname "host1"
                                       :lease-time 86400
                                       :status "lease"
                                       :offered-at now
                                       :leased-at now
                                       :expired-at now})]
        (p.db/add-lease db {:client-id (byte-array [10 20 30])
                            :hw-address (byte-array [10 20 30])
                            :ip-address (byte-array [172 16 0 19])
                            :hostname "host2"
                            :lease-time 3600
                            :status "offer"
                            :offered-at now
                            :leased-at nil
                            :expired-at now})
        (p.db/add-lease db {:client-id (byte-array [40 50 60])
                            :hw-address (byte-array [40 50 60])
                            :ip-address (byte-array [172 16 0 58])
                            :hostname "tablet10"
                            :lease-time 7200
                            :status "offer"
                            :offered-at now
                            :leased-at nil
                            :expired-at now})
        (testing "find-leases-by-hw-address-test"
          (testing "hit no entry"
            (is (= []
                   (p.db/find-leases-by-hw-address db (byte-array [10 20 30 40 50 60])))))
          (testing "hit 1 entry"
            (is (= [{:client-id (th/byte-vec [10 20 30])
                     :hw-address (th/byte-vec [10 20 30])
                     :ip-address (th/byte-vec [172 16 0 19])
                     :hostname "host2"
                     :lease-time 3600
                     :status "offer"
                     :offered-at now
                     :leased-at nil
                     :expired-at now}]
                   (->> (p.db/find-leases-by-hw-address db (byte-array [10 20 30]))
                        (map #(dissoc % :id))
                        (th/array->vec-recursively))))))
        (testing "find-leases-by-ip-address-range-test"
          (testing "hit no entry"
            (is (= []
                   (p.db/find-leases-by-ip-address-range
                    db (byte-array [127 0 0 0]) (byte-array [127 255 255 255])))))
          (testing "hit 1 entry"
            (is (= [{:client-id (th/byte-vec [1 2 3 4 5 6])
                     :hw-address (th/byte-vec [1 2 3 4 5 6])
                     :ip-address (th/byte-vec [192 168 0 1])
                     :hostname "host1"
                     :lease-time 86400
                     :status "lease"
                     :offered-at now
                     :leased-at now
                     :expired-at now}]
                   (->> (p.db/find-leases-by-ip-address-range
                         db (byte-array [192 168 0 0]) (byte-array [192 168 0 255]))
                        (map #(dissoc % :id))
                        (th/array->vec-recursively)))))
          (testing "hit 2 entry"
            (is (= [{:client-id (th/byte-vec [10 20 30])
                     :hw-address (th/byte-vec [10 20 30])
                     :ip-address (th/byte-vec [172 16 0 19])
                     :hostname "host2"
                     :lease-time 3600
                     :status "offer"
                     :offered-at now
                     :leased-at nil
                     :expired-at now}
                    {:client-id (th/byte-vec [40 50 60])
                     :hw-address (th/byte-vec [40 50 60])
                     :ip-address (th/byte-vec [172 16 0 58])
                     :hostname "tablet10"
                     :lease-time 7200
                     :status "offer"
                     :offered-at now
                     :leased-at nil
                     :expired-at now}]
                   (->> (p.db/find-leases-by-ip-address-range
                         db (byte-array [172 16 0 0]) (byte-array [172 16 15 255]))
                        (map #(dissoc % :id))
                        (th/array->vec-recursively))))
            (testing "start and end are inclusive"
              (is (= [{:client-id (th/byte-vec [1 2 3 4 5 6])
                       :hw-address (th/byte-vec [1 2 3 4 5 6])
                       :ip-address (th/byte-vec [192 168 0 1])
                       :hostname "host1"
                       :lease-time 86400
                       :status "lease"
                       :offered-at now
                       :leased-at now
                       :expired-at now}
                      {:client-id (th/byte-vec [40 50 60])
                       :hw-address (th/byte-vec [40 50 60])
                       :ip-address (th/byte-vec [172 16 0 58])
                       :hostname "tablet10"
                       :lease-time 7200
                       :status "offer"
                       :offered-at now
                       :leased-at nil
                       :expired-at now}]
                     (->> (p.db/find-leases-by-ip-address-range
                           db (byte-array [172 16 0 58]) (byte-array [192 168 0 1]))
                          (map #(dissoc % :id))
                          (th/array->vec-recursively)))))))
        (testing "find-lease-by-id"
          (testing "hit no entry"
            (is (nil? (p.db/find-lease-by-id db Long/MAX_VALUE))))
          (testing "hit 1 entry"
            (is (= lease1
                   (p.db/find-lease-by-id db (:id lease1)))))))
      (testing "update-lease-test"
               ;; TODO
               )
      (testing "delete-lease-tests"
        (let [db (sut/new-memory-database)
              now (Instant/now)]
          (p.db/add-lease db {:client-id (byte-array [1 2 3 4 5 6])
                              :hw-address (byte-array [1 2 3 4 5 6])
                              :ip-address (byte-array [192 168 0 1])
                              :hostname "host1"
                              :lease-time 86400
                              :status "lease"
                              :offered-at now
                              :leased-at now
                              :expired-at now})
          (p.db/add-lease db {:client-id (byte-array [1 2 3 4 5 6])
                              :hw-address (byte-array [1 2 3 4 5 6])
                              :ip-address (byte-array [192 168 0 2])
                              :hostname "host1 (2)"
                              :lease-time 86400
                              :status "lease"
                              :offered-at now
                              :leased-at now
                              :expired-at now})
          (p.db/add-lease db {:client-id (byte-array [10 20 30])
                              :hw-address (byte-array [10 20 30])
                              :ip-address (byte-array [172 16 0 19])
                              :hostname "host2"
                              :lease-time 3600
                              :status "offer"
                              :offered-at now
                              :leased-at nil
                              :expired-at now})
          (p.db/add-lease db {:client-id (byte-array [40 50 60])
                              :hw-address (byte-array [40 50 60])
                              :ip-address (byte-array [172 16 0 58])
                              :hostname "tablet10"
                              :lease-time 7200
                              :status "offer"
                              :offered-at now
                              :leased-at nil
                              :expired-at now})
          (testing "delete no entry"
            (p.db/delete-lease
             db (byte-array [10 20 30 40 50 60]) (byte-array [192 168 0 1]) (byte-array [192 168 0 255]))
            (is (= 4
                   (count (p.db/get-all-leases db)))))
          (testing "delete 1 entry"
            (p.db/delete-lease
             db (byte-array [10 20 30]) (byte-array [172 16 0 0]) (byte-array [172 16 0 255]))
            (is (= [{:client-id (th/byte-vec [1 2 3 4 5 6])
                     :hw-address (th/byte-vec [1 2 3 4 5 6])
                     :ip-address (th/byte-vec [192 168 0 1])
                     :hostname "host1"
                     :lease-time 86400
                     :status "lease"
                     :offered-at now
                     :leased-at now
                     :expired-at now}
                    {:client-id (th/byte-vec [1 2 3 4 5 6])
                     :hw-address (th/byte-vec [1 2 3 4 5 6])
                     :ip-address (th/byte-vec [192 168 0 2])
                     :hostname "host1 (2)"
                     :lease-time 86400
                     :status "lease"
                     :offered-at now
                     :leased-at now
                     :expired-at now}
                    {:client-id (th/byte-vec [40 50 60])
                     :hw-address (th/byte-vec [40 50 60])
                     :ip-address (th/byte-vec [172 16 0 58])
                     :hostname "tablet10"
                     :lease-time 7200
                     :status "offer"
                     :offered-at now
                     :leased-at nil
                     :expired-at now}]
                   (->> (p.db/get-all-leases db)
                        (map #(dissoc % :id))
                        (th/array->vec-recursively)))))
          (testing "delete 2 entry"
            (p.db/delete-lease
             db (byte-array [1 2 3 4 5 6]) (byte-array [192 168 0 1]) (byte-array [192 168 0 255]))
            (is (= [{:client-id (th/byte-vec [40 50 60])
                     :hw-address (th/byte-vec [40 50 60])
                     :ip-address (th/byte-vec [172 16 0 58])
                     :hostname "tablet10"
                     :lease-time 7200
                     :status "offer"
                     :offered-at now
                     :leased-at nil
                     :expired-at now}]
                   (->> (p.db/get-all-leases db)
                        (map #(dissoc % :id))
                        (th/array->vec-recursively))))))))))
