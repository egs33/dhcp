(ns dhcp.core.lease-test
  (:require
   [clojure.test :refer [deftest testing is]]
   [dhcp.components.database :as c.database]
   [dhcp.core.lease :as sut]
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
      (let [db (c.database/create-database "memory")
            _ (c.database/add-reservations db [{:hw-address (byte-array [0 1 2 3 4 5])
                                                :ip-address (byte-array [192 168 0 50])
                                                :source "config"}])]
        (is (= {:pool (th/array->vec-recursively (first (:pools sample-subnet)))
                :ip-address (th/byte-vec [192 168 0 50])
                :lease-time 86400}
               (th/array->vec-recursively (sut/choose-ip-address sample-subnet
                                                                 db
                                                                 (byte-array [0 1 2 3 4 5])
                                                                 (byte-array [0 0 0 0])))))))
    (testing "already leased by same host"
      (let [db (c.database/create-database "memory")
            _ (c.database/add-reservations db [{:hw-address (byte-array [0 1 2 3 4 5])
                                                :ip-address (byte-array [192 168 0 50])
                                                :source "config"}])
            _ (c.database/add-lease db {:client-id (byte-array [0 1 2 3 4 5])
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
                :lease-time 39999}
               (th/array->vec-recursively (sut/choose-ip-address sample-subnet
                                                                 db
                                                                 (byte-array [0 1 2 3 4 5])
                                                                 (byte-array [0 0 0 0])))))))
    (testing "already leased by other host"
      (let [db (c.database/create-database "memory")
            _ (c.database/add-reservations db [{:hw-address (byte-array [0 1 2 3 4 5])
                                                :ip-address (byte-array [192 168 0 50])
                                                :source "config"}])
            _ (c.database/add-lease db {:client-id (byte-array [0 1 2 3 4 5])
                                        :hw-address (byte-array [0 11 22 33 44 55])
                                        :ip-address (byte-array [192 168 0 50])
                                        :hostname "reserved-host"
                                        :lease-time 86400
                                        :status "lease"
                                        :offered-at (Instant/now)
                                        :leased-at (Instant/now)
                                        :expired-at (.plusSeconds (Instant/now) 40000)})]
        ;; TODO
        (is (nil?
             (th/array->vec-recursively (sut/choose-ip-address sample-subnet
                                                               db
                                                               (byte-array [0 1 2 3 4 5])
                                                               (byte-array [0 0 0 0])))))))))
