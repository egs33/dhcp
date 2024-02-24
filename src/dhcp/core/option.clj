(ns dhcp.core.option
  (:require
   [clojure.tools.logging :as log])
  (:import
   (clojure.lang
    IPersistentVector)))

(def ^:private option-code-map
  "RFC2132"
  {0 :pad
   255 :end
   1 :subnet-mask
   2 :time-offset
   3 :router-option
   4 :time-server-option
   5 :name-server-option
   6 :domain-name-server-option
   7 :log-server-option
   8 :cookie-server-option
   9 :lpr-server-option
   10 :impress-server-option
   11 :resource-location-server-option
   12 :host-name-option
   13 :boot-file-size-option
   14 :merit-dump-file
   15 :domain-name
   16 :swap-server
   17 :root-path
   18 :extensions-path
   19 :ip-forwarding-enable-disable-option
   20 :non-local-source-routing-enable-disable-option
   21 :policy-filter-option
   22 :max-datagram-reassembly-size
   23 :default-ip-time-to-live
   24 :path-mtu-aging-timeout-option
   25 :path-mtu-plateau-table-option
   26 :interface-mtu-option
   27 :all-subnets-local-option
   28 :broadcast-address-option
   29 :perform-mask-discovery-option
   30 :mask-supplier-option
   31 :perform-router-discovery-option
   32 :router-solicitation-address-option
   33 :static-route-option
   34 :trailer-encapsulation-option
   35 :arp-cache-timeout-option
   36 :ethernet-encapsulation-option
   37 :tcp-default-ttl-option
   38 :tcp-keepalive-interval-option
   39 :tcp-keepalive-garbage-option
   40 :network-information-service-domain-option
   41 :network-information-servers-option
   42 :network-time-protocol-servers-option
   43 :vendor-specific-information
   44 :netbios-over-tcp-ip-name-server-option
   45 :netbios-over-tcp-ip-datagram-distribution-server-option
   46 :netbios-over-tcp-ip-node-type-option
   47 :netbios-over-tcp-ip-scope-option
   48 :x-window-system-font-server-option
   49 :x-window-system-display-manager-option
   50 :requested-ip-address
   51 :ip-address-lease-time
   52 :option-overload
   53 :dhcp-message-type
   54 :server-identifier
   55 :parameter-request-list
   56 :message
   57 :maximum-dhcp-message-size
   58 :renewal-time-value
   59 :rebinding-time-value
   60 :vendor-class-identifier
   61 :client-identifier
   64 :network-information-service-plus-domain-option
   65 :network-information-service-plus-servers-option
   66 :tftp-server-name-option
   67 :bootfile-name-option
   68 :mobile-ip-home-agent-option
   69 :simple-mail-transport-protocol-server-option
   70 :post-office-protocol-server-option
   71 :network-news-transport-protocol-server-option
   72 :default-world-wide-web-server-option
   73 :default-finger-server-option
   74 :default-internet-relay-chat-server-option
   75 :streettalk-server-option
   76 :streettalk-directory-assistance-server-option})

(defn- parse-option
  [^IPersistentVector bytes]
  (let [code (Byte/toUnsignedInt (first bytes))]
    (case (Byte/toUnsignedInt (first bytes))
      0 {:code 0, :type :pad, :length 0, :value []}
      255 {:code 255, :type :end, :length 0, :value []}
      (let [len (Byte/toUnsignedInt (nth bytes 1))]
        {:code code
         :type (get option-code-map code :unknown)
         :length len
         :value (subvec bytes 2 (+ 2 len))}))))

(defn parse-options [^bytes bytes]
  (let [[b1 b2 b3 b4 & rest] (seq bytes)]
    (if (and (= b1 99)
             (= b2 -126)                                  ; = 130
             (= b3 83)
             (= b4 99))
      (loop [rest (vec rest)
             options (transient [])]
        (if (seq rest)
          (let [{:as option :keys [:code :length]} (parse-option rest)]
            (recur (subvec rest (if (#{0 255} code)
                                  1
                                  (+ 2 length)))
                   (conj! options option)))
          (persistent! options)))
      (log/warn "magic cookie not found" {:4octets [b1 b2 b3 b4]
                                          :len (count bytes)}))))

(defn options->bytes [options]
  (concat [99 130 83 99]
          (mapcat (fn [option]
                    (if (contains? #{0 255} (:code option))
                      [(:code option)]
                      (concat [(:code option)
                               (:length option)]
                              (:value option))))
                  options)))
