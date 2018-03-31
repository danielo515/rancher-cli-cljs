(ns rancher-cli.request
  (:require [cljs.nodejs :as nodejs]
            [cljs.core.async :as a :refer [go <! >! chan close!]]
            ["request" :as req]))

(nodejs/enable-util-print!)

(defn makeAuth [usr pass] #js{ :user usr :pass pass :sendImmediately false})
  
(defn handler [ch] 
  (fn [err res body]
   (go 
      (if err 
        (prn "Bad error" err) 
        (>! ch body))
      (close! ch))))

(defn Post [usr pass url payload] 
  (let [c (chan)
        payload #js{:body payload :auth (makeAuth usr pass) :json true}]
    ;(prn "Post payload" (js/JSON.stringify payload))
    (req/post url payload (handler c))
    c))

(defn Get 
  ([url] (Get nil nil url))
  ([usr pass url] 
   (let [c (chan)]
      (if (and usr pass) 
          (req/get url #js{:json true :auth (makeAuth usr pass)} (handler c))
          (req/get url (handler c))) 
      c)))
