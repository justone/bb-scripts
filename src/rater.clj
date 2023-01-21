(ns rater
  (:require [clojure.tools.cli :refer [parse-opts]]
            [lib.opts2 :as opts]
            [lib.string]
            [user])
  (:import (java.time Instant OffsetDateTime)
           (java.time.temporal ChronoUnit)))

(comment
  (Instant/now)
  (.until (.toInstant (OffsetDateTime/parse "2020-06-28T20:00:00Z"))
          (.toInstant (OffsetDateTime/parse "2020-06-28T20:00:04.923Z"))
          (ChronoUnit/MILLIS)))

(def progname (str *ns*))

(def cli-options
  [["-h" "--help" "Show help"]])

(def help
  (lib.string/dedent
    "    "
    "Report on the rate of change and approximate completion time for inputs.

    Run it and enter the current amount. After a time, enter the new current
    amount. Rater will tell you how fast the amount is changing and when it
    will hit 0."))

(defn process
  [options input]
  (prn options)
  (str "input: " input))

(defn -main [& args]
  (let [parsed (parse-opts args cli-options :in-order true)
        {:keys [options]} parsed]
    (or (some->> (opts/find-errors parsed)
                 (opts/format-help progname help parsed)
                 (opts/print-and-exit))
        (->> (map (partial process options) user/*input*)
             (run! println)))))
