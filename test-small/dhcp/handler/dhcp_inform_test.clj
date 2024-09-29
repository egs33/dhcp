(ns dhcp.handler.dhcp-inform-test
  (:require
   [clojure.test :refer [deftest testing is]]
   [dhcp.components.database.memory :as db.mem]
   [dhcp.components.handler]
   [dhcp.const.dhcp-type :refer [DHCPINFORM DHCPACK]]
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
    Inet4Address)))

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
                      :xid 135280221
                      :secs 0
                      :flags 0x80
                      :ciaddr (r.ip-address/str->ip-address "192.168.0.10")
                      :yiaddr (r.ip-address/->IpAddress 0)
                      :siaddr (r.ip-address/->IpAddress 0)
                      :giaddr (r.ip-address/->IpAddress 0)
                      :chaddr [11 22 33 44 55 66]
                      :sname ""
                      :file ""
                      :options [{:code 53, :type :dhcp-message-type, :length 1, :value [DHCPINFORM]}
                                {:code 55, :type :parameter-list, :length 3, :value [3 1 6]}
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
                                       {:code 6, :type :domain-server, :length 4, :value [192 168 0 2]}]}]
                    :options [{:code 1, :type :subnet-mask, :length 4, :value [255 255 255 0]}
                              {:code 3, :type :router, :length 4, :value [192 168 0 1]}]})

(deftest handler-dhcp-inform-test
  (testing "no subnet definition"
    (let [db (db.mem/new-memory-database)]
      (is (nil? (h/handler th/socket-mock
                           db
                           (reify
                             r.config/IConfig
                             (select-subnet [_ _] nil))
                           sample-packet)))))
  (testing "subnet selected"
    (let [db (db.mem/new-memory-database)
          config (reify
                   r.config/IConfig
                   (select-subnet [_ _] sample-subnet))]
      (testing "ciaddr is out of pools"
        (let [packet-to-send (atom nil)]
          (with-redefs [core.packet/send-packet (fn [_ _ reply] (reset! packet-to-send reply) nil)]
            (is (nil? (h/handler th/socket-mock db config sample-packet)))
            (is (= (r.dhcp-message/map->DhcpMessage {:op :BOOTREPLY
                                                     :htype (byte 1)
                                                     :hlen (byte 6)
                                                     :hops (byte 0)
                                                     :xid 135280221
                                                     :secs 0
                                                     :flags 0x80
                                                     :ciaddr (r.ip-address/->IpAddress 0)
                                                     :yiaddr (r.ip-address/str->ip-address "192.168.0.10")
                                                     :giaddr (r.ip-address/->IpAddress 0)
                                                     :siaddr (r.ip-address/->IpAddress 0)
                                                     :chaddr [11 22 33 44 55 66]
                                                     :file ""
                                                     :options [{:code 53, :type :dhcp-message-type, :length 1, :value [DHCPACK]}
                                                               {:code 54, :length 4, :type :dhcp-server-id, :value [192 168 0 100]}
                                                               {:code 3, :length 4, :type :router, :value [192 168 0 1]}
                                                               {:code 1, :length 4, :type :subnet-mask, :value [255 255 255 0]}
                                                               {:code 255, :length 0, :type :end, :value []}]
                                                     :sname ""})
                   @packet-to-send)))))
      (testing "ciaddr is in a pool"
        (let [packet-to-send (atom nil)
              packet (update sample-packet :message #(assoc % :ciaddr (r.ip-address/str->ip-address "192.168.0.50")))]
          (with-redefs [core.packet/send-packet (fn [_ _ reply] (reset! packet-to-send reply) nil)]
            (is (nil? (h/handler th/socket-mock db config packet)))
            (is (= (r.dhcp-message/map->DhcpMessage {:op :BOOTREPLY
                                                     :htype (byte 1)
                                                     :hlen (byte 6)
                                                     :hops (byte 0)
                                                     :xid 135280221
                                                     :secs 0
                                                     :flags 0x80
                                                     :ciaddr (r.ip-address/->IpAddress 0)
                                                     :yiaddr (r.ip-address/str->ip-address "192.168.0.50")
                                                     :giaddr (r.ip-address/->IpAddress 0)
                                                     :siaddr (r.ip-address/->IpAddress 0)
                                                     :chaddr [11 22 33 44 55 66]
                                                     :file ""
                                                     :options [{:code 53, :type :dhcp-message-type, :length 1, :value [DHCPACK]}
                                                               {:code 54, :length 4, :type :dhcp-server-id, :value [192 168 0 100]}
                                                               {:code 3, :length 4, :type :router, :value [192 168 0 1]}
                                                               {:code 1, :length 4, :type :subnet-mask, :value [255 255 255 0]}
                                                               {:code 6, :length 4, :type :domain-server, :value [192 168 0 2]}
                                                               {:code 255, :length 0, :type :end, :value []}]
                                                     :sname ""})
                   @packet-to-send))))))))
