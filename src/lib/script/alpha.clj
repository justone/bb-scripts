(ns lib.script.alpha
  (:require [clojure.java.io :as io]
            [clojure.tools.cli :refer [parse-opts]]
            [lib.opts2 :as opts]))

(defn divine-progname
  [filename]
  (.getName (io/file filename)))

(defn simple-main
  "Single-file script helper. This function will parse command line arguments and take care of displaying help and
  Takes a single map of options:
  * :help - Usage information to show in `--help` output. Can be multiple
            lines. Any occurances of 'SCRIPT_NAME' in this string will be
            replaced with the actual script's name.
  * :cli-options - Argument parsing configuration for clojure.tools.cli/parse-opts.
  * :validate-fn - (optional) A function that takes the result of
                   clojure.tools.cli/parse-opts to further validate the command
                   line arguments. This function should return a map to indicate an
                   error, or nil to indicate no errors. The map keys are:
                   * :message - (optional) Message to be printed.
                   * :exit - The numeric exit code that the
  * :progname - (optional) The name of the script, inferred from the script
                filename if not passed."
  ([opts]
    (simple-main opts *command-line-args*))
  ([opts args]
   (let [{:keys [help cli-options validate-fn]} opts
         progname (or (:progname opts) (divine-progname (System/getProperty "babashka.file")))
         parsed (parse-opts args cli-options)]
     (or (when-some [errors (or (opts/find-errors parsed)
                                (and validate-fn
                                     (validate-fn parsed)))]
           (->> (opts/format-help progname (or help "") parsed errors)
                (opts/print-and-exit)))
         parsed))))
