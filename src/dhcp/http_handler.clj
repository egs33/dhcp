(ns dhcp.http-handler
  (:require
   [clojure.tools.logging :as log]))

(defmulti handler
  (fn [{:as req :keys [:request-method]}]
    (let [match (:reitit.core/match req)]
      (or (get-in match [:data :name])
          (get-in match [:data request-method :name])))))

(defmethod handler nil
  [req]
  (log/errorf "no name route req:%s" req)
  {:status 500
   :headers {}
   :body ""})

(defmethod handler :default
  [req]
  (log/error "no handler req:%s" req)
  {:status 500
   :headers {}
   :body ""})
