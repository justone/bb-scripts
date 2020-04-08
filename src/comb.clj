(ns comb
  (:require
    [clojure.tools.cli :refer [parse-opts]]

    [lib.opts :as opts]
    [comb.template :as template]
    [user]
    )
  (:gen-class))

(def progname "comb")

(def cli-options
  [["-t" "--template TEMPLATE" "Template to process"]
   ["-h" "--help"]])

(defn process
  [options data]
  (let [{:keys [template]} options]
    (binding [*out* *err*]
      (println "data:" (pr-str data)))
    (template/eval template data)))

(defn -main [& args]
  (let [parsed (parse-opts args cli-options)
        {:keys [options]} parsed]
    (or (some->> (opts/find-errors parsed)
                 (opts/print-errors progname parsed)
                 (System/exit))
        (->> (map (partial process options) user/*input*)
             (run! println)))))
