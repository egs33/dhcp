(ns dhcp.core.lease-test
  (:require
   [clojure.test :refer [deftest testing is]]
   [dhcp.components.database.memory :as db.mem]
   [dhcp.core.lease :as sut]
   [dhcp.protocol.database :as p.db]
   [dhcp.records.ip-address :as r.ip-address]
   [dhcp.test-helper :as th])
  (:import
   (java.time
    Instant)))

(def sample-subnet
  {:start-address (r.ip-address/str->ip-address "192.168.0.0")
   :end-address (r.ip-address/str->ip-address "192.168.0.255")
   :pools [{:start-address (r.ip-address/str->ip-address "192.168.0.1")
            :end-address (r.ip-address/str->ip-address "192.168.0.254")
            :only-reserved-lease false
            :lease-time 86400
            :reservation []
            :options []}]})

(deftest choose-ip-address-test
  (testing "reserved hw-address"
    (testing "no lease"
      (let [db (db.mem/new-memory-database)
            _ (p.db/add-reservations db [{:hw-address (byte-array [0 1 2 3 4 5])
                                          :ip-address (byte-array [192 168 0 50])
                                          :source "config"}])]
        (is (= {:pool (th/array->vec-recursively (first (:pools sample-subnet)))
                :ip-address (th/byte-vec [192 168 0 50])
                :status :new
                :lease-time 86400}
               (th/array->vec-recursively (sut/choose-ip-address sample-subnet
                                                                 db
                                                                 (byte-array [0 1 2 3 4 5])
                                                                 (byte-array [0 0 0 0])))))))
    (testing "already leased by same host"
      (let [db (db.mem/new-memory-database)
            _ (p.db/add-reservations db [{:hw-address (byte-array [0 1 2 3 4 5])
                                          :ip-address (byte-array [192 168 0 50])
                                          :source "config"}])
            _ (p.db/add-lease db {:client-id (byte-array [0 1 2 3 4 5])
                                  :hw-address (byte-array [0 1 2 3 4 5])
                                  :ip-address (byte-array [192 168 0 50])
                                  :hostname "reserved-host"
                                  :lease-time 86400
                                  :status "lease"
                                  :offered-at (Instant/now)
                                  :leased-at (Instant/now)
                                  :expired-at (.plusSeconds (Instant/now) 40000)})]
        (is (= {:pool (th/array->vec-recursively (first (:pools sample-subnet)))
                :ip-address (th/byte-vec [192 168 0 50])
                :status :leasing
                :lease-time 39999}
               (th/array->vec-recursively (sut/choose-ip-address sample-subnet
                                                                 db
                                                                 (byte-array [0 1 2 3 4 5])
                                                                 (byte-array [0 0 0 0])))))))
    (testing "already leased by same host but expired"
      (let [db (db.mem/new-memory-database)
            _ (p.db/add-reservations db [{:hw-address (byte-array [0 1 2 3 4 5])
                                          :ip-address (byte-array [192 168 0 50])
                                          :source "config"}])
            _ (p.db/add-lease db {:client-id (byte-array [0 1 2 3 4 5])
                                  :hw-address (byte-array [0 1 2 3 4 5])
                                  :ip-address (byte-array [192 168 0 50])
                                  :hostname "reserved-host"
                                  :lease-time 86400
                                  :status "lease"
                                  :offered-at (Instant/now)
                                  :leased-at (Instant/now)
                                  :expired-at (.minusSeconds (Instant/now) 1)})]
        (is (= {:pool (th/array->vec-recursively (first (:pools sample-subnet)))
                :ip-address (th/byte-vec [192 168 0 50])
                :status :new
                :lease-time 86400}
               (th/array->vec-recursively (sut/choose-ip-address sample-subnet
                                                                 db
                                                                 (byte-array [0 1 2 3 4 5])
                                                                 (byte-array [0 0 0 0])))))))
    (testing "already leased by other host"
      (let [db (db.mem/new-memory-database)
            _ (p.db/add-reservations db [{:hw-address (byte-array [0 1 2 3 4 5])
                                          :ip-address (byte-array [192 168 0 50])
                                          :source "config"}])
            _ (p.db/add-lease db {:client-id (byte-array [0 1 2 3 4 5])
                                  :hw-address (byte-array [0 11 22 33 44 55])
                                  :ip-address (byte-array [192 168 0 50])
                                  :hostname "reserved-host"
                                  :lease-time 86400
                                  :status "lease"
                                  :offered-at (Instant/now)
                                  :leased-at (Instant/now)
                                  :expired-at (.plusSeconds (Instant/now) 40000)})]
        (is (= {:pool (th/array->vec-recursively (first (:pools sample-subnet)))
                :ip-address (th/byte-vec [192 168 0 1])
                :status :new
                :lease-time 86400}
               (th/array->vec-recursively (sut/choose-ip-address sample-subnet
                                                                 db
                                                                 (byte-array [0 1 2 3 4 5])
                                                                 nil)))))))
  (testing "reservation changed to different MAC address with expired lease"
    (let [db (db.mem/new-memory-database)
          _ (p.db/add-reservations db [{:hw-address (byte-array [0 1 2 3 4 5])
                                        :ip-address (byte-array [192 168 0 50])
                                        :source "config"}])
          _ (p.db/add-lease db {:client-id (byte-array [0 1  2 3 4 5])
                                :hw-address (byte-array [0 1 2 3 4 5])
                                :ip-address (byte-array [192 168 0 50])
                                :hostname "host-a"
                                :lease-time 86400
                                :status "lease"
                                :offered-at (Instant/now)
                                :leased-at (Instant/now)
                                :expired-at (.minusSeconds (Instant/now) 10)})
          _ (p.db/delete-reservations-by-source db "config")
          _ (p.db/add-reservations db [{:hw-address (byte-array [0 11 22 33 44 55])
                                        :ip-address (byte-array [192 168 0 50])
                                        :source "config"}])]
      (is (= {:pool (th/array->vec-recursively (first (:pools sample-subnet)))
              :ip-address (th/byte-vec [192 168 0 50])
              :status :new
              :lease-time 86400}
             (th/array->vec-recursively (sut/choose-ip-address sample-subnet
                                                               db
                                                               (byte-array [0 11 22 33 44 55])
                                                               (byte-array [0 0 0 0])))))))
  (testing "not reserved, already leased and active"
    (let [db (db.mem/new-memory-database)
          _ (p.db/add-lease db {:client-id (byte-array [0 1 2 3 4 5])
                                :hw-address (byte-array [0 1 2 3 4 5])
                                :ip-address (byte-array [192 168 0 50])
                                :hostname "reserved-host"
                                :lease-time 86400
                                :status "lease"
                                :offered-at (Instant/now)
                                :leased-at (Instant/now)
                                :expired-at (.plusSeconds (Instant/now) 40000)})]
      (is (= {:pool (th/array->vec-recursively (first (:pools sample-subnet)))
              :ip-address (th/byte-vec [192 168 0 50])
              :status :leasing
              :lease-time 39999}
             (th/array->vec-recursively (sut/choose-ip-address sample-subnet
                                                               db
                                                               (byte-array [0 1 2 3 4 5])
                                                               (byte-array [0 0 0 0])))))))
  (testing "not reserved, lease expired and not used by other host"
    (let [db (db.mem/new-memory-database)
          _ (p.db/add-lease db {:client-id (byte-array [0 1 2 3 4 5])
                                :hw-address (byte-array [0 1 2 3 4 5])
                                :ip-address (byte-array [192 168 0 50])
                                :hostname "reserved-host"
                                :lease-time 86400
                                :status "lease"
                                :offered-at (Instant/now)
                                :leased-at (Instant/now)
                                :expired-at (.minusSeconds (Instant/now) 1)})]
      (is (= {:pool (th/array->vec-recursively (first (:pools sample-subnet)))
              :ip-address (th/byte-vec [192 168 0 50])
              :status :new
              :lease-time 86400}
             (th/array->vec-recursively (sut/choose-ip-address sample-subnet
                                                               db
                                                               (byte-array [0 1 2 3 4 5])
                                                               (byte-array [0 0 0 0])))))))
  (testing "not reserved, not leased"
    (testing "ip address requested"
      (testing "available"
        (let [db (db.mem/new-memory-database)]
          (is (= {:pool (th/array->vec-recursively (first (:pools sample-subnet)))
                  :ip-address (th/byte-vec [192 168 0 50])
                  :status :new
                  :lease-time 86400}
                 (th/array->vec-recursively (sut/choose-ip-address sample-subnet
                                                                   db
                                                                   (byte-array [0 1 2 3 4 5])
                                                                   (byte-array [192 168 0 50])))))))
      (testing "unavailable"
        (testing "requested address is already leased by other host"
          (let [db (db.mem/new-memory-database)]
            (p.db/add-lease db {:client-id (byte-array [0 11 22 33 44 55])
                                :hw-address (byte-array [0 11 22 33 44 55])
                                :ip-address (byte-array [192 168 0 50])
                                :hostname "reserved-host"
                                :lease-time 86400
                                :status "lease"
                                :offered-at (Instant/now)
                                :leased-at (Instant/now)
                                :expired-at (.plusSeconds (Instant/now) 2)})
            (is (= {:pool (th/array->vec-recursively (first (:pools sample-subnet)))
                    :ip-address (th/byte-vec [192 168 0 1])
                    :status :new
                    :lease-time 86400}
                   (th/array->vec-recursively (sut/choose-ip-address sample-subnet
                                                                     db
                                                                     (byte-array [0 1 2 3 4 5])
                                                                     (byte-array [192 168 0 50])))))))
        (testing "requested address is reserved"
          (let [db (db.mem/new-memory-database)]
            (p.db/add-reservations db [{:hw-address (byte-array [10 20 30 40 50 60])
                                        :ip-address (byte-array [192 168 0 50])
                                        :source "config"}])
            (is (= {:pool (th/array->vec-recursively (first (:pools sample-subnet)))
                    :ip-address (th/byte-vec [192 168 0 1])
                    :status :new
                    :lease-time 86400}
                   (th/array->vec-recursively (sut/choose-ip-address sample-subnet
                                                                     db
                                                                     (byte-array [0 1 2 3 4 5])
                                                                     (byte-array [192 168 0 50]))))))))
      (testing "request out of range"
        (let [db (db.mem/new-memory-database)]
          (is (= {:pool (th/array->vec-recursively (first (:pools sample-subnet)))
                  :ip-address (th/byte-vec [192 168 0 1])
                  :status :new
                  :lease-time 86400}
                 (th/array->vec-recursively (sut/choose-ip-address sample-subnet
                                                                   db
                                                                   (byte-array [0 1 2 3 4 5])
                                                                   (byte-array [172 16 0 50]))))))))
    (testing "ip address not requested"
      (testing "no addresses leased"
        (let [db (db.mem/new-memory-database)]
          (is (= {:pool (th/array->vec-recursively (first (:pools sample-subnet)))
                  :ip-address (th/byte-vec [192 168 0 1])
                  :status :new
                  :lease-time 86400}
                 (th/array->vec-recursively (sut/choose-ip-address sample-subnet
                                                                   db
                                                                   (byte-array [0 1 2 3 4 5])
                                                                   nil))))))
      (testing "some addresses leased"
        (let [db (db.mem/new-memory-database)]
          (doseq [i (range 100)]
            (p.db/add-lease db {:client-id (byte-array [i 11 22 33 44 55])
                                :hw-address (byte-array [i 11 22 33 44 55])
                                :ip-address (byte-array [192 168 0 i])
                                :hostname (str "reserved-host" i)
                                :lease-time 86400
                                :status "lease"
                                :offered-at (Instant/now)
                                :leased-at (Instant/now)
                                :expired-at (.plusSeconds (Instant/now) 2)}))
          (p.db/add-reservations db [{:hw-address (byte-array [10 20 30 40 50 60])
                                      :ip-address (byte-array [192 168 0 100])
                                      :source "config"}])
          (is (= {:pool (th/array->vec-recursively (first (:pools sample-subnet)))
                  :ip-address (th/byte-vec [192 168 0 101])
                  :status :new
                  :lease-time 86400}
                 (th/array->vec-recursively (sut/choose-ip-address sample-subnet
                                                                   db
                                                                   (byte-array [0 1 2 3 4 5])
                                                                   nil))))))
      (testing "pool is full"
        (testing "all leases are active"
          (let [db (db.mem/new-memory-database)]
            (doseq [i (range 256)]
              (p.db/add-lease db {:client-id (byte-array [i 11 22 33 44 55])
                                  :hw-address (byte-array [i 11 22 33 44 55])
                                  :ip-address (byte-array [192 168 0 i])
                                  :hostname (str "reserved-host" i)
                                  :lease-time 86400
                                  :status "lease"
                                  :offered-at (Instant/now)
                                  :leased-at (Instant/now)
                                  :expired-at (.plusSeconds (Instant/now) 2)}))
            (is (nil? (sut/choose-ip-address sample-subnet
                                             db
                                             (byte-array [0 1 2 3 4 5])
                                             nil)))))
        (testing "some leases are expired"
          (let [db (db.mem/new-memory-database)]
            (doseq [i (range 62)]
              (p.db/add-lease db {:client-id (byte-array [i 11 22 33 44 155])
                                  :hw-address (byte-array [i 11 22 33 44 155])
                                  :ip-address (byte-array [192 168 1 i])
                                  :hostname (str "reserved-host" i)
                                  :lease-time 86400
                                  :status "lease"
                                  :offered-at (Instant/now)
                                  :leased-at (Instant/now)
                                  :expired-at (.plusSeconds (Instant/now) 2)}))
            (p.db/add-lease db {:client-id (byte-array [62 11 22 33 44 155])
                                :hw-address (byte-array [62 11 22 33 44 155])
                                :ip-address (byte-array [192 168 1 62])
                                :hostname (str "reserved-host" 62)
                                :lease-time 86400
                                :status "lease"
                                :offered-at (Instant/now)
                                :leased-at (Instant/now)
                                :expired-at (.minusSeconds (Instant/now) 2)})
            (p.db/add-lease db {:client-id (byte-array [63 11 22 33 44 155])
                                :hw-address (byte-array [63 11 22 33 44 155])
                                :ip-address (byte-array [192 168 1 63])
                                :hostname (str "reserved-host" 63)
                                :lease-time 86400
                                :status "lease"
                                :offered-at (Instant/now)
                                :leased-at (Instant/now)
                                :expired-at (.minusSeconds (Instant/now) 1)})
            (let [pool {:start-address (r.ip-address/str->ip-address "192.168.1.1")
                        :end-address (r.ip-address/str->ip-address "192.168.1.63")
                        :only-reserved-lease false
                        :lease-time 86400
                        :reservation []
                        :options []}]
              (is (= {:pool (th/array->vec-recursively pool)
                      :ip-address (th/byte-vec [192 168 1 62])
                      :status :new
                      :lease-time 86400}
                     (th/array->vec-recursively
                      (sut/choose-ip-address {:start-address (r.ip-address/str->ip-address "192.168.1.0")
                                              :end-address (r.ip-address/str->ip-address "192.168.1.255")
                                              :pools [pool]}
                                             db
                                             (byte-array [0 1 2 3 4 5])
                                             nil))))
              (is (= []
                     (p.db/find-leases-by-ip-address db (byte-array [192 168 1 62]))))))))
      (testing "multi pools"
        (let [db (db.mem/new-memory-database)
              subnet {:start-address (r.ip-address/str->ip-address "192.168.0.0")
                      :end-address (r.ip-address/str->ip-address "192.168.0.255")
                      :pools [{:start-address (r.ip-address/str->ip-address "192.168.0.1")
                               :end-address (r.ip-address/str->ip-address "192.168.0.10")
                               :only-reserved-lease false
                               :lease-time 86400
                               :reservation []
                               :options []}
                              {:start-address (r.ip-address/str->ip-address "192.168.0.11")
                               :end-address (r.ip-address/str->ip-address "192.168.0.20")
                               :only-reserved-lease true
                               :lease-time 86400
                               :reservation []
                               :options []}
                              {:start-address (r.ip-address/str->ip-address "192.168.0.21")
                               :end-address (r.ip-address/str->ip-address "192.168.0.30")
                               :only-reserved-lease false
                               :lease-time 86400
                               :reservation []
                               :options []}]}]
          (doseq [i (range 1 11)]
            (p.db/add-lease db {:client-id (byte-array [i 11 22 33 44 55])
                                :hw-address (byte-array [i 11 22 33 44 55])
                                :ip-address (byte-array [192 168 0 i])
                                :hostname (str "reserved-host" i)
                                :lease-time 86400
                                :status "lease"
                                :offered-at (Instant/now)
                                :leased-at (Instant/now)
                                :expired-at (.plusSeconds (Instant/now) 2)}))
          (doseq [i (range 21 25)]
            (p.db/add-lease db {:client-id (byte-array [i 11 22 33 44 55])
                                :hw-address (byte-array [i 11 22 33 44 55])
                                :ip-address (byte-array [192 168 0 i])
                                :hostname (str "reserved-host" i)
                                :lease-time 86400
                                :status "lease"
                                :offered-at (Instant/now)
                                :leased-at (Instant/now)
                                :expired-at (.plusSeconds (Instant/now) 2)}))
          (is (= {:pool (th/array->vec-recursively (nth (:pools subnet) 2))
                  :ip-address (th/byte-vec [192 168 0 25])
                  :status :new
                  :lease-time 86400}
                 (th/array->vec-recursively (sut/choose-ip-address subnet
                                                                   db
                                                                   (byte-array [0 1 2 3 4 5])
                                                                   nil)))))))))
