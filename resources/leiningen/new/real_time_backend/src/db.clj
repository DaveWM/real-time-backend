(ns {{name}}.db
  (:require [datomic.api :as d]
            [mount.core :refer [defstate]]
            [{{name}}.config :as config]))

(def schema
  [{:db/ident       :counter/name
    :db/valueType   :db.type/string
    :db/cardinality :db.cardinality/one
    :db/unique      :db.unique/identity}
   {:db/ident       :counter/value
    :db/valueType   :db.type/long
    :db/cardinality :db.cardinality/one}])

(defstate conn
  :start (do
           (d/create-database config/db-uri)
           (let [new-conn (d/connect config/db-uri)]
             @(d/transact new-conn schema)
             new-conn)))

(defn tx-queue []
  (d/tx-report-queue conn))

(defn get-db []
  (d/db conn))

(defn transact! [txs]
  @(d/transact conn txs))
