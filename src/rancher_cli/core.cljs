(ns rancher-cli.core
  (:require
    [cljs.nodejs :as nodejs]
    [rancher-cli.rancher :as rancher :refer [go-upgrade-services-with-image trace]]
    [cljs.core.async :as a :refer [go <!]]
    [rancher-cli.config :as conf]))
    

(defn -main [& args]
  (let [{:keys [user pass url]} (conf/load-options args)]
       (rancher/configure-client! user pass))
  (println "========================================")
  (go (some->> 
        (go-upgrade-services-with-image "docker:case/config-probes-microservice:2.25.0" "api")
        (<!)
        (trace "====================="))))
   
(set! *main-cli-fn* -main)
   
(defn some' [data pred] (some pred data))
