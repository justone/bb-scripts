(ns lib.script.alpha-test
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [lib.script.alpha :as sa]))

(def opts
  {:help "This script adds two numbers. Fancy, eh?

         Examples:

           SCRIPT_NAME -a 2 -b 4
           SCRIPT_NAME -a 3 -b 22"
   :progname "adder.clj"
   :cli-options [["-h" "--help" "Show help."]
                 ["-a" "--arg1 arg1" "First number to add." :missing "must supply first number." :parse-fn parse-long]
                 ["-b" "--arg2 arg2" "Second number to add." :missing "must supply second number." :parse-fn parse-long]]
   :validate-fn (fn [{{:keys [arg1 arg2]} :options}]
                  (cond (not arg1) {:message "arg1 must be a number" :exit 2}
                        (not arg2) {:message "arg2 must be b number" :exit 2}
                        (<= arg2 arg1)  {:message "arg2 must be greater than arg1" :exit 2}))})



(def header
  ["usage: adder.clj [opts]"
   ""])

(def usage
  ["This script adds two numbers. Fancy, eh?"
   ""
   "Examples:"
   ""
   "  adder.clj -a 2 -b 4"
   "  adder.clj -a 3 -b 22"])

(def options
  [""
   "options:"
   "  -h, --help       Show help."
   "  -a, --arg1 arg1  First number to add."
   "  -b, --arg2 arg2  Second number to add."])

(deftest test-simple-main
  (let [run-main (fn [opts args]
                   (try (sa/simple-main opts args)
                        (catch Exception e
                          {:help (str/split-lines (.getMessage e))
                           :exit (-> e ex-data :babashka/exit)})))]
    (testing "help"
      (is (= {:help (concat header usage options)
              :exit 0}
             (run-main opts ["-h"]))))

    (testing "errors"
      (is (= {:help (concat header ["Unknown option: \"-x\""] options)
              :exit 1}
             (run-main opts ["-a" "5" "-b" "3" "-x"])))

      (is (= {:help (concat header ["must supply second number."] options)
              :exit 1}
             (run-main opts ["-a" "3"])))

      (is (= {:help (concat header ["arg1 must be a number"] options)
              :exit 2}
             (run-main opts ["-a" "a" "-b" "3"])))

      (is (= {:help (concat header ["arg2 must be greater than arg1" ] options)
              :exit 2}
             (run-main opts ["-a" "5" "-b" "3"]))))

    (testing "success"
      (is (= {:arg1 3, :arg2 5}
             (:options (run-main opts ["-a" "3" "-b" "5"])))))))
