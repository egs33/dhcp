(ns dhcp.handler.dhcp-request-test
  (:require
   [clojure.test :refer [deftest testing is]]
   [dhcp.components.database.memory :as db.mem]
   [dhcp.components.handler]
   [dhcp.const.dhcp-type :refer [DHCPREQUEST DHCPACK DHCPNAK]]
   [dhcp.handler :as h]
   [dhcp.handler.dhcp-request]
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
                    (Inet4Address/getByAddress (byte-array  [192 168 0 100]))
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
                      :options [{:code 53, :type :dhcp-message-type, :length 1, :value [DHCPREQUEST]}
                                {:code 61, :type :client-identifier, :length 7, :value [1 11 22 33 44 55 66]}
                                {:code 54, :type :dhcp-server-id, :length 4, :value [192 168 0 100]}
                                {:code 55, :type :parameter-list, :length 2, :value [3 1]}
                                {:code 50, :type :requested-ip-address, :length 4, :value [192 168 0 100]}
                                {:code 0, :type :pad, :length 0, :value []}]})))

(def sample-subnet {:start-address (r.ip-address/str->ip-address "192.168.0.0")
                    :end-address (r.ip-address/str->ip-address "192.168.0.255")
                    :pools [{:start-address (r.ip-address/str->ip-address "192.168.0.50")
                             :end-address (r.ip-address/str->ip-address "192.168.0.110")
                             :only-reserved-lease false
                             :lease-time 3600
                             :reservation []
                             :options [{:code 1, :type :subnet-mask, :length 4, :value [255 255 255 0]}
                                       {:code 3, :type :router, :length 4, :value [192 168 0 1]}
                                       {:code 6, :type :domain-server, :length 4, :value [192 168 0 2]}]}]})

