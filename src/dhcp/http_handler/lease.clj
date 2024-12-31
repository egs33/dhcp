(ns dhcp.http-handler.lease
  (:require
   [dhcp.core.lease :as c.lease]
   [dhcp.http-handler :as h]
   [dhcp.protocol.database :as p.db]
   [dhcp.util.schema :as us]))

(def LeaseJsonSchema
  [:map {:closed true}
   [:id pos-int?]
   [:client-id string?]
   [:hw-address string?]
   [:ip-address string?]
   [:hostname string?]
   [:lease-time pos-int?]
   [:status [:enum "offer" "lease"]]
   [:offered-at us/instant-json-schema :time/instant]
   [:leased-at {:json-schema/oneOf [{:type "string"} {:type "null"}]} [:maybe :time/instant]]
   [:expired-at us/instant-json-schema :time/instant]])

(defmethod h/handler :get-leases
  [{:keys [:db]}]
  ;; TODO: use limit and offset
  {:status 200
   :body {:leases (map c.lease/format-lease (p.db/get-all-leases db))}})

(defmethod h/handler :get-lease
  [{:keys [:db :parameters]}]
  (let [{:keys [:lease-id]} (:path parameters)]
    (if-let [lease (p.db/find-lease-by-id db lease-id)]
      {:status 200
       :body (c.lease/format-lease lease)}
      {:status 404
       :body {:error "lease not found"}})))
