(ns {{name}}.subscriptions
  (:require [{{name}}.db :as db]
            [{{name}}.config :as config]
            [datomic.api :as d]))

(defmulti fetch-sub (fn [[sub-type] _] sub-type))

(defmethod fetch-sub :counter [_ db]
  (d/pull db [:counter/value] [:counter/name config/counter-name]))

(defmulti init-sub! (fn [[sub-type] _ _] sub-type))

(defmethod init-sub! :counter [_ user-id db]
  (let [counter (d/entity db [:counter/name config/counter-name])
        txs (when-not (:counter/value counter)
              [{:counter/name config/counter-name
                :counter/value 0}])]
    (if txs
      (:db-after (db/transact! txs))
      db)))

(defmulti authorised? (fn [[sub-type] user-id] sub-type))

(defmethod authorised? :counter [[_ player-id] user-id]
  true)

(defmethod authorised? :default [_ _]
  true)

(defmulti format-for-user (fn [[sub-type] _ _] sub-type))

(defmethod format-for-user :counter [_ counter user-id]
  counter)

(defmulti affected-subs-of-type (fn [[sub-type] evt txs]
                               sub-type))

(defmethod affected-subs-of-type :counter [[_ subs] evt txs]
  (let [counter-id (:db/id (d/entity (:db-after evt) [:counter/name "default"]))
        counter-affected? (->> txs
                               (some #(= (:e %) counter-id)))]
    (when counter-affected?
      subs)))

(defn affected-subs [all-subs evt txs]
  (->> all-subs
       (group-by first)
       (mapcat #(affected-subs-of-type % evt txs))))
