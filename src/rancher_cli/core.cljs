(ns rancher-cli.core
  (:require
    [cljs.nodejs :as nodejs]
    [rancher-cli.credentials :as cred]
    [cljs.core.async :as a :refer [go <! timeout go-loop]]
    [rancher-cli.config :as conf]
    [rancher-cli.request :as req]))

(nodejs/enable-util-print!)

(def credentials (atom []))

(defn trace [msg data] (println msg data) data)

(defn pick [prop o]
  (aget o prop))

(def pickUrl (partial pick "url"))
(defn convert [o] (js->clj o :keywordize-keys true))
(defn jFirst [o] (aget o 0))
(defn configure-client! [usr pass] (reset! credentials  [usr pass]))

(defn Get [& args]
    (apply req/Get (concat @credentials args)))

(defn Post [& args]
    (apply req/Post (concat @credentials args)))


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

(defn go-upgrade-services-with-image [imageName stackName]  
  (go (some->> (<! (getStacks cred/url "int"))
        (findStack stackName)
        :links :services
        Get
        (<!)
        .-data
        (findServicesWithImage imageName)
        first
        (upgradeImage imageName)
        (<!)
        .-name)))
        

(defn -main [& args]
 (let [{:keys [user pass url]} (conf/load-options args)]
    (configure-client! user pass))
 (println "========================================")
 (go (some->> 
      (go-upgrade-services-with-image "docker:case/config-probes-microservice:2.25.0" "api")
      (<!)
      (trace "====================="))))

(set! *main-cli-fn* -main)

(defn some' [data pred] (some pred data))
