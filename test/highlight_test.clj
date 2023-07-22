(ns highlight-test
  (:require [babashka.process :as process]
            [clojure.test :refer [deftest is testing]]
            [clojure.tools.cli :refer [parse-opts]]
            [highlight :as uut]
            [lib.highlight :as highlight]))

(defn- parse-args
  [cli]
  (-> (process/tokenize cli)
      (parse-opts uut/cli-options)))

(defn- args->color-opts
  [cli]
  (-> (parse-args cli)
      (uut/parsed->color-opts)))

(defn- args->errors
  [cli]
  (-> (parse-args cli)
      (uut/find-errors)))

(defn- args->color-opts-stringify-regex
  [cli]
  (let [[regex opts] (args->color-opts cli)]
    [(str regex) opts]))

(deftest opts-parsing
  (is (= ["foo"
          {:colors highlight/colors-for-dark
           :explicit {}
           :offset 0
           :reverse? false}]
         (args->color-opts-stringify-regex "foo")))

  (is (= ["foo|bar|baz"
          {:colors highlight/colors-for-dark
           :explicit {"foo" 196, "bar" 220, "baz" 105}
           :offset 0
           :reverse? false}]
         (args->color-opts-stringify-regex "foo|bar|baz -e 5,0,0:foo -e 5,4,0:bar -e 2,2,5:baz")))

  (is (= ["foo"
          {:colors highlight/colors-for-light
           :explicit {}
           :offset 0
           :reverse? false}]
         (args->color-opts-stringify-regex "--light foo")))

  (is (= ["foo"
          {:colors highlight/colors-for-dark
           :explicit {}
           :offset 123
           :reverse? false}]
         (args->color-opts-stringify-regex "-o 123 foo")))

  (let [[regex {:keys [offset] :as color-opts}] (args->color-opts-stringify-regex "-r foo")]
    (is (= regex "foo"))
    (is (= {:colors highlight/colors-for-dark
            :reverse? false}
           (select-keys color-opts [:colors :reverse?])))
    (is (<= 0 offset 255)))

  (is (= ["foo"
          {:colors highlight/colors-for-dark
           :explicit {}
           :offset 123
           :reverse? true}]
         (args->color-opts-stringify-regex "-R -o 123 foo"))))

(deftest error-detect
  (is (= {:message "Pass regex to match."
          :exit 1}
         (args->errors "")))

  (is (= {:message "Bad explicit arguments: foo, 3,7:foo"
          :exit 1}
         (args->errors "foo -e foo -e 3,7:foo"))))

(deftest color-wrapping
  (testing "vanilla regex"
    (is (= "[38;5;96mfoo[0m bar baz"
           (apply highlight/add "foo bar baz" (args->color-opts "foo"))))
    (is (= "[38;5;96mfoo[0m bar [38;5;190mbaz[0m"
           (apply highlight/add "foo bar baz" (args->color-opts "foo|baz"))))
    (is (= "[38;5;160mfoo[0m bar [38;5;49mbaz[0m"
           (apply highlight/add "foo bar baz" (args->color-opts "--light foo|baz")))))

  (testing "offset makes colors different"
    (is (= "[38;5;106mfoo[0m bar baz"
           (apply highlight/add "foo bar baz" (args->color-opts "-o 10 foo"))))
    (is (= "[38;5;106mfoo[0m bar [38;5;200mbaz[0m"
           (apply highlight/add "foo bar baz" (args->color-opts "-o 10 foo|baz")))))

  (testing "reverse moves color further apart"
    (is (= "[38;5;40mfoo1[0m [38;5;41mfoo2[0m [38;5;42mfoo3[0m"
           (apply highlight/add "foo1 foo2 foo3" (args->color-opts "foo\\d"))))
    (is (= "[38;5;170mfoo1[0m [38;5;32mfoo2[0m [38;5;111mfoo3[0m"
           (apply highlight/add "foo1 foo2 foo3" (args->color-opts "-R foo\\d")))))

  (testing "explicit color specifying"
    (is (= "[38;5;196mfoo1[0m [38;5;220mbar2[0m [38;5;105mbaz3[0m"
           (apply highlight/add "foo1 bar2 baz3" (args->color-opts "(foo|bar|baz)\\d -e 5,0,0:foo1 -e 5,4,0:bar2 -e 2,2,5:baz3"))))
    (is (= "[38;5;226mfoo1[0m [38;5;128mbar2[0m [38;5;57mbaz3[0m"
           (apply highlight/add "foo1 bar2 baz3" (args->color-opts "(foo|bar|baz)\\d -e 226:foo1 -e 128:bar2 -e 57:baz3"))))))
