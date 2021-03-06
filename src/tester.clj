(ns tester
  (:require
    [clojure.tools.cli :refer [parse-opts]]

    [lib.opts :as opts]
    [user]
    )
  (:gen-class))

(def progname "tester")

(def cli-options
  [["-h" "--help"]])

(defn process
  [options]
  (prn options))

(defn -main [& args]
  (let [parsed (parse-opts args cli-options)
        {:keys [options]} parsed]
    (or (some->> (opts/find-errors parsed)
                 (opts/print-errors progname parsed)
                 (System/exit))
        (process options))))
