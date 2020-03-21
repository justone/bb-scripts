(ns lib.opts
  (:require
    [clojure.string :as string]
    ))

(defn print-usage
  [progname summary]
  (println "usage: " progname " [opts]")
  (println " ")
  (println "options:")
  (println summary))

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

(defn print-errors
  [progname parsed errors]
  (let [{:keys [summary]} parsed
        {:keys [message exit]} errors]
    (when message
      (println message)
      (println " "))
    (print-usage progname summary)
    exit))
