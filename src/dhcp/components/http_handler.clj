(ns dhcp.components.http-handler
  (:require
   [com.stuartsierra.component :as component]
   [dhcp.http-handler :as h]
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
                  :parameters {:path [:map
                                      [:lease-id {:json-schema/example 10}
                                       int?]]
                               :query [:map
                                       [:limit {:optional true}
                                        pos-int?]
                                       [:offset {:optional true}
                                        pos-int?]]}
                  :responses {200 {:body [:map]}}
                  :handler handler}}]
       ["/:lease-id" {:get {:name :get-lease
                            :summary "get lease by id"
                            :parameters {:path [:map
                                                [:lease-id {:json-schema/example 10}
                                                 int?]]}
                            :responses {200 {:body [:map]}
                                        404 {:body [:map]}}
                            :handler handler}}]]
      ["/reservation" {:tags #{"reservation"}}
       ["" {:get {:handler handler}
            :post {:handler handler}}]
       ["/:id" {:get {:handler handler}
                :put {:handler handler}
                :delete {:handler handler}}]]]]

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
                         exception/exception-middleware
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
