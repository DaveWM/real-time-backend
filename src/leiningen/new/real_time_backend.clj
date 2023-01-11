(ns leiningen.new.real-time-backend
  (:require [leiningen.new.templates :refer [renderer name-to-path ->files]]
            [leiningen.core.main :as main]
            [me.raynes.fs :as fs]))

(def root-files
  (->> (fs/list-dir "resources/leiningen/new/real_time_backend")
       (filter fs/file?)))

(def src-files
  (->> (fs/list-dir "resources/leiningen/new/real_time_backend/src")
       (filter fs/file?)))

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
                                 [(fs/base-name f) (render (fs/base-name f) data)])))
                     (->> src-files
                          (map (fn [f]
                                 [(str "src/{{sanitized}}/" (fs/base-name f))
                                  (render (str "src/" (fs/base-name f)) data)])))))))
