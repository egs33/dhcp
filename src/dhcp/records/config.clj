(ns dhcp.records.config
  (:require
   [clj-yaml.core :as yaml]
   [clojure.string :as str]
   [clojure.tools.logging :as log]
   [dhcp.records.ip-address :as r.ip-address]
   [dhcp.util.bytes :as u.bytes]
   [malli.core :as m]
   [malli.error :as me])
  (:import
   (clojure.lang
    ExceptionInfo)
   (java.net
    Inet4Address)))

(defn- normalize-reservations
  [{:keys [:s-idx :p-idx :pool-start :pool-end]}
   idx
   {:keys [:hw-address :ip-address]}]
  (let [ip-addr (r.ip-address/str->ip-address ip-address)]
    (when (or (< (r.ip-address/->int ip-addr)
                 (r.ip-address/->int pool-start))
              (> (r.ip-address/->int ip-addr)
                 (r.ip-address/->int pool-end)))
      (throw (ex-info "config error"
                      {:message (format "subnets[%d].pools[%d].reservation[%d].ipAddress: not in pool"
                                        s-idx
                                        p-idx
                                        idx)})))
    {:hw-address (-> hw-address
                     (str/split #":")
                     (->> (mapv #(-> (Integer/parseInt % 16)
                                     unchecked-byte))))
     :ip-address ip-addr}))

(defn- normalize-pools [{:keys [:s-idx :lease-time :start-addr :end-addr :subnet-options]} idx pool]
  (let [lease-time (or (:lease-time pool) lease-time)
        pool-start (r.ip-address/str->ip-address (:start-address pool))
        pool-end (r.ip-address/str->ip-address (:end-address pool))
        reservation (map-indexed (partial normalize-reservations {:s-idx s-idx
                                                                  :p-idx idx
                                                                  :pool-start pool-start
                                                                  :pool-end pool-end})
                                 (:reservation pool))]
    (when (or (< (r.ip-address/->int pool-start)
                 (r.ip-address/->int start-addr))
              (> (r.ip-address/->int pool-start)
                 (r.ip-address/->int end-addr)))
      (throw (ex-info "config error"
                      {:message (format "subnets[%d].pools[%d].start-address: not in network"
                                        s-idx
                                        idx)})))
    (when (or (< (r.ip-address/->int pool-end)
                 (r.ip-address/->int start-addr))
              (> (r.ip-address/->int pool-end)
                 (r.ip-address/->int end-addr)))
      (throw (ex-info "config error"
                      {:message (format "subnets[%d].pools[%d].end-address: not in network"
                                        s-idx
                                        idx)})))
    (when (> (r.ip-address/->int start-addr) (r.ip-address/->int end-addr))
      (throw (ex-info "config error"
                      {:message (format "subnets[%d].pools[%d].end-address: must be greater than start-address"
                                        s-idx
                                        idx)})))
    {:start-address pool-start
     :end-address pool-end
     :only-reserved-lease (boolean (:only-reserved-lease pool))
     :lease-time lease-time
     :reservation (vec reservation)
     :options (->> (concat subnet-options (:options pool))
                   (mapv (fn [option]
                           (if (string? (:value option))
                             {:code (:code option)
                              :length (/ (count (:value option)) 2)
                              :value (->> (partition 2 (:value option))
                                          (map #(-> (apply str %)
                                                    (Integer/parseInt 16)
                                                    unchecked-byte)))}
                             option))))}))

