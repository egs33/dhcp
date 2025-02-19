(ns dhcp.components.http-handler
  (:require
   [clojure.tools.logging :as log]
   [com.stuartsierra.component :as component]
   [dhcp.http-handler :as h]
   [dhcp.http-handler.lease :as h.lease]
   [dhcp.http-handler.metadata]
   [dhcp.http-handler.reservation :as h.reservation]
   [malli.util :as mu]
   [muuntaja.core :as m]
   [reitit.coercion.malli]
   [reitit.dev.pretty :as pretty]
   [reitit.openapi :as openapi]
   [reitit.ring :as ring]
   [reitit.ring.coercion :as coercion]
   [reitit.ring.malli]
   [reitit.ring.middleware.exception :as exception]
   [reitit.ring.middleware.muuntaja :as muuntaja]
   [reitit.ring.middleware.parameters :as parameters]
   [reitit.ring.spec :as spec]
   [reitit.swagger-ui :as swagger-ui])
  (:import
   (dhcp.protocol.database
    IDatabase)))

(defn- wrap-states [handler db]
  (fn [request]
    (handler (assoc request :db db))))

(defn- handler [req]
  (h/handler req))

(def CommonError
  [:map {:closed true}
   [:error string?]])

(def exception-middleware
  (exception/create-exception-middleware
   (merge
    exception/default-handlers
    {::exception/wrap (fn [handler e request]
                        (log/errorf "%s" e)
                        (handler e request))})))

(defn make-handler [^IDatabase db]
  (ring/ring-handler
   (ring/router
    [["/openapi.json"
      {:get {:no-doc true
             :openapi {:info {:title "dhcp rest api"
                              :description "rest api for dhcp"
                              :version "0.0.1"}}
             :handler (openapi/create-openapi-handler)}}]
     ["/api/v1"
      ["/lease" {:tags #{"lease"}}
       ["" {:get {:name :get-leases
                  :summary "get all leases"
                  :parameters {:query [:map
                                       [:limit {:optional true}
                                        pos-int?]
                                       [:offset {:optional true}
                                        pos-int?]]}
                  :responses {200 {:body [:map
                                          [:leases [:sequential h.lease/LeaseJsonSchema]]]}}
                  :handler handler}}]
       ["/:lease-id" {:get {:name :get-lease
                            :summary "get lease by id"
                            :parameters {:path [:map
                                                [:lease-id {:json-schema/example 10}
                                                 int?]]}
                            :responses {200 {:body h.lease/LeaseJsonSchema}
                                        404 {:body CommonError}}
                            :handler handler}}]]
      ["/reservation" {:tags #{"reservation"}}
       ["" {:get {:name :get-reservations
                  :summary "get all reservations"
                  :parameters {:query [:map
                                       [:limit {:optional true}
                                        pos-int?]
                                       [:offset {:optional true}
                                        pos-int?]]}
                  :responses {200 {:body [:map
                                          [:reservations [:sequential h.reservation/ReservationJsonSchema]]]}}
                  :handler handler}
            :post {:name :add-reservation
                   :summary "add reservation"
                   :parameters {:body (-> h.reservation/ReservationJsonSchema
                                          (mu/dissoc :source)
                                          (mu/dissoc :id))}
                   :responses {201 {:body h.reservation/ReservationJsonSchema}}
                   :handler handler}}]
       ["/:id" {:parameters {:path [:map
                                    [:id pos-int?]]}
                :get {:name :get-reservation-by-id
                      :summary "get a reservation by id"
                      :responses {200 {:body h.reservation/ReservationJsonSchema}
                                  404 {:body CommonError}}
                      :handler handler}
                :put {:name :edit-reservation
                      :summary "edit reservation"
                      :parameters {:body (-> h.reservation/ReservationJsonSchema
                                             (mu/dissoc :source)
                                             (mu/dissoc :id))}
                      :responses {200 {:body h.reservation/ReservationJsonSchema}
                                  404 {:body CommonError}
                                  409 {:body CommonError}}
                      :handler handler}
                :delete {:name :delete-reservation
                         :summary "delete reservation"
                         :responses {204 {:description "delete success"}
                                     404 {:body CommonError}}
                         :handler handler}}]]
      ["/metadata" {:tags #{"metadata"}}
       ["/server-version" {:get {:name :get-server-version
                                 :summary "get server version"
                                 :responses {200 {:body [:map
                                                         [:version string?]]}}
                                 :handler handler}}]
       ["/webhook-event-schema" {:get {:name :get-webhook-event-schema
                                       :summary "get webhook event json schema"
                                       :responses {200 {:body [:map {:closed false}]}}
                                       :handler handler}}]]]]

    {:validate spec/validate ; enable spec validation for route data
     :exception pretty/exception
     :data {:coercion (reitit.coercion.malli/create
                       {:error-keys #{:type :coercion :in :schema :value :errors :humanized :transformed}
                        :compile mu/closed-schema
                        :strip-extra-keys true
                        :default-values true
                        :options nil})
            :muuntaja (m/create (-> m/default-options
                                    (update :formats select-keys ["application/json"])))
            :middleware [openapi/openapi-feature
                         parameters/parameters-middleware
                         muuntaja/format-negotiate-middleware
                         muuntaja/format-response-middleware
                         exception-middleware
                         muuntaja/format-request-middleware
                         coercion/coerce-response-middleware
                         coercion/coerce-request-middleware
                         [wrap-states db]]}})
   (ring/routes
    (swagger-ui/create-swagger-ui-handler
     {:path "/"
      :config {:validatorUrl nil
               :urls [{:name "openapi" :url "openapi.json"}]
               :urls.primaryName "openapi"
               :operationsSorter "alpha"}})
    (ring/create-default-handler))))

(defrecord HttpHandler [handler db]
  component/Lifecycle
  (start [this]
    (assoc this :handler (make-handler db)))
  (stop [this]
    (assoc this :handler nil)))
