(ns dhcp.records.dhcp-packet
  (:require
   [dhcp.records.dhcp-message])
  (:import
   (dhcp.records.dhcp_message
    DhcpMessage)
   (java.net
    Inet4Address)))

(defrecord DhcpPacket [^bytes local-hw-address
                       ^bytes destination-hw-address
                       ^bytes remote-hw-address
                       ^Inet4Address local-ip-address
                       ^DhcpMessage message])
