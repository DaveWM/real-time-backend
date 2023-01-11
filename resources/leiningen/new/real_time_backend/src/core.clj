(ns {{name}}.core
  (:require [datomic.api :as d]
            [jumblerg.middleware.cors]
            [mount.core :refer [defstate]]
            [ring.middleware.json]
            [ring.middleware.keyword-params]
            [ring.middleware.params]
            [ring.middleware.session]
            [{{name}}.db :as db]
            [{{name}}.subscriptions :as subs]
            [{{name}}.server :as server]
            [taoensso.sente :as sente]
            [{{name}}.config :as config])
  (:gen-class))

(declare sub->users*)

(defmulti event-msg-handler :id)

(defmethod event-msg-handler :event/subscribe [{sub :?data reply-fn :?reply-fn user-id :uid :as event}]
  (when (subs/authorised? sub user-id)
    (let [db (subs/init-sub! sub user-id (db/get-db))
          result (subs/fetch-sub sub db)]
      (swap! sub->users* update sub (comp set conj) user-id)
      (when reply-fn
        (reply-fn {:data (subs/format-for-user sub result user-id)
                   :sub  sub})))))

(defmethod event-msg-handler :event/unsubscribe [{sub-pattern :?data user-id :uid :as event}]
  (swap! sub->users* (fn [sub->users]
                       (->> sub->users
                            (map (fn [[sub users]]
                                   [sub
                                    (if (= sub sub-pattern)
                                      (disj users user-id)
                                      users)]))
                            (into {})))))

(defmethod event-msg-handler :chsk/ws-ping [{user-id :uid :as event}]
  (server/chsk-send! user-id [:channel/ws-pong]))

(defmethod event-msg-handler :chsk/uidport-close [{user-id :uid}]
  (swap! sub->users*
         (fn [sub->users]
           (->> sub->users
                (keep (fn [[sub users]]
                        (when-let [new-users (->> users (remove #{user-id}) seq)]
                          [sub (set new-users)])))
                (into {})))))

(defmethod event-msg-handler :counter/increment [_]
  (let [db (db/get-db)
        counter-val (:counter/value (d/entity db [:counter/name config/counter-name]))]
    (db/transact! [[:db/add [:counter/name config/counter-name] :counter/value (inc counter-val)]])))

(defmethod event-msg-handler :counter/decrement [_]
  (let [db (db/get-db)
        counter-val (:counter/value (d/entity db [:counter/name config/counter-name]))]
    (db/transact! [[:db/add [:counter/name config/counter-name] :counter/value (dec counter-val)]])))

(defmethod event-msg-handler :channel/ws-pong [event]
  nil)

(defmethod event-msg-handler :default [event]
  (println "Unknown event: " (prn-str event))
  nil)

(defstate socket-server-router
  :start (sente/start-server-chsk-router! (:ch-recv server/socket-server) event-msg-handler)
  :stop (socket-server-router))

(defstate sub->users*
  :start (atom {}))

(defstate tx-watcher
  :start (doto
           (Thread.
             ^Runnable
             (fn []
               (println "STARTING")
               (try
                 (while (not (Thread/interrupted))
                   (println "WAITING...")
                   (try
                     (let [{:keys [db-after tx-data] :as evt} (.take (db/tx-queue))
                           sub->users @sub->users*
                           subs-to-update (subs/affected-subs (keys sub->users) evt tx-data)]
                       (doseq [sub subs-to-update]
                         (let [users (sub->users sub)
                               result (subs/fetch-sub sub db-after)]
                           (doseq [user users]
                             (println "Pushing to " user)
                             (server/chsk-send! user [:server/push {:data (subs/format-for-user sub result user)
                                                                        :sub  sub}])))))
                     (catch InterruptedException e
                       (throw e))
                     (catch Exception e
                       (println "Caught error in tx watcher thread: " e))))
                 (catch InterruptedException e
                   (println "Thread interrupted")))))
           (.start))
  :stop (.interrupt tx-watcher))


(defn -main [& args]
  (mount.core/start))


(comment

  (mount.core/start)
  (mount.core/stop)

  )
