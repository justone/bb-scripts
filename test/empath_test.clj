(ns empath-test
  (:require
    [clojure.test :refer :all]
    [empath :refer :all]
    ))

(deftest edit
  (testing "munge"
    (is (= "/usr/bin"
           (munge-path ":" ["append" "/usr/bin"])))
    (is (= "/usr/bin:/usr/local/bin:/home/nate/bin"
           (munge-path "/usr/bin:/usr/local/bin" ["append" "/home/nate/bin"])))
    (is (= "/home/nate/bin:/usr/bin:/usr/local/bin"
           (munge-path "/usr/bin:/usr/local/bin" ["prepend" "/home/nate/bin"])))
    (is (= "/usr/local/bin:/usr/bin"
           (munge-path "/usr/bin:/usr/local/bin" ["xprepend" "/usr/local/bin"])))
    (is (= "/usr/local/bin:/usr/bin"
           (munge-path "/usr/bin:/usr/local/bin" ["xappend" "/usr/bin"])))
    (is (= "/usr/local/bin"
           (munge-path "/usr/bin:/usr/local/bin" ["remove" "/usr/bin"])))
    (is (= "/usr/local/bin:/home/nate/bin"
           (munge-path "/usr/bin:/usr/local/bin" ["remove" "/usr/bin" "append" "/home/nate/bin"])))
    )
  )
