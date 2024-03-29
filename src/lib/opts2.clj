(ns lib.opts2
  (:require
    [clojure.string :as string]

    [lib.string]
    ))

(defn find-errors
  [parsed]
  (let [{:keys [errors options]} parsed
        {:keys [help]} options]
    (cond
      help
      {:exit 0}

      errors
      {:message (string/join "\n" errors)
       :exit 1}
      )))

(def help-fmt
  (lib.string/dedent
    "    "
    "usage: %s [opts]

    %s

    options:
    %s"))

(defn format-help
  ([prog-ns parsed errors]
   (format-help (str prog-ns)
                (-> (meta prog-ns) :doc lib.string/dedent)
                parsed
                errors))
  ([progname help parsed errors]
   (let [{:keys [summary]} parsed
         {:keys [message exit]} errors
         final-message (or message
                           (-> help
                               lib.string/dedent
                               (string/replace "SCRIPT_NAME" progname)))]
     {:help (format help-fmt progname final-message summary)
      :exit exit})))

(defn print-and-exit
  [{:keys [help exit]}]
  (throw (ex-info help {:babashka/exit exit})))
