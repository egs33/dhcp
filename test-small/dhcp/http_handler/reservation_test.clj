(ns dhcp.http-handler.reservation-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [dhcp.components.database.memory :as db.mem]
   [dhcp.http-handler :as h]
   [dhcp.http-handler.reservation]
   [dhcp.protocol.database :as p.db]
   [dhcp.test-helper :as th]))

(deftest get-reservations-test
  (let [db (db.mem/new-memory-database)]
    (testing "no reservations"
      (is (= {:status 200, :body {:reservations []}}
             (h/handler {:db db
                         :reitit.core/match {:data {:name :get-reservations}}}))))
    (with-redefs [rand-int (th/create-random-mock 1)]
      (p.db/add-reservations db [{:hw-address (byte-array [0 11 22 33 44 55])
                                  :ip-address (byte-array [192 168 0 10])
                                  :source "config"}
                                 {:hw-address (byte-array [0 11 22 00 00 00])
                                  :ip-address (byte-array [192 168 0 20])
                                  :source "api"}]))
    (testing "multiple reservations"
      (is (= {:status 200
              :body {:reservations [{:id 2
                                     :hw-address "00:0B:16:21:2C:37"
                                     :ip-address "192.168.0.10"
                                     :source "config"}
                                    {:id 3
                                     :hw-address "00:0B:16:00:00:00"
                                     :ip-address "192.168.0.20"
                                     :source "api"}]}}
             (h/handler {:db db
                         :reitit.core/match {:data {:name :get-reservations}}}))))))

(deftest add-reservation-test
  (let [db (db.mem/new-memory-database)]
    (testing "reservation added"
      (with-redefs [rand-int (th/create-random-mock 1)]
        (is (= {:status 201
                :body {:hw-address "00:0B:16:21:2C:37"
                       :ip-address "192.168.0.10"
                       :source "api"
                       :id 2}}
               (h/handler {:db db
                           :reitit.core/match {:data {:name :add-reservation}}
                           :parameters {:body {:hw-address "00:0B:16:21:2C:37"
                                               :ip-address "192.168.0.10"}}}))
            "response"))
      (is (= [{:id 2
               :hw-address [0 11 22 33 44 55]
               :ip-address (vec (byte-array [192 168 0 10]))
               :source "api"}]
             (th/array->vec-recursively (p.db/get-all-reservations db)))
          "database"))))
