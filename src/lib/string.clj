(ns lib.string
  (:require
    [clojure.string :as string]
    ))

(defn dedent
  [indent string]
  (let [pattern (re-pattern (str "^" indent))]
    (->> (string/split string #"\n")
         (map #(string/replace % pattern ""))
         (string/join "\n"))))
