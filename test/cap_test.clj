(ns cap-test
  (:require [clojure.test :refer [deftest is testing]]
            [cap :refer [format-duration]])
  (:import (java.time Duration)))

(defn duration-from
  "Helper to create Duration objects from various time components"
  ([seconds]
   (Duration/ofSeconds seconds))
  ([hours minutes seconds]
   (-> (Duration/ofHours hours)
       (.plusMinutes minutes)
       (.plusSeconds seconds)))
  ([days hours minutes seconds]
   (-> (Duration/ofDays days)
       (.plusHours hours)
       (.plusMinutes minutes)
       (.plusSeconds seconds))))

(deftest format-duration-test
  (testing "minutes and seconds format (< 1 hour)"
    (is (= "0m0s" (format-duration (duration-from 0))))
    (is (= "0m30s" (format-duration (duration-from 30))))
    (is (= "5m0s" (format-duration (duration-from 300))))
    (is (= "5m30s" (format-duration (duration-from 330))))
    (is (= "59m59s" (format-duration (duration-from 3599)))))

  (testing "hours and minutes format (1 hour to < 1 day)"
    (is (= "1h0m" (format-duration (duration-from 3600))))
    (is (= "1h30m" (format-duration (duration-from 1 30 0))))
    (is (= "12h0m" (format-duration (duration-from 12 0 0))))
    (is (= "23h59m" (format-duration (duration-from 23 59 0)))))

  (testing "days and hours format (1 day to < 7 days)"
    (is (= "1d0h" (format-duration (duration-from 1 0 0 0))))
    (is (= "1d12h" (format-duration (duration-from 1 12 0 0))))
    (is (= "3d6h" (format-duration (duration-from 3 6 30 45))))
    (is (= "6d23h" (format-duration (duration-from 6 23 59 59)))))

  (testing "days only format (>= 7 days)"
    (is (= "7d" (format-duration (duration-from 7 0 0 0))))
    (is (= "7d" (format-duration (duration-from 7 12 30 45))))
    (is (= "30d" (format-duration (duration-from 30 0 0 0))))
    (is (= "365d" (format-duration (duration-from 365 0 0 0)))))

  (testing "boundary conditions"
    ;; Test exact thresholds
    (is (= "59m59s" (format-duration (Duration/ofSeconds 3599))))
    (is (= "1h0m" (format-duration (Duration/ofSeconds 3600))))
    (is (= "23h59m" (format-duration (Duration/ofSeconds 86340))))
    (is (= "1d0h" (format-duration (Duration/ofSeconds 86400))))
    (is (= "6d23h" (format-duration (Duration/ofSeconds 604740))))
    (is (= "7d" (format-duration (Duration/ofSeconds 604800)))))

  (testing "edge cases"
    ;; Very small durations
    (is (= "0m0s" (format-duration (Duration/ofMillis 500))))
    (is (= "0m1s" (format-duration (Duration/ofMillis 1500))))

    ;; Large durations
    (is (= "1000d" (format-duration (Duration/ofDays 1000))))
    (is (= "999d" (format-duration (duration-from 999 23 59 59)))))

  (testing "real-world scenarios"
    ;; Common command durations users might see
    (is (= "0m30s" (format-duration (Duration/ofSeconds 30))))
    (is (= "2m15s" (format-duration (Duration/ofSeconds 135))))
    (is (= "1h30m" (format-duration (Duration/ofMinutes 90))))
    (is (= "2d12h" (format-duration (Duration/ofHours 60))))
    (is (= "14d" (format-duration (Duration/ofDays 14))))))
