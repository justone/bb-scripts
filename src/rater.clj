(ns rater
  (:require [clojure.string :as string]
            [clojure.tools.cli :refer [parse-opts]]
            [lib.opts2 :as opts]
            [lib.string]
            [user])
  (:import (java.lang NumberFormatException)
           (java.time Instant OffsetDateTime ZoneId)
           (java.time.temporal ChronoUnit)))

(comment
  (Instant/now)
  (.until (.toInstant (OffsetDateTime/parse "2020-06-28T20:00:00Z"))
          (.toInstant (OffsetDateTime/parse "2020-06-28T20:00:04.923Z"))
          (ChronoUnit/MILLIS))
  (.atZone (.toInstant (OffsetDateTime/parse "2020-06-28T20:00:00Z")) (ZoneId/systemDefault)))

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

(def initial-state
  {:prev nil})

(comment
  (string/replace "20,298,103" "," ""))

(defn advance
  [state input now]
  (try
    (let [new-value (-> input
                        (string/replace "," "")
                        Float/parseFloat)
          {:keys [value ts] :as prev} (first (:prev state))
          millis (when prev (.until ts now ChronoUnit/MILLIS))
          value-change (when prev (- value new-value))
          rate-ms (when prev (/ value-change millis))
          remaining-ms (when rate-ms (/ new-value rate-ms))
          finish-time (when remaining-ms (.plus now (long remaining-ms) ChronoUnit/MILLIS))
          formatted-rate (when rate-ms (format "\nrate: %02f/s" (* rate-ms 1000.0)))
          formatted-remaining (when remaining-ms (format "\nremaining time: %02f s" (/ remaining-ms 1000.0)))
          formatted-finish (when finish-time (format "\nestimated finish: %s" (.atZone finish-time (ZoneId/systemDefault))))
          formatted-now (.atZone now (ZoneId/systemDefault))]
      ; (when rate
      ;   (prn :debug millis value-change (/ value-change millis) rate))
      {:prev [{:value new-value :ts now}]
       :report/finish formatted-finish
       :report/now formatted-now
       :report/rate formatted-rate
       :report/remaining formatted-remaining
       :report/txt (str "input: " new-value " at " formatted-now formatted-rate formatted-remaining formatted-finish)})
    (catch NumberFormatException _e
      {})))

(defn -main [& args]
  (let [parsed (parse-opts args cli-options :in-order true)
        {:keys [_options]} parsed]
    (or (some->> (opts/find-errors parsed)
                 (opts/format-help progname help parsed)
                 (opts/print-and-exit))
        (do
          (println "Enter values on separate lines. Rates will be calculated.")
          (loop [inputs user/*input*
                 state initial-state]
            (when-let [next-line (first inputs)]
              (let [next-state (advance state next-line (Instant/now))]
                (some-> next-state :report/txt println)
                (recur (next inputs) next-state))))))))