(deftest handler-dhcp-request-test
  (testing "no subnet definition"
    (let [db (db.mem/new-memory-database)]
      (is (nil? (h/handler th/socket-mock
                           db
                           (reify
                             r.config/IConfig
                             (select-subnet [_ _] nil))
                           sample-packet)))))
  (testing "request-in-selecting"
    (let [db (db.mem/new-memory-database)
          config (reify
                   r.config/IConfig
                   (select-subnet [_ _] sample-subnet))]
      (testing "not target packet"
        (testing "server id not match"
          (let [message (update-in sample-packet
                                   [:message :options]
                                   (fn [options]
                                     (map #(if (= (:code %) 54)
                                             (assoc % :value [192 168 0 101])
                                             %)
                                          options)))]
            (is (nil? (h/handler th/socket-mock db config message)))))
        (testing "requested id not in message"
          (let [message (update-in sample-packet
                                   [:message :options]
                                   (fn [options]
                                     (remove #(= (:code %) 50) options)))]
            (is (nil? (h/handler th/socket-mock db config message)))))
        (testing "requested id not in subnet"
          ;; TODO: DHCPNAK?
          (let [message (update-in sample-packet
                                   [:message :options]
                                   (fn [options]
                                     (map #(if (= (:code %) 50)
                                             (assoc % :value [192 167 255 255])
                                             %)
                                          options)))]
            (is (nil? (h/handler th/socket-mock db config message))))))
      (testing "offering record not found"
        (testing "no record"
          (with-redefs [p.db/find-leases-by-hw-address (constantly [])]
            (is (= (r.dhcp-message/map->DhcpMessage {:op :BOOTREPLY
                                                     :htype (byte 1)
                                                     :hlen (byte 6)
                                                     :hops (byte 0)
                                                     :xid 135280220
                                                     :secs 0
                                                     :flags 0x80
                                                     :ciaddr (r.ip-address/->IpAddress 0)
                                                     :yiaddr (r.ip-address/->IpAddress 0)
                                                     :giaddr (r.ip-address/->IpAddress 0)
                                                     :siaddr (r.ip-address/->IpAddress 0)
                                                     :chaddr [11 22 33 44 55 66]
                                                     :file ""
                                                     :options [{:code 53, :type :dhcp-message-type, :length 1, :value [DHCPNAK]}
                                                               {:code 54, :type :dhcp-server-id
                                                                :length 4, :value (th/byte-vec [192 168 0 100])}]
                                                     :sname ""})
                   (h/handler th/socket-mock db config sample-packet)))))
        (testing "other address only"
          (with-redefs [p.db/find-leases-by-hw-address (constantly [{:client-id (byte-array [1 11 22 33 44 55])
                                                                     :hw-address (byte-array [11 22 33 44 55 66])
                                                                     :ip-address (byte-array [192 168 0 101])
                                                                     :hostname "host1"
                                                                     :lease-time 86400
                                                                     :status "offer"
                                                                     :offered-at (Instant/now)
                                                                     :leased-at (Instant/now)
                                                                     :expired-at (.plusSeconds (Instant/now) 2)}
                                                                    {:client-id (byte-array [1 11 22 33 44 55])
                                                                     :hw-address (byte-array [11 22 33 44 55 66])
                                                                     :ip-address (byte-array [172 16 0 100])
                                                                     :hostname "host1"
                                                                     :lease-time 86400
                                                                     :status "offer"
                                                                     :offered-at (Instant/now)
                                                                     :leased-at (Instant/now)
                                                                     :expired-at (.plusSeconds (Instant/now) 2)}])]

            (is (= (r.dhcp-message/map->DhcpMessage {:op :BOOTREPLY
                                                     :htype (byte 1)
                                                     :hlen (byte 6)
                                                     :hops (byte 0)
                                                     :xid 135280220
                                                     :secs 0
                                                     :flags 0x80
                                                     :ciaddr (r.ip-address/->IpAddress 0)
                                                     :yiaddr (r.ip-address/->IpAddress 0)
                                                     :giaddr (r.ip-address/->IpAddress 0)
                                                     :siaddr (r.ip-address/->IpAddress 0)
                                                     :chaddr [11 22 33 44 55 66]
                                                     :file ""
                                                     :options [{:code 53, :type :dhcp-message-type, :length 1, :value [DHCPNAK]}
                                                               {:code 54, :type :dhcp-server-id
                                                                :length 4, :value [-64 -88 0 100]}]
                                                     :sname ""})
                   (h/handler th/socket-mock db config sample-packet)))))
        (testing "lease record expired"
          (with-redefs [p.db/find-leases-by-hw-address (constantly [{:client-id (byte-array [1 11 22 33 44 55])
                                                                     :hw-address (byte-array [11 22 33 44 55 66])
                                                                     :ip-address (byte-array [192 168 0 100])
                                                                     :hostname "host1"
                                                                     :lease-time 86400
                                                                     :status "offer"
                                                                     :offered-at (Instant/now)
                                                                     :leased-at (Instant/now)
                                                                     :expired-at (.minusSeconds (Instant/now) 86401)}])]
            (is (= (r.dhcp-message/map->DhcpMessage {:op :BOOTREPLY
                                                     :htype (byte 1)
                                                     :hlen (byte 6)
                                                     :hops (byte 0)
                                                     :xid 135280220
                                                     :secs 0
                                                     :flags 0x80
                                                     :ciaddr (r.ip-address/->IpAddress 0)
                                                     :yiaddr (r.ip-address/->IpAddress 0)
                                                     :giaddr (r.ip-address/->IpAddress 0)
                                                     :siaddr (r.ip-address/->IpAddress 0)
                                                     :chaddr [11 22 33 44 55 66]
                                                     :file ""
                                                     :options [{:code 53, :type :dhcp-message-type, :length 1, :value [DHCPNAK]}
                                                               {:code 54, :type :dhcp-server-id
                                                                :length 4, :value [-64 -88 0 100]}]
                                                     :sname ""})
                   (h/handler th/socket-mock db config sample-packet))))))
      (testing "send DHCPACK and update lease"
        (let [db (db.mem/new-memory-database)
              lease {:client-id (byte-array [1 11 22 33 44 55])
                     :hw-address (byte-array [11 22 33 44 55 66])
                     :ip-address (byte-array [192 168 0 100])
                     :hostname "host1"
                     :lease-time 86400
                     :status "offer"
                     :offered-at (Instant/now)
                     :leased-at (Instant/now)
                     :expired-at (.plusSeconds (Instant/now) 80000)}
              mock-now (Instant/now)]
          (p.db/add-lease db lease)
          (with-redefs [dhcp.handler.dhcp-request/now (constantly mock-now)]
            (is (= (r.dhcp-message/map->DhcpMessage {:op :BOOTREPLY
                                                     :htype (byte 1)
                                                     :hlen (byte 6)
                                                     :hops (byte 0)
                                                     :xid 135280220
                                                     :secs 0
                                                     :flags 0x80
                                                     :ciaddr (r.ip-address/->IpAddress 0)
                                                     :yiaddr (r.ip-address/str->ip-address "192.168.0.100")
                                                     :giaddr (r.ip-address/->IpAddress 0)
                                                     :siaddr (r.ip-address/->IpAddress 0)
                                                     :chaddr [11 22 33 44 55 66]
                                                     :file ""
                                                     :options [{:code 53, :type :dhcp-message-type, :length 1, :value [DHCPACK]}
                                                               {:code 51, :type :address-time
                                                                :length 4, :value [0 0 14 16]}
                                                               {:code 54, :type :dhcp-server-id
                                                                :length 4, :value [192 168 0 100]}
                                                               {:code 3, :type :router
                                                                :length 4, :value [192 168 0 1]}
                                                               {:code 1, :type :subnet-mask
                                                                :length 4, :value [255 255 255 0]}
                                                               {:code 255, :type :end, :length 0, :value []}]
                                                     :sname ""})
                   (h/handler th/socket-mock db config sample-packet)))
            (is (= [(assoc (th/array->vec-recursively lease)
                           :status "lease"
                           :leased-at mock-now
                           :expired-at (.plusSeconds mock-now 3600))]
                   (->> (p.db/find-leases-by-ip-address-range
                         db (byte-array [192 168 0 100]) (byte-array [192 168 0 100]))
                        (map #(dissoc % :id))
                        (map th/array->vec-recursively)))))))))
  (testing "request-in-init-reboot"
           ;; TODO
           ))
