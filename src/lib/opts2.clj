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
  [progname help parsed errors]
  (let [{:keys [summary]} parsed
        {:keys [message exit]} errors]
    {:help (format help-fmt progname (or message help) summary)
     :exit exit}))

(defn print-and-exit
  [{:keys [help exit]}]
  (println help)
  (System/exit exit))
