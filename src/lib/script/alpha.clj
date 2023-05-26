(ns lib.script.alpha
  (:require [clojure.java.io :as io]
            [clojure.tools.cli :refer [parse-opts]]
            [lib.opts2 :as opts]))

(defn divine-progname
  [filename]
  (.getName (io/file filename)))

(defn simple-main
  [opts]
  (let [{:keys [help cli-options]} opts
        progname (or (:progname opts) (divine-progname (System/getProperty "babashka.file")))
        parsed (parse-opts *command-line-args* cli-options)]
    (or (when-some [errors (opts/find-errors parsed)]
          (->> (opts/format-help progname (or help "") parsed errors)
               (opts/print-and-exit)))
        parsed)))
