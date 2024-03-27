(ns dhcp.handler.dhcp-request-test
  (:require
   [clojure.test :refer [deftest testing is]]
   [dhcp.components.database :as c.database]
   [dhcp.components.handler]
   [dhcp.const.dhcp-type :refer [DHCPREQUEST DHCPACK DHCPNAK]]
   [dhcp.core.packet :as core.packet]
   [dhcp.handler :as h]
   [dhcp.handler.dhcp-request]
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
                                {:code 0, :type :pad, :length 0, :value [192 168 0 51]}]})))

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
    (with-redefs [r.config/select-subnet (constantly nil)]
      (let [db (c.database/create-database "memory")]
        (is (nil? (h/handler th/socket-mock db (r.config/->Config nil) sample-packet))))))
  (testing "request-in-selecting"
    (with-redefs [r.config/select-subnet (constantly sample-subnet)]
      (let [db (c.database/create-database "memory")]
        (testing "not target packet"
          (testing "server id not match"
            (let [message (update-in sample-packet
                                     [:message :options]
                                     (fn [options]
                                       (map #(if (= (:code %) 54)
                                               (assoc % :value [192 168 0 101])
                                               %)
                                            options)))]
              (is (nil? (h/handler th/socket-mock db (r.config/->Config nil) message)))))
          (testing "requested id not in message"
            (let [message (update-in sample-packet
                                     [:message :options]
                                     (fn [options]
                                       (remove #(= (:code %) 50) options)))]
              (is (nil? (h/handler th/socket-mock db (r.config/->Config nil) message)))))
          (testing "requested id not in subnet"
            ;; TODO: DHCPNAK?
            (let [message (update-in sample-packet
                                     [:message :options]
                                     (fn [options]
                                       (map #(if (= (:code %) 50)
                                               (assoc % :value [192 167 255 255])
                                               %)
                                            options)))]
              (is (nil? (h/handler th/socket-mock db (r.config/->Config nil) message))))))
        (testing "offering record not found"
          (testing "no record"
            (let [packet-to-send (atom nil)]
              (with-redefs [c.database/find-leases-by-hw-address (constantly [])
                            core.packet/send-packet (fn [_ _ reply] (reset! packet-to-send reply))]
                (is (nil? (h/handler th/socket-mock db (r.config/->Config nil) sample-packet)))
                (is (= {:op :BOOTREPLY
                        :htype 1
                        :hlen 6
                        :hops 0
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
                        :sname ""}
                       @packet-to-send)))))
          (testing "other address only"
            (let [packet-to-send (atom nil)]
              (with-redefs [c.database/find-leases-by-hw-address (constantly [{:client-id (byte-array [1 11 22 33 44 55])
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
                                                                               :expired-at (.plusSeconds (Instant/now) 2)}])
                            core.packet/send-packet (fn [_ _ reply] (reset! packet-to-send reply))]
                (is (nil? (h/handler th/socket-mock db (r.config/->Config nil) sample-packet)))
                (is (= {:op :BOOTREPLY
                        :htype 1
                        :hlen 6
                        :hops 0
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
                        :sname ""}
                       @packet-to-send)))))
          (testing "lease record expired"
            (let [packet-to-send (atom nil)]
              (with-redefs [c.database/find-leases-by-hw-address (constantly [{:client-id (byte-array [1 11 22 33 44 55])
                                                                               :hw-address (byte-array [11 22 33 44 55 66])
                                                                               :ip-address (byte-array [192 168 0 100])
                                                                               :hostname "host1"
                                                                               :lease-time 86400
                                                                               :status "offer"
                                                                               :offered-at (Instant/now)
                                                                               :leased-at (Instant/now)
                                                                               :expired-at (.minusSeconds (Instant/now) 86401)}])
                            core.packet/send-packet (fn [_ _ reply] (reset! packet-to-send reply))]
                (is (nil? (h/handler th/socket-mock db (r.config/->Config nil) sample-packet)))
                (is (= {:op :BOOTREPLY
                        :htype 1
                        :hlen 6
                        :hops 0
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
                        :sname ""}
                       @packet-to-send))))))
        (testing "send DHCPACK and update lease"
          (let [db (c.database/create-database "memory")
                packet-to-send (atom nil)
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
            (c.database/add-lease db lease)
            (with-redefs [dhcp.handler.dhcp-request/now (constantly mock-now)
                          core.packet/send-packet (fn [_ _ reply] (reset! packet-to-send reply))]
              (is (nil? (h/handler th/socket-mock db (r.config/->Config nil) sample-packet)))
              (is (= {:op :BOOTREPLY
                      :htype 1
                      :hlen 6
                      :hops 0
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
                                 :length 4, :value (th/byte-vec [0 0 14 16])}
                                {:code 54, :type :dhcp-server-id
                                 :length 4, :value (th/byte-vec [192 168 0 100])}
                                {:code 3, :type :router
                                 :length 4, :value (th/byte-vec [192 168 0 1])}
                                {:code 1, :type :subnet-mask
                                 :length 4, :value (th/byte-vec [255 255 255 0])}
                                {:code 255, :type :end, :length 0, :value []}]
                      :sname ""}
                     (r.dhcp-message/parse-message @packet-to-send)))
              (is (= [(assoc (th/array->vec-recursively lease)
                             :status "lease"
                             :leased-at mock-now
                             :expired-at (.plusSeconds mock-now 3600))]
                     (map th/array->vec-recursively
                          (c.database/find-leases-by-ip-address-range
                           db (byte-array [192 168 0 100]) (byte-array [192 168 0 100]))))))))))))
