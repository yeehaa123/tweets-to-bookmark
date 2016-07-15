(ns app.message
  (:require [cljs.nodejs :as node]
            [app.specs :as specs]
            [app.bookmark :as bookmark]
            [cljs.spec :as spec]
            [cljs.core.async :refer [<! put! close! chan >!]])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(def ^:private AWS (node/require "aws-sdk"))
(def Kinesis (new AWS.Kinesis))

(defmulti create (fn [type _] type))

#_(defmethod create :bookmarks [type twitter-data]
  (when (:has-tweets? twitter-data)
    (let [bookmarks (spec/explain-data ::specs/bookmarks (bookmark/extract (:tweets twitter-data)))]
      (when (not (empty? bookmarks))
                 {:StreamName "tweeted-bookmarks"
                  :Records (->> bookmarks
                                (map (fn [bookmark]
                                       {:Data (.stringify js/JSON (clj->js bookmark))
                                        :PartitionKey "user"})))}))))

#_(defmethod create :user-data [type twitter-data]
  (when (:has-more? twitter-data)
    {:StreamName "test-channel"
     :Records [(let [data (select-keys twitter-data [:user :min-id])]
                 {:Data (.stringify js/JSON (clj->js data))
                  :PartitionKey "user"})]}))

(defn create [{:keys [type payload]} partition-key]
  {:StreamName type
   :Records (->> payload
                 (map (fn [item]
                        {:Data (.stringify js/JSON (clj->js item))
                         :PartitionKey partition-key})))})

(defn send [action partition-key]
  (let [c (chan)
        message (create action partition-key)]
    (.putRecords Kinesis
                 (clj->js message)
                 #(if %1
                    (println "error" %1)
                    (go (>! c %2))))
    c))
