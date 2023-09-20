(ns rater-test
  (:require [clojure.test :refer [deftest is testing]])
  (:import (java.time Instant)
           (java.time.format DateTimeFormatter)))

(comment
  (Instant/parse)
  (-> (.parse DateTimeFormatter/ISO_LOCAL_DATE "2022-04-03")
      (Instant/from))
  (Instant/parse "2011-12-03T10:15:30Z"))

(deftest advance
  (testing "main path"
    (is true)))
