(ns rancher-cli.core
  (:require
    [cljs.nodejs :as nodejs]
    [rancher-cli.credentials :as cred]
    [cljs.core.async :as a :refer [go <!]]
    [goog.object :as ob :refer [get]]
    [rancher-cli.request :as req]))


(nodejs/enable-util-print!)
(defn trace [msg data] (println msg data) data)


(defn pick [prop o]
  (aget o prop))

(def pickUrl (partial pick "url"))
(defn convert [o] (js->clj o :keywordize-keys true))
(def Get (partial req/Get cred/user cred/pass))
(def Post (partial req/Post cred/user cred/pass))
(defn findServicesWithImage [imageName services] 
 (->> services 
  convert
  (filter #(=(get-in % [:launchConfig :imageUuid]) imageName))))

(defn upgradePayload [imageName envConfig launchConfig]
 { :inServiceStrategy { :launchConfig 
                        (merge launchConfig 
                         {:imageUuid imageName
                          :environment (merge (:environment launchConfig)
                                           envConfig)})} 
  :toServiceStrategy nil})
  
        

(defn upgradeImage [imageName service]
 (let [upgradeUrl (get-in service [:actions :upgrade])] 
  (go (->> service 
        :launchConfig
        (upgradePayload imageName {:pene "gordo"})
        (clj->js)
        (Post upgradeUrl)
        (<!))))) 
 

(defn -main [& args] 
    (println "========================================")
    (go (->> (<! (getStacks cred/url "int"))
            (findStack "api")
            :links :services
            Get
            (<!)
            .-data
            (findServicesWithImage "docker:case/config-probes-microservice:2.25.0")
            first
            (upgradeImage "docker:case/config-probes-microservice:2.25.0")
            (<!)
            (trace "====================="))))
  

(set! *main-cli-fn* -main)
(defn some' [data pred] (some pred data))
(defn nameEquals [name data] (-> data 
                              :name
                              (.toLowerCase)
                              (= name)))

(defn jFirst [o] (aget o 0))

(defn getStacks [baseUrl envName] 
  (let [url (str baseUrl "/v2-beta", "/projects?name=" envName)]
    (go (some-> (<! (Get url))
          .-data   
          jFirst
          .-links
          .-stacks
          Get
          (<!)))))



(defn findFirst [pred col] (first (filter pred col)))
(defn findStack [name response]
 (->> response 
  .-data 
  convert 
  (findFirst (partial nameEquals name))))
                                


(defn findServicesByImage [envName stackName imageName]
   getStacks(envName))
