(ns dhcp.http-handler.lease
  (:require
   [dhcp.http-handler :as h]
   [dhcp.protocol.database :as p.db]
   [dhcp.records.ip-address :as r.ip-address]
   [dhcp.util.bytes :as u.bytes]))

(defn- format-lease [lease]
  (-> lease
      (update :client-id u.bytes/->colon-str)
      (update :hw-address u.bytes/->colon-str)
      (update :ip-address (comp str r.ip-address/bytes->ip-address))))

(def LeaseJsonSchema
  [:map {:closed true}
   [:id pos-int?]
   [:client-id string?]
   [:hw-address string?]
   [:ip-address string?]
   [:hostname string?]
   [:lease-time pos-int?]
   [:status [:enum "offer" "lease"]]
   [:offered-at :time/instant]
   [:leased-at [:maybe :time/instant]]
   [:expired-at :time/instant]])

(defmethod h/handler :get-leases
  [{:keys [:db]}]
  ;; TODO: use limit and offset
  {:status 200
   :body {:leases (map format-lease (p.db/get-all-leases db))}})

(defmethod h/handler :get-lease
  [{:keys [:db :parameters]}]
  (let [{:keys [:lease-id]} (:path parameters)]
    (if-let [lease (p.db/find-lease-by-id db lease-id)]
      {:status 200
       :body (format-lease lease)}
      {:status 404
       :body {:error "lease not found"}})))
