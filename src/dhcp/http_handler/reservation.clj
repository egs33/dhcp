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
   [:id {:json-schema/example 1}
    pos-int?]
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

(defmethod h/handler :get-reservation-by-id
  [{:keys [:db :parameters]}]
  (if-let [reservation (p.db/find-reservation-by-id db (get-in parameters [:path :id]))]
    {:status 200
     :body (format-reservation reservation)}
    {:status 404
     :body {:error "reservation not found"}}))

(defmethod h/handler :edit-reservation
  [{:keys [:db :parameters]}]
  (if-let [reservation (p.db/find-reservation-by-id db (get-in parameters [:path :id]))]
    (if (= (:source reservation) "config")
      {:status 409
       :body {:error "reservation is from config, cannot be edited"}}
      (p.db/transaction db
                        (fn [db]
                          ;; TODO fix preserve id
                          (p.db/delete-reservation-by-id db (:id reservation))
                          (let [[r] (p.db/add-reservations db [(assoc (:body parameters) :source "api")])]
                            {:status 200
                             :body (format-reservation r)}))))
    {:status 404
     :body {:error "reservation not found"}}))

(defmethod h/handler :delete-reservation
  [{:keys [:db :parameters]}]
  (if-let [reservation (p.db/find-reservation-by-id db (get-in parameters [:path :id]))]
    (do
      (p.db/delete-reservation-by-id db (:id reservation))
      {:status 204
       :body nil})
    {:status 404
     :body {:error "reservation not found"}}))
