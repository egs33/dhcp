(ns dhcp.handler.dhcp-decline-test
  (:require
   [clojure.test :refer [deftest testing is]]
   [dhcp.components.database.memory :as db.mem]
   [dhcp.components.handler]
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
                      :options [{:code 53, :type :dhcp-message-type, :length 1, :value [4]}
                                {:code 61, :type :client-identifier, :length 7, :value [1 11 22 33 44 55 66]}
                                {:code 50, :type :requested-ip-address, :length 4, :value [192 168 0 100]}
                                {:code 255, :type :end, :length 0, :value []}]})))

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

(deftest handler-dhcp-decline-test
  (testing "decline"
    (let [db (db.mem/new-memory-database)]
      (p.db/add-lease db {:client-id (byte-array [1 11 22 33 44 55 66])
                          :hw-address (byte-array [11 22 33 44 55 66])
                          :ip-address (byte-array [192 168 0 100])
                          :hostname ""
                          :lease-time 3600
                          :status "offer"
                          :offered-at (Instant/now)
                          :leased-at nil
                          :expired-at (Instant/now)})
      (h/handler th/socket-mock
                 db
                 (reify
                   r.config/IConfig
                   (select-subnet [_ _] sample-subnet))
                 sample-packet)
      (is (= [{:client-id (th/byte-vec [1 11 22 33 44 55 66])
               :hw-address (th/byte-vec [255 255 255 255 255 255])
               :ip-address (th/byte-vec [192 168 0 100])
               :hostname ""
               :lease-time 3600
               :status "declined"
               :leased-at nil}]
             (th/array->vec-recursively (map #(dissoc % :offered-at :expired-at :id)
                                             (p.db/get-all-leases db))))))))
