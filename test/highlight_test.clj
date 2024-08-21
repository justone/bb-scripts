(ns highlight-test
  (:require [babashka.process :as process]
            [clojure.test :refer [deftest is]]
            [clojure.tools.cli :refer [parse-opts]]
            [highlight :as uut]
            [scribe.highlight :as highlight]))

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
