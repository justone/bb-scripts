(ns lib.string
  (:require [clojure.string :as string]))

(defn- find-indent
  [string]
  (let [candidate (->> (string/split-lines string)
                       (next)
                       (filter seq)
                       first)
        [_ indent] (re-matches #"^(\s+).*" candidate)]
    indent))

(defn dedent
  ([string]
   (dedent (find-indent string) string))
  ([indent string]
   (let [pattern (re-pattern (str "^" indent))]
     (->> (string/split-lines string)
          (map #(string/replace % pattern ""))
          (string/join "\n")))))
