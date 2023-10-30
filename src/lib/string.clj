(ns lib.string
  (:require [clojure.string :as string]))

(defn- find-indent
  [string]
  (let [candidate (->> (string/split-lines string)
                       (next)
                       (filter seq)
                       first)
        [_ indent] (when candidate (re-matches #"^(\s+).*" candidate))]
    indent))

(defn dedent
  ([string]
   (dedent (find-indent string) string))
  ([indent string]
   (cond->> (string/split-lines string)
     indent (map #(string/replace % (re-pattern (str "^" indent)) ""))
     :always (string/join "\n"))))
