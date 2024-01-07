(ns empath-test
  (:require
    [clojure.edn :as edn]
    [clojure.string :as string]
    [clojure.test :refer [deftest is testing]]

    [cheshire.core :as json]

    [empath :refer [prepare-output munge-path analyze]]
    ))

(def table-result
  (str "|----------------+--------+------+-------+-----------|\n"
       "|     Element    | Exists |  Dir |  File | Can Write |\n"
       "|----------------+--------+------+-------+-----------|\n"
       "| /usr/bin       | true   | true | false | false     |\n"
       "| /usr/local/bin | true   | true | false | false     |\n"
       "|----------------+--------+------+-------+-----------|"))

(deftest print-path
  (testing "prepare"
    (is (= table-result
           (prepare-output {} (analyze "/usr/bin:/usr/local/bin"))))
    (is (= table-result
           (prepare-output {:table true} (analyze "/usr/bin:/usr/local/bin"))))
    (is (= [{"element" "/usr/bin" "exists" true "dir" true "file" false "can-write" false}
            {"element" "/usr/local/bin" "exists" true "dir" true "file" false "can-write" false}]
           (-> (prepare-output {:json true} (analyze "/usr/bin:/usr/local/bin"))
               (string/split #"\n")
               (->> (map json/parse-string)))))
    (is (= [{:element "/usr/bin" :exists true :dir true :file false :can-write false}
            {:element "/usr/local/bin" :exists true :dir true :file false :can-write false}]
           (-> (prepare-output {:edn true} (analyze "/usr/bin:/usr/local/bin"))
               (string/split #"\n")
               (->> (map edn/read-string)))))
    (is (= (str "/usr/bin\n"
                "/usr/local/bin")
           (prepare-output {:plain true} (analyze "/usr/bin:/usr/local/bin"))))
    ))

(deftest edit-path
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
