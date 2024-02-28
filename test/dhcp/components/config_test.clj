(ns dhcp.components.config-test
  (:require
   [clojure.java.io :as io]
   [clojure.string :as str]
   [clojure.test :refer [deftest testing is]]
   [dhcp.components.config :as c.config]
   [dhcp.records.ip-address :as r.ip-address]))

(deftest load-config-test
  (testing "load error"
    (testing "empty config"
      (with-redefs [slurp (constantly "")]
        (let [ret (atom nil)
              msg (with-out-str
                    (reset! ret (c.config/load-config "")))]
          (is (nil? @ret)
              "return nil")
          (is (= "failed to parse config\n" msg)
              "error message"))))
    (testing "some errors"
      (let [path1 (.getPath (io/resource "config/error-config1.yml"))
            ret (atom nil)
            msg (with-out-str
                  (reset! ret (c.config/load-config path1)))]
        (is (nil? @ret)
            "return nil")
        (is (= (str/join "\n"
                         ["leaseTime: should be a positive int"
                          "subnets[0].cidr: invalid CIDR"
                          "subnets[0].router: missing required key"
                          "subnets[0].dns: missing required key"
                          "subnets[0].pools: missing required key"
                          "subnets[2].router: invalid IP address"
                          "subnets[2].dns[0]:invalid IP address"
                          "subnets[2].pools[0]:invalid type"
                          "database.type: should be memory"
                          "foo: disallowed key\n"])
               msg)
            "error message")))
    (testing "Ip address range error"
      (let [path1 (.getPath (io/resource "config/error-config2.yml"))
            ret (atom nil)
            msg (with-out-str
                  (reset! ret (c.config/load-config path1)))]
        (is (nil? @ret)
            "return nil")
        (is (= "subnets[0].pools[0].start-address: not in network\n"
               msg)
            "error message"))))
  (testing "load success"
    (testing "minimal config"
      (let [path2 (.getPath (io/resource "config/minimal-config.yml"))
            config (c.config/load-config path2)]
        (is (= (c.config/->Config
                {:interfaces nil
                 :subnets [{:start-address (r.ip-address/str->ip-address "192.168.0.0")
                            :end-address (r.ip-address/str->ip-address "192.168.0.255")
                            :pools [{:start-address (r.ip-address/str->ip-address "192.168.0.50")
                                     :end-address (r.ip-address/str->ip-address "192.168.0.60")
                                     :only-reserved-lease false
                                     :lease-time 3600
                                     :reservation []
                                     :options [{:code 3, :type :router, :length 4, :value [192 168 0 1]}
                                               {:code 6, :type :domain-server, :length 4, :value [192 168 0 2]}]}]}],
                 :database {:type "memory"}})
               config)
            "return config is normalized")))
    (testing "complex config"
      (let [path2 (.getPath (io/resource "config/complex-config.yml"))
            config (c.config/load-config path2)]
        (is (= (c.config/->Config
                {:interfaces ["eth0" "eth1"]
                 :subnets [{:start-address (r.ip-address/str->ip-address "192.168.0.0")
                            :end-address (r.ip-address/str->ip-address "192.168.0.127")
                            :pools [{:start-address (r.ip-address/str->ip-address "192.168.0.50")
                                     :end-address (r.ip-address/str->ip-address "192.168.0.60")
                                     :only-reserved-lease true
                                     :lease-time 10800
                                     :reservation [{:hw-address [0 0 0 0 0 1], :ip-address (r.ip-address/str->ip-address "192.168.0.50")}
                                                   {:hw-address [0 0 0 0 0 2], :ip-address (r.ip-address/str->ip-address "192.168.0.60")}]
                                     :options [{:code 3, :type :router, :length 4, :value [192 168 0 1]}
                                               {:code 6, :type :domain-server, :length 4, :value [192 168 0 2]}
                                               {:code 190, :length 0, :value []}
                                               {:code 191, :length 3, :value [-1 -1 -1]}
                                               {:code 200, :length 5, :value [1 2 3 4 5]}
                                               {:code 201, :length 4, :value [10 11 -1 16]}]}
                                    {:start-address (r.ip-address/str->ip-address "192.168.0.70")
                                     :end-address (r.ip-address/str->ip-address "192.168.0.90")
                                     :only-reserved-lease false
                                     :lease-time 50000
                                     :reservation [{:hw-address [0 0 0 17 17 17], :ip-address (r.ip-address/str->ip-address "192.168.0.70")}
                                                   {:hw-address [0 0 0 34 34 34], :ip-address (r.ip-address/str->ip-address "192.168.0.72")}]
                                     :options [{:code 3, :type :router, :length 4, :value [192 168 0 1]}
                                               {:code 6, :type :domain-server, :length 4, :value [192 168 0 2]}
                                               {:code 190, :length 0, :value []}
                                               {:code 191, :length 3, :value [-1 -1 -1]}
                                               {:code 210, :length 0, :value []}
                                               {:code 211, :length 8, :value [17 17 17 17 17 17 17 17]}]}]}
                           {:start-address (r.ip-address/str->ip-address "172.16.0.0")
                            :end-address (r.ip-address/str->ip-address "172.16.255.255")
                            :pools [{:start-address (r.ip-address/str->ip-address "172.16.10.0")
                                     :end-address (r.ip-address/str->ip-address "172.16.20.0")
                                     :only-reserved-lease true
                                     :lease-time 10800
                                     :reservation [{:hw-address [0 0 0 -86 -86 -86], :ip-address (r.ip-address/str->ip-address "172.16.10.0")}
                                                   {:hw-address [0 0 0 -69 -69 -69], :ip-address (r.ip-address/str->ip-address "172.16.10.1")}]
                                     :options [{:code 3, :type :router, :length 4, :value [172 16 100 0]}
                                               {:code 6, :type :domain-server, :length 8, :value [172 16 100 1 172 16 100 2]}
                                               {:code 190, :length 0, :value []}
                                               {:code 230, :length 5, :value [1 2 3 4 5]}
                                               {:code 231, :length 4, :value [10 11 -1 16]}]}]}],
                 :database {:type "memory"}})
               config)
            "return config is normalized")))))
