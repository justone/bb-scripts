(ns ftime
  (:require
    [clojure.tools.cli :refer [parse-opts]]

    [lib.opts :as opts]
    [lib.time :as time])
  (:gen-class))

(def progname "ftime")

(def cli-options
  [["-f" "--format FORMAT" "Format to use to print the time"
    :default "datetime"]
   ["-z" "--timezone ZONE" "Timezone to use"
    :default "America/Los_Angeles"]
   ["-h" "--help"]])

(defn find-errors
  [parsed]
  (or (opts/find-errors parsed)
      (let [{:keys [arguments]} parsed]
        (cond
          (zero? (count arguments))
          {:message "pass in millis as the first argument"
           :exit 1}))))

(defn process
  [millis options]
  (let [{:keys [format timezone]} options]
    (-> (time/millis->datetime millis timezone)
        (time/format-datetime format))))

(defn -main [& args]
  (let [parsed (parse-opts args cli-options)
        {:keys [options arguments]} parsed]
    (or (some->> (find-errors parsed)
                 (opts/print-errors progname parsed)
                 (System/exit))
        (-> (process (-> arguments first Long.) options)
            (println)))))
