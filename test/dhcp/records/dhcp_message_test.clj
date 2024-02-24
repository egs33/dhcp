(ns dhcp.records.dhcp-message-test
  (:require
   [clojure.test :refer [deftest testing is]]
   [dhcp.records.dhcp-message :as r.dhcp-message]
   [dhcp.records.ip-address :as r.ip-address])
  (:import
   (java.net
    DatagramPacket
    InetAddress)))

(deftest parse-message-test
  (testing "DHCPDISCOVER"
    (let [data (concat [1 1 6 0
                        8 16 54 92
                        0 0 0 0
                        0 0 0 0
                        0 0 0 0
                        0 0 0 0
                        0 0 0 0
                        11 22 33 44
                        55 66 0 0
                        0 0 0 0
                        0 0 0 0]
                       (repeat 64 0)
                       (repeat 128 0)
                       [99 130 83 99]
                       [53 1 1] [0])
          packet (DatagramPacket. (->> (concat data (repeat 0))
                                       (take 1024)
                                       byte-array)
                                  0
                                  (int (count data)))
          ip-addr (InetAddress/getByAddress ^bytes (byte-array [192 168 0 1]))]
      (is (= (r.dhcp-message/map->DhcpMessage
              {:local-address ip-addr
               :op :BOOTREQUEST
               :htype (byte 1)
               :hlen (byte 6)
               :hops (byte 0)
               :xid 135280220
               :secs 0
               :flags 0
               :ciaddr (r.ip-address/->IpAddress 0)
               :yiaddr (r.ip-address/->IpAddress 0)
               :siaddr (r.ip-address/->IpAddress 0)
               :giaddr (r.ip-address/->IpAddress 0)
               :chaddr [11 22 33 44
                        55 66 0 0
                        0 0 0 0
                        0 0 0 0]
               :sname ""
               :file ""
               :options [{:code 53, :type :dhcp-message-type, :length 1, :value [1]}
                         {:code 0, :type :pad, :length 0, :value []}]})
             (r.dhcp-message/parse-message ip-addr packet))))))

(deftest ->bytes-test
  (testing "DHCPDISCOVER"
    (let [expected (concat [1 1 6 0
                            8 16 54 92
                            0 0 0 0
                            0 0 0 0
                            0 0 0 0
                            0 0 0 0
                            0 0 0 0
                            11 22 33 44
                            55 66 0 0
                            0 0 0 0
                            0 0 0 0]
                           (repeat 64 0)
                           (repeat 128 0)
                           [99 130 83 99]
                           [53 1 1] [0])]
      (is (= expected
             (map #(Byte/toUnsignedInt %)
                  (r.dhcp-message/->bytes (r.dhcp-message/map->DhcpMessage
                                           {:local-address nil
                                            :op :BOOTREQUEST
                                            :htype (byte 1)
                                            :hlen (byte 6)
                                            :hops (byte 0)
                                            :xid 135280220
                                            :secs 0
                                            :flags 0
                                            :ciaddr (r.ip-address/->IpAddress 0)
                                            :yiaddr (r.ip-address/->IpAddress 0)
                                            :siaddr (r.ip-address/->IpAddress 0)
                                            :giaddr (r.ip-address/->IpAddress 0)
                                            :chaddr [11 22 33 44
                                                     55 66 0 0
                                                     0 0 0 0
                                                     0 0 0 0]
                                            :sname ""
                                            :file ""
                                            :options [{:code 53, :type :dhcp-message-type, :length 1, :value [1]}
                                                      {:code 0, :type :pad, :length 0, :value []}]}))))))))
