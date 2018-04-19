(ns rancher-cli.core
  (:require
    [cljs.nodejs :as nodejs]
    [rancher-cli.rancher :as rancher :refer [go-upgrade-services-with-image trace]]
    [cljs.core.async :as a :refer [go <!]]
    [rancher-cli.config :as conf]))
    

(defn -main [& args]
  (println "========================================")
  (let [{:keys [user pass url save print-config] :as options} (conf/load-options args)]
       (rancher/configure-client! user pass)
       (condp #(%2 %1) options
        :print-config (js/console.info conf/js-preferences)
        :save (conf/save-options options)
        (go (some->>
                   (go-upgrade-services-with-image "docker:case/config-probes-microservice:2.25.0" "api")
                   (<!)
                   (trace "====================="))))))
   
(set! *main-cli-fn* -main)
   
(defn some' [data pred] (some pred data))
