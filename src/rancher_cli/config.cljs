(ns rancher-cli.config 
 (:require ["preferences" :as pref]
           [goog.object :as ob]
           [cljs.tools.cli :refer [parse-opts]]))

(def options-spec
  [
    ["-h" "--help"]
    ["-U" "--user user" "Rancher username"]
    ["-P" "--pass password" "Rancher password"]
    ["-S" "--save" "Save the provided values into the configuration"]]) 
    

(defn run [args]
  (parse-opts args options-spec))

(def js-preferences 
  (pref. "com.rancher-cli.cljs"
        #js { :rancher #js {:url nil :user nil :pass nil}}))
     
(defn merge-into-js [js clj]
  "Merges a clj object into a js object (clj has preference), returning a clj object."
  (merge (js->clj js :keywordize-keys true) clj))

(defn load-options [argv]
  "Load CMD options and merges them with the stored ones, giving preference to CMD ones"
 (let [options (:options (run argv))]
  (prn "CMD options" options)
  (js/console.info "Stored prefs " js-preferences)
  (merge-into-js (.-rancher js-preferences) options)))

(defn set' [key obj val] (ob/set obj key val) obj)

(defn save-options [usr pass url]
  "Saves the options that we understand into the store"
 (->> (merge-into-js (.-rancher js-preferences) {:user usr :pass pass :url url})
  (clj->js)
  (set' "rancher" js-preferences)))
