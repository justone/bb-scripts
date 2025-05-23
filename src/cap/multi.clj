(ns cap.multi
  (:require [clojure.tools.cli :refer [parse-opts]]
            [scribe.opts :as opts]))

(defn find-errors
  [usage parsed subcommands]
  (or (opts/validate parsed usage)
      (and subcommands
           (let [{:keys [arguments]} parsed
                 subcommand (-> arguments first keyword)]
             (cond
               (nil? subcommand)
               {:exit 1
                :message usage}

               (not (contains? subcommands subcommand))
               {:message (str "Invalid subcommand: " (name subcommand))
                :exit 1})))))

(defn entry
  ([opts]
   (entry opts *command-line-args*))
  ([opts args]
   (entry opts args :top []))
  ([opts args cmd result]
   (let [{:keys [usage cli-options validate-fn subcommands]} opts
         script-name (or (:script-name opts)
                         (opts/detect-script-name))
         parsed (-> (parse-opts args cli-options :in-order true)
                    (assoc :command cmd))
         [subcommand & subargs] (:arguments parsed)
         subcommandkey (keyword subcommand)
         subcommandopts (get subcommands subcommandkey)]
     (or (some-> (or (find-errors (or usage "") parsed subcommands)
                     (and validate-fn
                          (validate-fn parsed)))
                 (opts/format-help script-name parsed)
                 (opts/print-and-exit))
         (if subcommandopts
           (entry (assoc subcommandopts :script-name (format "%s %s" script-name subcommand)) subargs subcommandkey (conj result parsed))
           (conj result parsed))))))

(comment
  (def try-opts
    {:cli-options [["-h" "--help" "Show help"]
                   ["-p" "--path PATH" "Specify path to operate on"]]
     :usage "top"
     :subcommands {:foo {:cli-options [["-h" "--help" "Show help"]
                                       ["-d" "--dath DATH" "Specify path to operate on"]]
                         :usage "foo"
                         :subcommands {:bar {:cli-options [["-h" "--help" "Show help"]
                                                           ["-g" "--gath GATH" "Specify path to operate on"]]
                                             :usage "bar"
                                             }}}}})
  (entry try-opts ["-p" "foobarpath" "foo" "-d" "test" "bar" "-g" "final"])
  (entry try-opts ["foo" "bar" "-h"])
  (entry try-opts ["foo" "-h"])
  (entry try-opts ["-h"])
  (entry try-opts [])
  )
