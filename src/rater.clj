(ns rater
  (:require [clojure.string :as string]
            [clojure.tools.cli :refer [parse-opts]]
            [clj-commons.humanize :as humanize]
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

(defn least-squares
  [xys]
  (let [{:keys [x y x2 xy] :as foo} (reduce
                                      (fn [acc [x y]]
                                        (-> acc
                                            (update :x + x)
                                            (update :y + y)
                                            (update :x2 + (* x x))
                                            (update :xy + (* x y))))
                                      {:x 0.0 :y 0.0 :x2 0.0 :xy 0.0}
                                      xys)
        n (count xys)
        m (/ (- (* n xy)
                (* x y))
             (- (* n x2)
                (* x x)))
        b (/ (- y (* m x))
             n)]
    {:m m :b b}))

(comment
  (least-squares [[2 4] [3 5] [5 7] [7 10] [9 15]])
  (let [{:keys [m b]} (least-squares [[2 4] [3 5] [5 7] [7 10] [9 15]])]
    ; (+ (* m 8) b)
    (/ (- b) m)))



;; Calculate with https://www.mathsisfun.com/data/least-squares-regression.html
(defn advance
  [state input now]
  (try
    (let [[amount time-str] (string/split input #"\s")
          new-value (-> amount
                        (string/replace "," "")
                        Float/parseFloat)
          now (if time-str
                (Instant/parse time-str)
                now)
          {:keys [value ts] :as prev} (first (:prev state))
          millis (when prev (.until ts now ChronoUnit/MILLIS))
          value-change (when prev (- value new-value))
          rate-ms (when prev (/ value-change millis))
          per-ms (when prev (/ millis value-change))
          remaining-ms (when rate-ms (/ new-value rate-ms))
          finish-time (when remaining-ms (.plus now (long remaining-ms) ChronoUnit/MILLIS))
          formatted-rate (when rate-ms (format "\nrate: %02f/s" (* rate-ms 1000.0)))
          formatted-remaining (when remaining-ms (format "\nremaining time: %s" (humanize/duration remaining-ms)))
          formatted-per (when per-ms (format "\ntime per: %s" (humanize/duration per-ms)))
          formatted-finish (when finish-time (format "\nestimated finish: %s" (.atZone finish-time (ZoneId/systemDefault))))
          formatted-now (.atZone now (ZoneId/systemDefault))]
      ; (when rate
      ;   (prn :debug millis value-change (/ value-change millis) rate))
      (println (str input " " now))
      {:prev [{:value new-value :ts now}]
       :report/finish formatted-finish
       :report/now formatted-now
       :report/rate formatted-rate
       :report/remaining formatted-remaining
       :report/txt (str "input: " new-value " at " formatted-now formatted-rate formatted-per formatted-remaining formatted-finish)})
    (catch NumberFormatException _e
      {})))

; "2023-12-03T10:15:30.00Z"
; "2023-08-29T21:46:02.00Z"

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
