(ns dhcp.core.option
  (:import
   (clojure.lang
    IPersistentVector)))

(def ^:private option-code-map
  {0 :pad
   255 :end
   1 :subnet-mask
   2 :time-offset
   3 :router
   4 :time-server
   5 :name-server
   6 :domain-server
   7 :log-server
   8 :quotes-server
   9 :lpr-server
   10 :impress-server
   11 :rlp-server
   12 :hostname
   13 :boot-file-size
   14 :merit-dump-file
   15 :domain-name
   16 :swap-server
   17 :root-path
   18 :extension-file
   19 :forward-on-off
   20 :srcrte-on-off
   21 :policy-filter
   22 :max-dg-assembly
   23 :default-ip-ttl
   24 :mtu-timeout
   25 :mtu-plateau
   26 :mtu-interface
   27 :mtu-subnet
   28 :broadcast-address
   29 :mask-discovery
   30 :mask-supplier
   31 :router-discovery
   32 :router-request
   33 :static-route
   34 :trailers
   35 :arp-timeout
   36 :ethernet
   37 :default-tcp-ttl
   38 :keepalive-time
   39 :keepalive-data
   40 :nis-domain
   41 :nis-servers
   42 :ntp-servers
   43 :vendor-specific
   44 :netbios-name-srv
   45 :netbios-dist-srv
   46 :netbios-node-type
   47 :netbios-scope
   48 :x-window-font
   49 :x-window-manager
   50 :requested-ip-address
   51 :address-time
   52 :overload
   53 :dhcp-message-type
   54 :dhcp-server-id
   55 :parameter-list
   56 :dhcp-message
   57 :dhcp-max-msg-size
   58 :renewal-time
   59 :rebinding-time
   60 :class-id
   61 :client-identifier
   62 :netware-ip-domain
   63 :netware-ip-option
   64 :nis-domain-name
   65 :nis-server-addr
   66 :server-name
   67 :bootfile-name
   68 :home-agent-addrs
   69 :smtp-server
   70 :pop3-server
   71 :nntp-server
   72 :www-server
   73 :finger-server
   74 :irc-server
   75 :streettalk-server
   76 :stda-server
   77 :user-class
   78 :directory-agent
   79 :service-scope
   80 :rapid-commit
   81 :client-fqdn
   82 :relay-agent-information
   83 :isns
   84 :removed-unassigned
   85 :nds-servers
   86 :nds-tree-name
   87 :nds-context
   88 :bcmcs-controller-domain-name-list
   89 :bcmcs-controller-ipv4-address-option
   90 :authentication
   91 :client-last-transaction-time-option
   92 :associated-ip-option
   93 :client-system
   94 :client-ndi
   95 :ldap
   96 :removed-unassigned
   97 :uuid-guid
   98 :user-auth
   99 :geoconf-civic
   100 :pcode
   101 :tcode
   108 :ipv6-only-preferred
   109 :option-dhcp4o6-s46-saddr
   110 :removed-unassigned
   112 :netinfo-address
   113 :netinfo-tag
   114 :dhcp-captive-portal
   115 :removed-unassigned
   116 :auto-config
   117 :name-service-search
   118 :subnet-selection-option
   119 :domain-search
   120 :sip-servers-dhcp-option
   121 :classless-static-route-option
   122 :ccc
   123 :geoconf-option
   124 :v-i-vendor-class
   125 :v-i-vendor-specific-information
   136 :option-pana-agent
   137 :option-v4-lost
   138 :option-capwap-ac-v4
   139 :option-ipv4-address-mos
   140 :option-ipv4-fqdn-mos
   141 :sip-ua-configuration-service-domains
   142 :option-ipv4-address-andsf
   143 :option-v4-sztp-redirect
   144 :geoloc
   145 :forcerenew-nonce-capable
   146 :rdnss-selection
   147 :option-v4-dots-ri
   148 :option-v4-dots-address
   150 :tftp-server-address
   151 :status-code
   152 :base-time
   153 :start-time-of-state
   154 :query-start-time
   155 :query-end-time
   156 :dhcp-state
   157 :data-source
   158 :option-v4-pcp-server
   159 :option-v4-portparams
   161 :option-mud-url-v4
   162 :option-v4-dnr
   209 :configuration-file
   210 :path-prefix
   211 :reboot-time
   212 :option-6rd
   213 :option-v4-access-domain
   220 :subnet-allocation-option
   221 :virtual-subnet-selection-option})

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
  ;; TODO The client concatenates
  ;;   the values of multiple instances of the same option into a single
  ;;   parameter list for configuration.
  (loop [rest (vec bytes)
         options (transient [])]
    (if (seq rest)
      (let [{:as option :keys [:code :length]} (parse-option rest)]
        (recur (subvec rest (if (#{0 255} code)
                              1
                              (+ 2 length)))
               (conj! options option)))
      (persistent! options))))

(defn start-with-magic-cookie? [^bytes bytes]
  (and (= (nth bytes 0) 99)
       (= (nth bytes 1) -126)                                  ; = 130
       (= (nth bytes 2) 83)
       (= (nth bytes 3) 99)))

(defn options->bytes [options]
  (concat [99 130 83 99]
          (mapcat (fn [option]
                    (if (contains? #{0 255} (:code option))
                      [(:code option)]
                      (concat [(:code option)
                               (:length option)]
                              (:value option))))
                  options)))
