(ns lib.string-test
  (:require [clojure.test :refer [deftest is]]
            [lib.string]))

(deftest dedent
  (is (= (str "test\n"
              "foo\n"
              "bar")
         (lib.string/dedent "      " "test\n      foo\n      bar")
         (lib.string/dedent "test\n      foo\n      bar"))))
