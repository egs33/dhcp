(ns dhcp.http-handler.reservation
  (:require
   [clojure.string :as str]
   [clojure.tools.logging :as log]
   [dhcp.http-handler :as h]
   [dhcp.protocol.database :as p.db]
   [dhcp.records.ip-address :as r.ip-address]
   [dhcp.util.bytes :as u.bytes]))

(defn- format-reservation [reservation]
  (-> reservation
      (update :hw-address u.bytes/->colon-str)
      (update :ip-address (comp str r.ip-address/bytes->ip-address))))

(def ^:private IpAddressSchema
  [:and
   [:string {:min 1, :error/message "must be a not empty string"}]
   [:fn {:error/message "invalid IP address"}
    (fn [s]
      (let [parts (str/split s #"\.")]
        (and (= 4 (count parts))
             (every? #(<= 0 (parse-long %) 255)
                     parts))))]])

(def ReservationJsonSchema
  [:map {:closed true}
   [:hw-address {:json-schema/example "00:00:00:00:00:00"}
    [:and
     string?
     [:re #"^([0-9a-fA-F]{2}:?)*$"]]]
   [:ip-address {:json-schema/example "192.168.0.1"}
    IpAddressSchema]
   [:source [:enum "config" "api"]]])

(defmethod h/handler :get-reservations
  [{:keys [:db]}]
  ;; TODO: use limit and offset
  {:status 200
   :body {:reservations (map format-reservation (p.db/get-all-reservations db))}})

(defmethod h/handler :add-reservation
  [{:keys [:db :parameters]}]
  (try (let [reservation (-> (:body parameters)
                             (update :hw-address u.bytes/parse)
                             (update :ip-address (comp r.ip-address/->byte-array r.ip-address/str->ip-address))
                             (assoc :source "api"))]
         (p.db/add-reservations db [reservation])
         {:status 201
          :body (format-reservation reservation)})
       (catch Exception e
         (log/error e))))