(defn- normalize-subnet [root-lease-time idx {:keys [:cidr :router :dns :lease-time :options :pools]}]
  (let [[network-addr bits] (str/split cidr #"/")
        network-addr (r.ip-address/str->ip-address network-addr)
        subnet-mask (- (bit-shift-left 1 32)
                       (bit-shift-left 1 (- 32 (parse-long bits))))
        start-addr (bit-and (r.ip-address/->int network-addr)
                            subnet-mask)
        end-addr (-> (bit-or start-addr
                             (dec (bit-shift-left 1 (- 32 (parse-long bits)))))
                     r.ip-address/->IpAddress)
        start-addr (r.ip-address/->IpAddress start-addr)
        subnet-option {:code 1, :type :subnet-mask, :length 4, :value (u.bytes/number->byte-coll subnet-mask 4)}
        router-option {:code 3, :type :router, :length 4, :value (-> router
                                                                     r.ip-address/str->ip-address
                                                                     r.ip-address/->bytes)}
        dns-option {:code 6
                    :type :domain-server
                    :length (* 4 (count dns))
                    :value (->> dns
                                (map (comp r.ip-address/->bytes
                                           r.ip-address/str->ip-address))
                                (apply concat))}
        pools (map-indexed (partial normalize-pools {:s-idx idx
                                                     :lease-time (or lease-time root-lease-time)
                                                     :start-addr start-addr
                                                     :end-addr end-addr
                                                     :subnet-options (concat [subnet-option router-option dns-option]
                                                                             options)})
                           pools)]
    {:start-address start-addr
     :end-address end-addr
     :pools (vec pools)}))

(defn- normalize-config [config]
  (let [root-lease-time (:lease-time config)
        subnets (map-indexed (partial normalize-subnet root-lease-time)
                             (:subnets config))]
    {:interfaces (:interfaces config)
     :subnets (vec subnets)
     :database (:database config)}))

(def ^:private CidrSchema
  [:and
   [:string {:min 1, :error/message "must be a not empty string"}]
   [:fn {:error/message "invalid CIDR"}
    (fn [s]
      (let [[_ p1 p2 p3 p4 bits] (re-matches #"(\d{1,3})\.(\d{1,3})\.(\d{1,3})\.(\d{1,3})/(\d{1,2})" s)]
        (and (<= 0 (parse-long p1) 255)
             (<= 0 (parse-long p2) 255)
             (<= 0 (parse-long p3) 255)
             (<= 0 (parse-long p4) 255)
             (<= 0 (parse-long bits) 32))))]])

(def ^:private IpAddressSchema
  [:and
   [:string {:min 1, :error/message "must be a not empty string"}]
   [:fn {:error/message "invalid IP address"}
    (fn [s]
      (let [parts (str/split s #"\.")]
        (and (= 4 (count parts))
             (every? #(<= 0 (parse-long %) 255)
                     parts))))]])

(def ^:private OptionSchema
  [:map
   [:code [:and
           pos-int?
           [:fn (fn [code]
                  (<= 0 code 255))]]]
   [:value [:and
            [:string {:error/message "must be a string"}]
            [:re #"^([0-9a-fA-F]{2})*$"]]]])

(def ConfigFileSchema
  [:map {:closed true}
   [:interfaces {:optional true} [:maybe [:sequential [:string {:min 1, :error/message "must be a not empty string"}]]]]
   [:lease-time pos-int?]
   [:subnets [:sequential
              [:map
               [:cidr CidrSchema]
               [:router IpAddressSchema]
               [:dns [:sequential {:min 1, :error/message "must be an array of string"}
                      IpAddressSchema]]
               [:lease-time {:optional true} pos-int?]
               [:options {:optional true} [:sequential
                                           OptionSchema]]
               [:pools [:sequential
                        [:map
                         [:start-address IpAddressSchema]
                         [:end-address IpAddressSchema]
                         [:only-reserved-lease {:optional true} boolean?]
                         [:lease-time {:optional true} pos-int?]
                         [:reservation {:optional true} [:sequential
                                                         [:map
                                                          [:hw-address
                                                           [:re #"^[0-9a-fA-F]{2}(:[0-9a-fA-F]{2})*$"]]
                                                          [:ip-address
                                                           IpAddressSchema]]]]
                         [:options {:optional true} [:sequential
                                                     OptionSchema]]]]]]]]
   [:database {:error/message "must be object"}
    [:and
     [:fn {:error/message "postgres-option is required for type \"postgresql\""}
      (fn [db]
        (or (not= "postgresql" (:type db))
            (map? (:postgresql-option db))))]
     [:map {:closed true}
      [:type [:enum "memory" "postgresql"]]
      [:postgresql-option {:optional true}
       [:map {:closed true}
        [:jdbc-url {:optional true} :string]
        [:username {:optional true} :string]
        [:password {:optional true} :string]
        [:database-name {:optional true} :string]
        [:server-name {:optional true} :string]
        [:port-number {:optional true} pos-int?]]]]]]])

(defn- flat-error
  [error]
  (reduce-kv (fn [acc k v]
               (let [key (if (keyword? k)
                           (str/replace (name k) #"-." #(str/upper-case (subs % 1)))
                           (str "[" k "]"))]
                 (cond
                   (map? v)
                   (->> (flat-error v)
                        (map #(str key "." %))
                        (concat acc))

                   (sequential? v)
                   (->> v
                        (map-indexed (fn [idx e] {:value e :index idx}))
                        (mapcat (fn [{:keys [:value :index]}]
                                  (cond
                                    (string? value)
                                    [(str key ": " value)]

                                    (map? value)
                                    (->> (flat-error value)
                                         (map #(str key "[" index "]." %)))

                                    (and (vector? value)
                                         (string? (first value)))
                                    (->> value
                                         (map #(str key "[" index "]:" %)))

                                    (vector? value)
                                    (->> (flat-error value)
                                         (map #(str key "[" index "]." %))))))
                        (concat acc))

                   :else
                   (concat acc [(str key ": unknown error")]))))
             []
             error))

(defprotocol IConfig
  (select-subnet [this ^Inet4Address ip-address]))

(defrecord Config [config]
  IConfig
  (select-subnet [_ ip-address]
    (let [ip-addr (u.bytes/bytes->number (.getAddress ip-address))]
      (->> (:subnets config)
           (filter (fn [{:keys [start-address end-address]}]
                     (<= (r.ip-address/->int start-address)
                         ip-addr
                         (r.ip-address/->int end-address))))
           first))))

(defn load-config [^String path]
  (let [yml-str (slurp path)
        data (yaml/parse-string yml-str :key-fn (fn [k]
                                                  (-> k
                                                      :key
                                                      (str/replace #"[A-Z]" #(str "-" (str/lower-case (str %))))
                                                      keyword)))]
    (if (not (map? data))
      (println "failed to parse config")
      (if-let [err (me/humanize (m/explain ConfigFileSchema data))]
        (let [err-msgs (flat-error err)]
          (log/error "config load error" {:errors err-msgs})
          (doseq [m err-msgs]
            (println m)))
        (try
          (->Config (normalize-config data))
          (catch ExceptionInfo ex
            (println (:message (ex-data ex)))
            (log/error "config load error" {:errors (ex-data ex)}))
          (catch Exception ex
            (log/error "config load error" {:error ex})))))))
