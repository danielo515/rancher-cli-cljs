(ns rancher-cli.core
  (:require
    [cljs.nodejs :as nodejs]
    [rancher-cli.credentials :as cred]
    [cljs.core.async :as a :refer [go <! timeout go-loop]]
    [goog.object :as ob :refer [get]]
    [cljs.tools.cli :refer [parse-opts]]
    [rancher-cli.request :as req]))

(nodejs/enable-util-print!)

(def options-spec
  [
    ["-h" "--help"]
    ["-U" "--usr user" "Rancher username"]
    ["-P" "--pass password" "Rancher password"]])

(defn run [args]
  (parse-opts args options-spec))

(defn trace [msg data] (println msg data) data)

(defn pick [prop o]
  (aget o prop))

(def pickUrl (partial pick "url"))
(defn convert [o] (js->clj o :keywordize-keys true))
(defn jFirst [o] (aget o 0))

(def Get (partial req/Get cred/user cred/pass))
(def Post (partial req/Post cred/user cred/pass))


(defn findServicesWithImage [imageName services] 
 (->> services 
  convert
  (filter #(=(get-in % [:launchConfig :imageUuid]) imageName))))

(defn upgradePayload [imageName envConfig service]
 (let [launchConfig (:launchConfig service)
       payload (clj->js { :inServiceStrategy { :launchConfig 
                                              (merge launchConfig 
                                                   {:imageUuid imageName
                                                    :environment (merge (:environment launchConfig)
                                                                        envConfig)})}
                          :toServiceStrategy nil})] 
      [service payload]))

(defn go-wait-for-upgrade [service] 
 (let [self (:self (:links service))
       name (:name service)]
  (go-loop [_service (<!(Get self))]
    (<! (timeout 1000))
    (if (= (.-state _service) "active") 
        (do (prn "Service " name " upgrade finished") 
            (js->clj _service :keywordize-keys true))
        (do (prn "Service not yet ready, waiting again" (.-state _service))
            (recur (<!(Get self))))))))
   
    

(defn finishUpgrade [service]
  (go 
    (if-let [finishUrl (get-in service [:actions :finishupgrade])] 
      (do (prn (str "About to finish upgrade for " (:name service)))
          (<!(Post finishUrl))
          (<! (go-wait-for-upgrade service)))
      (do (prn (str "Finish upgrade not required for " (:name service))) 
          service))))
    
(defn retuple-first [processor tuple] 
 [(processor (first tuple)) (last tuple)])

(defn action [name service] (get-in service [:actions name]))

(defn upgradeImage [imageName service]
 (go (->> service
        finishUpgrade
        (<!)
        (upgradePayload imageName {:pene "POLLÓN"})
        (retuple-first (partial action :upgrade))
        ;(trace "Upgrade may fail")
        (apply Post)
        (<!))))
 
(defn getStacks [baseUrl envName] 
  (let [url (str baseUrl "/v2-beta", "/projects?name=" envName)]
    (go (some-> (<! (Get url))
          .-data   
          jFirst
          .-links
          .-stacks
          Get
          (<!)))))

(defn nameEquals [name data] (-> data 
                              :name
                              (.toLowerCase)
                              (= name)))


(defn findFirst [pred col] (first (filter pred col)))


(defn findStack [name response]
 (->> response 
  .-data
  convert 
  (findFirst (partial nameEquals name))))

(defn -main [& args] 
    (println "========================================")
    (println (run args))
    (go (some->> (<! (getStacks cred/url "int"))
            (findStack "api")
            :links :services
            Get
            (<!)
            .-data
            (findServicesWithImage "docker:case/config-probes-microservice:2.25.0")
            first
            (upgradeImage "docker:case/config-probes-microservice:2.25.0")
            (<!)
            .-name
            (trace "====================="))))
  

(set! *main-cli-fn* -main)

(defn some' [data pred] (some pred data))

(defn findServicesByImage [envName stackName imageName]
   getStacks(envName))
