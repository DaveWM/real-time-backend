(ns leiningen.new.real-time-backend
  (:require [leiningen.new.templates :refer [renderer name-to-path ->files]]
            [leiningen.core.main :as main]))

(def root-files
  ["LICENSE" "project.clj" "README.md"])

(def src-files
  ["config.clj" "core.clj" "db.clj" "server.clj" "subscriptions.clj"])


(def render (renderer "real-time-backend"))

(defn real-time-backend
  "FIXME: write documentation"
  [name]
  (let [data {:name      name
              :sanitized (name-to-path name)}]
    (main/info "Generating fresh 'lein new' real-time-backend project.")
    (apply ->files data
             (concat (->> root-files
                          (map (fn [f]
                                 [f (render f data)])))
                     (->> src-files
                          (map (fn [f]
                                 [(str "src/{{sanitized}}/" f)
                                  (render (str "src/" f) data)])))))))
