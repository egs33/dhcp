(ns dhcp.http-handler.lease-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [dhcp.components.database.memory :as db.mem]
   [dhcp.http-handler :as h]
   [dhcp.http-handler.lease]
   [dhcp.protocol.database :as p.db])
  (:import
   (java.time
    Instant)))

(deftest get-leases-test
  (let [db (db.mem/new-memory-database)
        now (Instant/now)]
    (testing "no leases"
      (is (= {:status 200, :body {:leases []}}
             (h/handler {:db db
                         :reitit.core/match {:data {:name :get-leases}}}))))
    (let [lease1 (p.db/add-lease db {:client-id (byte-array [0 1 2 3 4 5])
                                     :hw-address (byte-array [0 11 22 33 44 55])
                                     :ip-address (byte-array [192 168 0 50])
                                     :hostname "smartphone"
                                     :lease-time 86400
                                     :status "lease"
                                     :offered-at now
                                     :leased-at now
                                     :expired-at (.plusSeconds now 40000)})
          lease2 (p.db/add-lease db {:client-id (byte-array [10 20 30])
                                     :hw-address (byte-array [0 11 22 33 44 66])
                                     :ip-address (byte-array [192 168 0 51])
                                     :hostname "tablet"
                                     :lease-time 360
                                     :status "offer"
                                     :offered-at now
                                     :leased-at nil
                                     :expired-at (.plusSeconds now 3000)})]
      (testing "multiple leases"
        (is (= {:status 200
                :body {:leases [(-> (select-keys lease1 [:id :hostname :lease-time :status])
                                    (assoc :client-id "00:01:02:03:04:05"
                                           :hw-address "00:0B:16:21:2C:37"
                                           :ip-address "192.168.0.50"
                                           :offered-at now
                                           :leased-at now
                                           :expired-at (.plusSeconds now 40000)))
                                (-> (select-keys lease2 [:id :hostname :lease-time :status])
                                    (assoc :client-id "0A:14:1E"
                                           :hw-address "00:0B:16:21:2C:42"
                                           :ip-address "192.168.0.51"
                                           :offered-at now
                                           :leased-at nil
                                           :expired-at (.plusSeconds now 3000)))]}}
               (h/handler {:db db
                           :reitit.core/match {:data {:name :get-leases}}})))))))

(deftest get-lease-test
  (let [db (db.mem/new-memory-database)
        now (Instant/now)]
    (testing "no leases"
      (is (= {:status 404, :body {:error "lease not found"}}
             (h/handler {:db db
                         :reitit.core/match {:data {:name :get-lease}}
                         :parameters {:path {:lease-id 1}}}))))
    (let [_lease1 (p.db/add-lease db {:client-id (byte-array [0 1 2 3 4 5])
                                      :hw-address (byte-array [0 11 22 33 44 55])
                                      :ip-address (byte-array [192 168 0 50])
                                      :hostname "smartphone"
                                      :lease-time 86400
                                      :status "lease"
                                      :offered-at now
                                      :leased-at now
                                      :expired-at (.plusSeconds now 40000)})
          lease2 (p.db/add-lease db {:client-id (byte-array [10 20 30])
                                     :hw-address (byte-array [0 11 22 33 44 66])
                                     :ip-address (byte-array [192 168 0 51])
                                     :hostname "tablet"
                                     :lease-time 360
                                     :status "offer"
                                     :offered-at now
                                     :leased-at nil
                                     :expired-at (.plusSeconds now 3000)})]
      (testing "lease found"
        (is (= {:status 200
                :body (-> (select-keys lease2 [:id :hostname :lease-time :status])
                          (assoc :client-id "0A:14:1E"
                                 :hw-address "00:0B:16:21:2C:42"
                                 :ip-address "192.168.0.51"
                                 :offered-at now
                                 :leased-at nil
                                 :expired-at (.plusSeconds now 3000)))}
               (h/handler {:db db
                           :reitit.core/match {:data {:name :get-lease}}
                           :parameters {:path {:lease-id (:id lease2)}}})))))))
