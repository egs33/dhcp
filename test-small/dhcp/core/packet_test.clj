(ns dhcp.core.packet-test
  (:require
   [clojure.test :refer [deftest testing is]]
   [dhcp.const.dhcp-type :refer [DHCPACK DHCPNAK]]
   [dhcp.core.packet :as sut]
   [dhcp.records.dhcp-message :as r.dhcp-message]
   [dhcp.records.dhcp-packet :as r.packet]
   [dhcp.records.ip-address :as r.ip-address]
   [dhcp.test-helper :as th])
  (:import
   (java.net
    Inet4Address)
   (java.util
    HexFormat)))

(def ^:private hex (HexFormat/of))

(deftest create-udp-datagram-test
  (let [source (Inet4Address/getByAddress (byte-array [8 8 8 8]))
        dest (Inet4Address/getByAddress (byte-array [192 168 0 100]))
        payload (.parseHex hex "456029955d8c94f592b2ceb5b3417f5910b7ac4da5")]
    (is (= "00430044001dd690456029955d8c94f592b2ceb5b3417f5910b7ac4da5"
           (.formatHex hex (byte-array (#'sut/create-udp-datagram source dest 68 payload)))))))

(deftest create-ip-packet-test
  (let [source (Inet4Address/getByAddress (byte-array [0 0 0 0]))
        dest (Inet4Address/getByAddress (byte-array [192 168 0 100]))
        payload (.parseHex hex "01bcd02b002218d3531f6d49ad9f6667e92f22a834b4d1aabab2d16ebd05bc5efc")]
    (is (= "45000035000000003a11bfac00000000c0a8006401bcd02b002218d3531f6d49ad9f6667e92f22a834b4d1aabab2d16ebd05bc5efc"
           (.formatHex hex (byte-array (#'sut/create-ip-packet source dest payload)))))))

(deftest get-packet-info-test
  (testing "replay to relay"
    (let [request-message (r.dhcp-message/map->DhcpMessage
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
                            :giaddr (r.ip-address/str->ip-address "192.168.12.34")
                            :chaddr [11 22 33 44 55 66]
                            :sname ""
                            :file ""
                            :options [{:code 53, :type :dhcp-message-type, :length 1, :value [1]}
                                      {:code 61, :type :client-identifier, :length 7, :value [1 11 22 33 44 55 66]}
                                      {:code 55, :type :parameter-list, :length 2, :value [3 1]}
                                      {:code 0, :type :pad, :length 0, :value []}]})
          request (r.packet/->DhcpPacket (byte-array [1 2 3 4 5 6])
                                         (byte-array [1 2 3 4 5 6])
                                         (byte-array [6 5 4 3 2 1])
                                         (Inet4Address/getByAddress (byte-array [192 168 0 1]))
                                         false
                                         request-message)
          reply-message (r.dhcp-message/map->DhcpMessage
                         {:op :BOOTREPLY
                          :htype (byte 1)
                          :hlen (byte 6)
                          :hops (byte 0)
                          :xid 123456
                          :secs 0
                          :flags 0
                          :ciaddr (r.ip-address/bytes->ip-address (byte-array [192 168 0 1]))
                          :yiaddr (r.ip-address/bytes->ip-address (byte-array [192 168 0 1]))
                          :siaddr (r.ip-address/bytes->ip-address (byte-array [192 168 0 1]))
                          :giaddr (r.ip-address/bytes->ip-address (byte-array [192 168 0 1]))
                          :chaddr (byte-array [1 2 3 4 5 6])
                          :sname ""
                          :file ""
                          :options [{:code 53, :type :dhcp-message-type, :length 1, :value [DHCPACK]}
                                    {:code 54, :type :dhcp-server-id
                                     :length 4, :value [-64 -88 0 100]}]})]
      (is (= {:dest-ip-addr (Inet4Address/getByAddress (byte-array [192 168 12 34]))
              :dest-mac-addr [6 5 4 3 2 1]
              :dest-port 67}
             (th/array->vec-recursively (#'sut/get-packet-info request reply-message))))))
  (testing "DHCPNAK"
    (let [request-message (r.dhcp-message/map->DhcpMessage
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
                                      {:code 0, :type :pad, :length 0, :value []}]})
          request (r.packet/->DhcpPacket (byte-array [1 2 3 4 5 6])
                                         (byte-array [1 2 3 4 5 6])
                                         (byte-array [6 5 4 3 2 1])
                                         (Inet4Address/getByAddress (byte-array [192 168 0 1]))
                                         false
                                         request-message)
          reply-message (r.dhcp-message/map->DhcpMessage
                         {:op :BOOTREPLY
                          :htype (byte 1)
                          :hlen (byte 6)
                          :hops (byte 0)
                          :xid 123456
                          :secs 0
                          :flags 0
                          :ciaddr (r.ip-address/bytes->ip-address (byte-array [192 168 0 1]))
                          :yiaddr (r.ip-address/bytes->ip-address (byte-array [192 168 0 1]))
                          :siaddr (r.ip-address/bytes->ip-address (byte-array [192 168 0 1]))
                          :giaddr (r.ip-address/bytes->ip-address (byte-array [192 168 0 1]))
                          :chaddr (byte-array [1 2 3 4 5 6])
                          :sname ""
                          :file ""
                          :options [{:code 53, :type :dhcp-message-type, :length 1, :value [DHCPNAK]}]})]
      (is (= {:dest-ip-addr (Inet4Address/getByAddress (byte-array [255 255 255 255]))
              :dest-mac-addr (th/byte-vec (byte-array [255 255 255 255 255 255]))
              :dest-port 68}
             (th/array->vec-recursively (#'sut/get-packet-info request reply-message))))))
  (testing "ciaddr is not 0"
    (let [request-message (r.dhcp-message/map->DhcpMessage
                           {:op :BOOTREQUEST
                            :htype (byte 1)
                            :hlen (byte 6)
                            :hops (byte 0)
                            :xid 135280220
                            :secs 0
                            :flags 0x80
                            :ciaddr (r.ip-address/str->ip-address "192.168.10.20")
                            :yiaddr (r.ip-address/->IpAddress 0)
                            :siaddr (r.ip-address/->IpAddress 0)
                            :giaddr (r.ip-address/->IpAddress 0)
                            :chaddr [11 22 33 44 55 66]
                            :sname ""
                            :file ""
                            :options [{:code 53, :type :dhcp-message-type, :length 1, :value [1]}
                                      {:code 61, :type :client-identifier, :length 7, :value [1 11 22 33 44 55 66]}
                                      {:code 55, :type :parameter-list, :length 2, :value [3 1]}
                                      {:code 0, :type :pad, :length 0, :value []}]})
          request (r.packet/->DhcpPacket (byte-array [1 2 3 4 5 6])
                                         (byte-array [1 2 3 4 5 6])
                                         (byte-array [6 5 4 3 2 1])
                                         (Inet4Address/getByAddress (byte-array [192 168 0 1]))
                                         false
                                         request-message)
          reply-message (r.dhcp-message/map->DhcpMessage
                         {:op :BOOTREPLY
                          :htype (byte 1)
                          :hlen (byte 6)
                          :hops (byte 0)
                          :xid 123456
                          :secs 0
                          :flags 0
                          :ciaddr (r.ip-address/bytes->ip-address (byte-array [192 168 0 1]))
                          :yiaddr (r.ip-address/bytes->ip-address (byte-array [192 168 0 1]))
                          :siaddr (r.ip-address/bytes->ip-address (byte-array [192 168 0 1]))
                          :giaddr (r.ip-address/bytes->ip-address (byte-array [192 168 0 1]))
                          :chaddr (byte-array [1 2 3 4 5 6])
                          :sname ""
                          :file ""
                          :options [{:code 53, :type :dhcp-message-type, :length 1, :value [DHCPACK]}]})]
      (is (= {:dest-ip-addr (Inet4Address/getByAddress (byte-array [192 168 10 20]))
              :dest-mac-addr (th/byte-vec (byte-array [6 5 4 3 2 1]))
              :dest-port 68}
             (th/array->vec-recursively (#'sut/get-packet-info request reply-message))))))
  (testing "not ciaddr and broadcast"
    (let [request-message (r.dhcp-message/map->DhcpMessage
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
                                      {:code 0, :type :pad, :length 0, :value []}]})
          request (r.packet/->DhcpPacket (byte-array [1 2 3 4 5 6])
                                         (byte-array [1 2 3 4 5 6])
                                         (byte-array [6 5 4 3 2 1])
                                         (Inet4Address/getByAddress (byte-array [192 168 0 1]))
                                         false
                                         request-message)
          reply-message (r.dhcp-message/map->DhcpMessage
                         {:op :BOOTREPLY
                          :htype (byte 1)
                          :hlen (byte 6)
                          :hops (byte 0)
                          :xid 123456
                          :secs 0
                          :flags 0
                          :ciaddr (r.ip-address/bytes->ip-address (byte-array [192 168 0 1]))
                          :yiaddr (r.ip-address/bytes->ip-address (byte-array [192 168 0 1]))
                          :siaddr (r.ip-address/bytes->ip-address (byte-array [192 168 0 1]))
                          :giaddr (r.ip-address/bytes->ip-address (byte-array [192 168 0 1]))
                          :chaddr (byte-array [1 2 3 4 5 6])
                          :sname ""
                          :file ""
                          :options [{:code 53, :type :dhcp-message-type, :length 1, :value [DHCPACK]}]})]
      (is (= {:dest-ip-addr (Inet4Address/getByAddress (byte-array [255 255 255 255]))
              :dest-mac-addr (th/byte-vec (byte-array [255 255 255 255 255 255]))
              :dest-port 68}
             (th/array->vec-recursively (#'sut/get-packet-info request reply-message))))))
  (testing "not ciaddr and unicast"
    (let [request-message (r.dhcp-message/map->DhcpMessage
                           {:op :BOOTREQUEST
                            :htype (byte 1)
                            :hlen (byte 6)
                            :hops (byte 0)
                            :xid 135280220
                            :secs 0
                            :flags 0x00
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
                                      {:code 0, :type :pad, :length 0, :value []}]})
          request (r.packet/->DhcpPacket (byte-array [1 2 3 4 5 6])
                                         (byte-array [1 2 3 4 5 6])
                                         (byte-array [6 5 4 3 2 1])
                                         (Inet4Address/getByAddress (byte-array [192 168 0 1]))
                                         false
                                         request-message)
          reply-message (r.dhcp-message/map->DhcpMessage
                         {:op :BOOTREPLY
                          :htype (byte 1)
                          :hlen (byte 6)
                          :hops (byte 0)
                          :xid 123456
                          :secs 0
                          :flags 0
                          :ciaddr (r.ip-address/bytes->ip-address (byte-array [192 168 0 1]))
                          :yiaddr (r.ip-address/bytes->ip-address (byte-array [192 168 20 30]))
                          :siaddr (r.ip-address/bytes->ip-address (byte-array [192 168 0 1]))
                          :giaddr (r.ip-address/bytes->ip-address (byte-array [192 168 0 1]))
                          :chaddr (byte-array [1 2 3 4 5 6])
                          :sname ""
                          :file ""
                          :options [{:code 53, :type :dhcp-message-type, :length 1, :value [DHCPACK]}]})]
      (is (= {:dest-ip-addr (Inet4Address/getByAddress (byte-array [192 168 20 30]))
              :dest-mac-addr (th/byte-vec (byte-array [6 5 4 3 2 1]))
              :dest-port 68}
             (th/array->vec-recursively (#'sut/get-packet-info request reply-message)))))))
