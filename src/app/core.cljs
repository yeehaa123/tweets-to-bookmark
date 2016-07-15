(ns app.core
  (:require [cljs.nodejs :as node]
            [app.action :as action]
            [app.bookmark :as bookmark]
            [app.message :as message]
            [app.specs :as specs]
            [cljs.spec :as spec]
            [cljs.core.async :as async :refer [<! put! close! chan >!]]
            [clojure.string :as str])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(node/enable-util-print!)

(def unmarshaler (.-unmarshalItem (node/require "dynamodb-marshaler")))
(def expander (node/require "unshortener"))

(defn to-tweet [record]
  (-> record
      :dynamodb
      :NewImage
      clj->js
      unmarshaler
      (js->clj :keywordize-keys true)))

(defn inserted? [record] (= (:eventName record) "INSERT"))

(defn handle-error [reason payload cb]
  (let [error (clj->js {:type :error
                        :error reason
                        :payload payload})]
    (println (.stringify js/JSON error))))

(defn expand-url [res]
  (let [{:keys [protocol hostname pathname] :as url} (js->clj (->> res
                                                                   (.stringify js/JSON)
                                                                   (.parse js/JSON))
                                                              :keywordize-keys true)]
    (str protocol "//" hostname pathname)))

(defn extract-url [{:keys [url] :as record}]
  (let [c (chan)]
    (.expand expander url #(go (if %1
                                 (println %1)
                                 (>! c (assoc record :url (expand-url %2)))
                                 )))
    c))

(defn ^:export handler [event context cb]
  (println (.stringify js/JSON event))
  (go
    (let [records         (:Records (js->clj event :keywordize-keys true))
          tweets          (map to-tweet (filter inserted? records))
          raw-bookmarks   (bookmark/extract tweets)
          bookmarks-chan  (map extract-url raw-bookmarks)]
      (doseq [bc bookmarks-chan]
        (let [outgoing-action (action/create [(<! bc)])]
          (if (spec/valid? ::specs/action outgoing-action)
            (let [response (<! (message/send outgoing-action :created_at))]
              (println (.stringify js/JSON (clj->js outgoing-action))))
            (handle-error :invalid-outgoing-action (spec/explain-data ::specs/action outgoing-action) cb))))
      (cb nil (clj->js "saved")))))

(defn -main [] identity)
(set! *main-cli-fn* -main)
