(ns rancher-cli.request
  (:require [cljs.nodejs :as nodejs]
            [cljs.core.async :as a :refer [go <! >! chan close!]]
            ["request" :as req]))

(nodejs/enable-util-print!)

(defn makeAuth [usr pass] #js{ :user usr :pass pass :sendImmediately false})
  
(defn handler [ch msg] 
  (fn [err res body]
   (go 
      (if err 
        (js/console.error (str "Bad error during " msg) err)
        (>! ch  body))
      (close! ch))))

(defn make-opts
 ([usr pass url] (make-opts usr pass url nil))
 ([usr pass url payload] 
  (let [ opts (if (and usr pass)
                  #js{:json true :auth (makeAuth usr pass)} 
                  #js{:json true})]
   (when payload (aset opts "body" payload))
   opts)))


(defn Post 
 ([usr pass url] (Post usr pass url #js{}))
 ([usr pass url payload] 
  (let [c (chan)
        options (make-opts usr pass url payload)]
    ;(prn "Posting to " url options)
    (req/post url options (handler c (str "POST" url)))
    c)))

(defn Get 
  ([url] (Get nil nil url))
  ([usr pass url] 
   (let [c (chan)
         reqHandler (handler c "GET")
         opts (make-opts usr pass url)]
    (req/get url opts reqHandler)
    c)))
