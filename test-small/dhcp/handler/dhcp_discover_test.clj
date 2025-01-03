(ns dhcp.handler.dhcp-discover-test
  (:require
   [clojure.test :refer [deftest testing is]]
   [dhcp.components.database.memory :as db.mem]
   [dhcp.components.handler]
   [dhcp.core.lease :as core.lease]
   [dhcp.handler :as h]
   [dhcp.protocol.database :as p.db]
   [dhcp.records.config :as r.config]
   [dhcp.records.dhcp-message :as r.dhcp-message]
   [dhcp.records.dhcp-packet :as r.packet]
   [dhcp.records.ip-address :as r.ip-address]
   [dhcp.test-helper :as th])
  (:import
   (java.net
    Inet4Address)
   (java.time
    Instant)))

(def sample-packet (r.packet/->DhcpPacket
                    (byte-array [])
                    (byte-array [])
                    (byte-array [])
                    (Inet4Address/getByAddress (byte-array [192 168 0 100]))
                    true
                    (r.dhcp-message/map->DhcpMessage
                     {:op :BOOTREQUEST
                      :htype (byte 1)
                      :hlen (byte 6)
                      :hops (byte 0)
                      :xid 135280220
                      :secs 0
                      :flags 0x80
                      :ciaddr (r.ip-address/->IpAddress 0)
                      :yiaddr (r.ip-address/->IpAddress 0)
                      :siaddr (r.ip-address/->IpAddress 0)
                      :giaddr (r.ip-address/->IpAddress 0)
                      :chaddr [11 22 33 44 55 66]
                      :sname ""
                      :file ""
                      :options [{:code 53, :type :dhcp-message-type, :length 1, :value [1]}
                                {:code 61, :type :client-identifier, :length 7, :value [1 11 22 33 44 55 66]}
                                {:code 55, :type :parameter-list, :length 2, :value [3 1]}
                                {:code 0, :type :pad, :length 0, :value []}]})))

(def sample-subnet {:start-address (r.ip-address/str->ip-address "192.168.0.0")
                    :end-address (r.ip-address/str->ip-address "192.168.0.255")
                    :pools [{:start-address (r.ip-address/str->ip-address "192.168.0.50")
                             :end-address (r.ip-address/str->ip-address "192.168.0.60")
                             :only-reserved-lease false
                             :lease-time 3600
                             :reservation []
                             :options [{:code 1, :type :subnet-mask, :length 4, :value [255 255 255 0]}
                                       {:code 3, :type :router, :length 4, :value [192 168 0 1]}
                                       {:code 6, :type :domain-server, :length 4, :value [192 168 0 2]}]}]})

(deftest handler-dhcp-discover-test
  (testing "no subnet definition"
    (let [db (db.mem/new-memory-database)]
      (is (nil? (h/handler {:db db
                            :config (reify
                                      r.config/IConfig
                                      (select-subnet [_ _] nil))}
                           sample-packet)))))
  (testing "no available lease"
    (with-redefs [core.lease/choose-ip-address (constantly nil)]
      (let [db (db.mem/new-memory-database)]
        (is (nil? (h/handler {:db db
                              :config (reify
                                        r.config/IConfig
                                        (select-subnet [_ _] sample-subnet))}
                             sample-packet))))))
  (testing "offer-new-lease"
    (with-redefs [core.lease/choose-ip-address (constantly {:pool (first (:pools sample-subnet))
                                                            :ip-address (byte-array [192 168 0 25])
                                                            :status :new
                                                            :lease-time 3600})]
      (let [db (db.mem/new-memory-database)
            _ (p.db/add-lease db {:client-id (byte-array [1 11 22 33 44 55 66])
                                  :hw-address (byte-array [11 22 33 44 55 66])
                                  :ip-address (byte-array [192 168 0 123])
                                  :hostname ""
                                  :lease-time 3600
                                  :status "lease"
                                  :offered-at (Instant/now)
                                  :leased-at (Instant/now)
                                  :expired-at (Instant/now)})]
        (is (= (r.dhcp-message/map->DhcpMessage {:op :BOOTREPLY
                                                 :htype (byte 1)
                                                 :hlen (byte 6)
                                                 :hops (byte 0)
                                                 :xid 135280220
                                                 :secs 0
                                                 :flags 0x80
                                                 :ciaddr (r.ip-address/->IpAddress 0)
                                                 :yiaddr (r.ip-address/str->ip-address "192.168.0.25")
                                                 :giaddr (r.ip-address/->IpAddress 0)
                                                 :siaddr (r.ip-address/->IpAddress 0)
                                                 :chaddr [11 22 33 44 55 66]
                                                 :file ""
                                                 :options [{:code 53, :length 1, :type :dhcp-message-type, :value [2]}
                                                           {:code 51, :length 4, :type :address-time, :value [0 0 14 16]}
                                                           {:code 54, :length 4, :type :dhcp-server-id, :value [192 168 0 100]}
                                                           {:code 3, :length 4, :type :router, :value [192 168 0 1]}
                                                           {:code 1, :length 4, :type :subnet-mask, :value [255 255 255 0]}
                                                           {:code 255, :length 0, :type :end, :value []}]
                                                 :sname ""})
               (h/handler {:db db
                           :config (reify
                                     r.config/IConfig
                                     (select-subnet [_ _] sample-subnet))}
                          sample-packet)))
        (is (= [{:client-id (th/byte-vec [1 11 22 33 44 55 66])
                 :hw-address (th/byte-vec [11 22 33 44 55 66])
                 :ip-address (th/byte-vec [192 168 0 25])
                 :hostname ""
                 :lease-time 3600
                 :status "offer"
                 :leased-at nil}]
               (th/array->vec-recursively (map #(dissoc % :offered-at :expired-at :id)
                                               (p.db/get-all-leases db)))))))))
