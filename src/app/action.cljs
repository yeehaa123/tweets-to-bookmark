(ns app.action
  (:require [cljs.spec :as spec]
            [app.specs :as specs]
            [clojure.string :as str]))

(defn create [bookmarks]
  {:type "add-bookmarks"
   :payload bookmarks})

#_(spec/instrument #'convert)
