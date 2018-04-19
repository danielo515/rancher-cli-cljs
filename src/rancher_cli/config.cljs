(ns rancher-cli.config 
 (:require ["preferences" :as pref]
           ["docopt" :as cli :refer [docopt]]
           [goog.object :as ob]
           [cljs.tools.cli :refer [parse-opts]]))

(def options-spec
  [
    ["-h" "--help"]
    ["-U" "--user user" "Rancher username"]
    ["-P" "--pass password" "Rancher password"]
    ["-p" "--print-config" "Prints the saved config"]
    ["-S" "--save" "Save the provided values into the configuration"]]) 
    
(def docoptSpec " rancher-cli

  Options can be read from a stored configuration or provided through environment variables.
  If they exist, enviroment variables have preference over stored configurations.
  Accepted env variables are RANCHER_URL, RANCHER_SECRET_KEY, RANCHER_ACCESS_KEY
 
  Usage:
     rancher-cli upgrade <stackName> <imageName> [-e  <NAME=value>...] [--rancher-env=<name>]
     rancher-cli upgrade finish <stackName> <imageName> [--rancher-env=<name>]
     rancher-cli get (dockerCompose|rancherCompose) <stackName> [-o <fileName>] [--rancher-env=<name>]
     rancher-cli config saveEnv
     rancher-cli config print
     rancher-cli -v
 
 
  Options:
     --rancher-env=<name>            Rancher environment name (stg or int) [default: int]
     -e --environment <NAME=value>   Adds a new environment variable to the list of already existing environment variables
     -o --output      <fileName>     Write the result of the command to a file instead of stdout
     -v --version                    Show the version of the tool")

(defn run [args]
  (parse-opts args options-spec))

(def js-preferences 
  (pref. "com.rancher-cli.cljs"
        #js { :rancher #js {:url nil :user nil :pass nil}}))
     
(defn merge-into-js 
  "Merges a clj object into a js object (clj has preference), returning a clj object."
  [js clj]
  (merge (js->clj js :keywordize-keys true) clj))

(defn load-options 
  "Load CMD options and merges them with the stored ones, giving preference to CMD ones"
  [argv]
 (let [options (js->clj (docopt docoptSpec) :keywordize-keys true)]
;(let [options (:options (run argv))]
  (prn "CMD options" options)
  ; (prn (js->clj (docopt docoptSpec) :keywordize-keys true))
  ;(js/console.info "Stored prefs " js-preferences)
  (merge-into-js (.-rancher js-preferences) options)))

(defn set' [key obj val] (ob/set obj key val) obj)

(defn save-options 
  "Saves the options that we understand into the store"
  [{:keys [user pass url]}]
 (->> (merge-into-js (.-rancher js-preferences) {:user user :pass pass :url url})
  (clj->js)
  (set' "rancher" js-preferences)))
