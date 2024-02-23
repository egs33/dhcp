(ns dhcp.records.dhcp-message-test
  (:require
   [clojure.test :refer [deftest testing is]]
   [dhcp.records.dhcp-message :as r.dhcp-message]
   [dhcp.records.ip-address :as r.ip-address])
  (:import
   (java.net
    DatagramPacket)))

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
                                  (int (count data)))]
      (is (= (r.dhcp-message/map->DhcpMessage
              {:op :BOOTREQUEST
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
               :options [{:code 53, :type :dhcp-message-type, :length 3, :value [1]}
                         {:code 0, :type :pad, :length 1, :value []}]})
             (r.dhcp-message/parse-message packet))))))
