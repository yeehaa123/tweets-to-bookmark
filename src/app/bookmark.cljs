(ns app.bookmark
  (:require [app.specs :as specs]
            [cljs.spec :as spec]))

(spec/def ::data (spec/or :one ::specs/tweet :many (spec/* ::specs/tweet)))

(defmulti extract (fn [data] (first (spec/conform ::data data))))

(defmethod extract :one [tweet]
  {:user (-> tweet :user :screen_name)
   :url (-> tweet :entities :urls first :expanded_url)
   :timestamp (-> tweet :created_at)})

(defmethod extract :many [tweets]
  (->> tweets
       (map #(extract %1))
       (filter #(:url %1))))
