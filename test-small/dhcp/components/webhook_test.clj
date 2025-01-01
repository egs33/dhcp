(ns dhcp.components.webhook-test
  (:require
   [clojure.test :refer [deftest testing is]]
   [com.stuartsierra.component :as component]
   [dhcp.components.webhook :as sut]
   [dhcp.protocol.webhook :as p.webhook]
   [jsonista.core :as j])
  (:import
   (java.net
    URI)
   (java.time
    Instant)))

(deftest Webhook-test
  (testing "nil events send all events"
    (testing "lease event"
      (let [component (-> (sut/map->Webhook {:events nil
                                             :url "http://localhost:8912"})
                          component/start)
            lease {:id 2
                   :client-id (byte-array [1 2 3])
                   :hw-address (byte-array [1 2 3])
                   :ip-address (byte-array [192 168 10 20])
                   :hostname "sample-host"
                   :lease-time 3600
                   :status "lease"
                   :offered-at (Instant/parse "2021-01-01T00:00:00Z")
                   :leased-at (Instant/parse "2021-02-01T00:00:00Z")
                   :expired-at (Instant/parse "2021-03-01T00:00:00Z")}
            arg-atom (atom nil)]
        (with-redefs [sut/send-webhook (fn [client
                                            uri
                                            event
                                            content]
                                         (reset! arg-atom {:client client
                                                           :uri uri
                                                           :event event
                                                           :content content}))]
          (p.webhook/send-lease component lease))
        (is (= (URI/create "http://localhost:8912")
               (:uri @arg-atom)))
        (is (= "lease"
               (:event @arg-atom)))
        (is (= {"client-id" "01:02:03",
                "event" "lease",
                "expired-at" "2021-03-01T00:00:00Z",
                "hostname" "sample-host",
                "hw-address" "01:02:03",
                "id" 2,
                "ip-address" "192.168.10.20",
                "lease-time" 3600,
                "leased-at" "2021-02-01T00:00:00Z",
                "offered-at" "2021-01-01T00:00:00Z",
                "status" "lease"}
               (j/read-value (:content @arg-atom)))))))
  (testing "target events set"
    (testing "lease event"
      (let [component (-> (sut/map->Webhook {:events ["lease"]
                                             :url "http://localhost:8912"})
                          component/start)
            lease {:id 2
                   :client-id (byte-array [1 2 3])
                   :hw-address (byte-array [1 2 3])
                   :ip-address (byte-array [192 168 10 20])
                   :hostname "sample-host"
                   :lease-time 3600
                   :status "lease"
                   :offered-at (Instant/parse "2021-01-01T00:00:00Z")
                   :leased-at (Instant/parse "2021-02-01T00:00:00Z")
                   :expired-at (Instant/parse "2021-03-01T00:00:00Z")}
            arg-atom (atom nil)]
        (with-redefs [sut/send-webhook (fn [client
                                            uri
                                            event
                                            content]
                                         (reset! arg-atom {:client client
                                                           :uri uri
                                                           :event event
                                                           :content content}))]
          (p.webhook/send-lease component lease))
        (is (= (URI/create "http://localhost:8912")
               (:uri @arg-atom)))
        (is (= "lease"
               (:event @arg-atom)))
        (is (= {"client-id" "01:02:03",
                "event" "lease",
                "expired-at" "2021-03-01T00:00:00Z",
                "hostname" "sample-host",
                "hw-address" "01:02:03",
                "id" 2,
                "ip-address" "192.168.10.20",
                "lease-time" 3600,
                "leased-at" "2021-02-01T00:00:00Z",
                "offered-at" "2021-01-01T00:00:00Z",
                "status" "lease"}
               (j/read-value (:content @arg-atom)))))))
  (testing "not target event"
    (testing "lease event"
      (let [component (-> (sut/map->Webhook {:events ["offer"]
                                             :url "http://localhost:8912"})
                          component/start)
            lease {:id 2
                   :client-id (byte-array [1 2 3])
                   :hw-address (byte-array [1 2 3])
                   :ip-address (byte-array [192 168 10 20])
                   :hostname "sample-host"
                   :lease-time 3600
                   :status "lease"
                   :offered-at (Instant/parse "2021-01-01T00:00:00Z")
                   :leased-at (Instant/parse "2021-02-01T00:00:00Z")
                   :expired-at (Instant/parse "2021-03-01T00:00:00Z")}]
        (with-redefs [sut/send-webhook (fn [& _]
                                         (throw (ex-info "not-called" {})))]
          (is (any? (p.webhook/send-lease component lease))))))))
