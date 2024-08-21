(ns tester
  (:require
    [clojure.tools.cli :refer [parse-opts]]

    [scribe.opts :as opts])
  (:gen-class))

(def script-name (opts/detect-script-name))

(def cli-options
  [["-h" "--help"]])

(defn process
  [options]
  (prn options))

(defn -main [& args]
  (let [parsed (parse-opts args cli-options)
        {:keys [options]} parsed]
    (or (some-> (opts/validate parsed "Test script for copying.")
                (opts/format-help script-name parsed)
                (opts/print-and-exit))
        (process options))))
