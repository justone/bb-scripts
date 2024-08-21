(ns comb
  (:require
    [clojure.tools.cli :refer [parse-opts]]

    [scribe.opts :as opts]
    [comb.template :as template]
    [user])
  (:gen-class))

(def script-name (opts/detect-script-name))

(def cli-options
  [["-t" "--template TEMPLATE" "Template to process"]
   ["-s" "--show-data" "Print incoming data to stderr"]
   ["-h" "--help"]])

(defn process
  [options data]
  (let [{:keys [template show-data]} options]
    (when show-data
      (binding [*out* *err*] (println "data:" (pr-str data))))
    (template/eval template data)))

(defn -main [& args]
  (let [parsed (parse-opts args cli-options)
        {:keys [options]} parsed]
    (or (some-> (opts/validate parsed "Command line templating using the comb library.")
                (opts/format-help script-name parsed)
                (opts/print-and-exit))
        (->> (map (partial process options) user/*input*)
             (run! println)))))
