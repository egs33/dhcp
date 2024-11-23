(ns dhcp.components.database.postgres
  (:require
   [camel-snake-kebab.core :as csk]
   [clojure.tools.logging :as log]
   [com.stuartsierra.component :as component]
   [dhcp.components.database.common :as db.common]
   [dhcp.protocol.database :as p.db]
   [hikari-cp.core :as hc]
   [honey.sql :as honey]
   [honey.sql.helpers :as sql]
   [migratus.core :as mig]
   [next.jdbc :as jdbc]
   [next.jdbc.prepare :as next.prepare]
   [next.jdbc.protocols :as jdbc.proto]
   [next.jdbc.result-set :as jdbc.rs])
  (:import
   (java.sql
    Date
    PreparedStatement
    Time
    Timestamp)
   (java.time
    Instant
    LocalDate)))

(defn- rs-builder
  [rs opts]
  (jdbc.rs/as-unqualified-modified-maps rs (assoc opts :label-fn csk/->kebab-case-keyword)))

(extend-protocol jdbc.rs/ReadableColumn
  Date
  (read-column-by-label [^Date v _] (.toLocalDate v))
  (read-column-by-index [^Date v _ _] (.toLocalDate v))
  Time
  (read-column-by-label [^Time v _] (.toLocalTime v))
  (read-column-by-index [^Time v _ _] (.toLocalTime v))
  Timestamp
  (read-column-by-label [^Timestamp v _] (.toInstant v))
  (read-column-by-index [^Timestamp v _ _] (.toInstant v)))

(extend-protocol next.prepare/SettableParameter
  Instant
  (set-parameter [^Instant v ^PreparedStatement ps ^long i]
    (.setTimestamp ps i (Timestamp/from v)))
  LocalDate
  (set-parameter [^LocalDate v ^PreparedStatement ps ^long i]
    (.setTimestamp ps i (Timestamp/valueOf (.atStartOfDay v)))))

(defn fetch
  ([ds sql]
   (fetch ds sql nil))
  ([ds sql opts]
   (let [sqlvec (honey/format sql)]
     (log/debug sqlvec)
     (->> (jdbc/execute! ds sqlvec (merge {:builder-fn rs-builder} opts))
          (map #(into {} %))))))

(defn execute-batch
  [ds sql]
  (let [sqlvec (honey/format sql)]
    (log/debug sqlvec)
    (->> (jdbc/execute-one! ds sqlvec {:return-keys false})
         ::jdbc/update-count)))

(defn execute
  [ds sql]
  (let [sqlvec (honey/format sql)]
    (log/debug sqlvec)
    (->> (jdbc/execute! ds sqlvec {:builder-fn rs-builder
                                   :return-keys true})
         (map #(into {} %)))))

(defrecord ^:private PostgresDatabase
  [option datasource]
  component/Lifecycle
  (start [this]
    (let [datasource (or datasource
                         (hc/make-datasource (assoc option :adapter "postgresql")))
          component (assoc this :datasource datasource)]
      (mig/migrate {:store :database
                    :migration-dir "migrations"
                    :db component})
      component))
  (stop [this]
    (when datasource
      (hc/close-datasource datasource))
    (assoc this :datasource nil))

  jdbc.proto/Sourceable
  (get-datasource [_]
    datasource)

  p.db/IDatabase
  (add-reservations [_ reservations]
    (doseq [reservation reservations]
      (db.common/assert-reservation reservation))
    (when (seq reservations)
      (-> (sql/insert-into :reservation)
          (sql/values reservations)
          (->> (execute datasource)))))
  (get-all-reservations [_]
    (-> (sql/select :*)
        (sql/from :reservation)
        (->> (fetch datasource))))
  (find-reservation-by-id [_ id]
    (-> (sql/select :*)
        (sql/from :reservation)
        (sql/where [:= :id id])
        (->> (fetch datasource)
             first)))
  (find-reservations-by-hw-address [_  hw-address]
    (-> (sql/select :*)
        (sql/from :reservation)
        (sql/where [:= :hw-address hw-address])
        (->> (fetch datasource))))
  (find-reservations-by-ip-address-range [_ start-address end-address]
    (-> (sql/select :*)
        (sql/from :reservation)
        (sql/where [:>= :ip-address start-address]
                   [:<= :ip-address end-address])
        (->> (fetch datasource))))
  (delete-reservation-by-id [_ id]
    (-> (sql/delete-from :reservation)
        (sql/where [:= :id id])
        (->> (execute-batch datasource))))
  (delete-reservation [_ hw-address]
    (-> (sql/delete-from :reservation)
        (sql/where [:= :hw-address hw-address])
        (->> (execute-batch datasource))))
  (delete-reservations-by-source [_ source]
    (-> (sql/delete-from :reservation)
        (sql/where [:= :source source])
        (->> (execute-batch datasource))))

  (add-lease [_ lease]
    (db.common/assert-lease lease)
    (-> (sql/insert-into :lease)
        (sql/values [lease])
        (->> (execute-batch datasource))))
  (get-all-leases [_]
    (-> (sql/select :*)
        (sql/from :lease)
        (->> (fetch datasource))))
  (find-leases-by-hw-address [_ hw-address]
    (-> (sql/select :*)
        (sql/from :lease)
        (sql/where [:= :hw-address hw-address])
        (->> (fetch datasource))))
  (find-leases-by-ip-address-range [_ start-address end-address]
    (-> (sql/select :*)
        (sql/from :lease)
        (sql/where [:>= :ip-address start-address]
                   [:<= :ip-address end-address])
        (->> (fetch datasource))))
  (find-lease-by-id [_ lease-id]
    (-> (sql/select :*)
        (sql/from :lease)
        (sql/where [:= :id lease-id])
        (->> (fetch datasource)
             first)))
  (update-lease [_  hw-address ip-address values]
    (-> (sql/update :lease)
        (sql/set values)
        (sql/where [:= :hw-address hw-address]
                   [:= :ip-address ip-address])
        (->> (execute-batch datasource))))
  (delete-lease [_ hw-address start-address end-address]
    (-> (sql/delete-from :lease)
        (sql/where [:= :hw-address hw-address]
                   [:>= :ip-address start-address]
                   [:<= :ip-address end-address])
        (->> (execute-batch datasource))))

  (transaction [this f]
    #_:clj-kondo/ignore
    (jdbc/with-transaction [db datasource]
                           (f (assoc this :datasource db)))))

(defn new-postgres-database [option]
  (->PostgresDatabase option nil))
