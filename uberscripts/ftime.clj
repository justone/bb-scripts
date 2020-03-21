(ns lib.time
  (:import
    (java.time ZoneId
               Instant)
    (java.time.format DateTimeFormatter)))

(def default-zone "America/Los_Angeles")

(defn zone-id
  ([]
   (zone-id default-zone))
  ([zone]
   (ZoneId/of zone)))

(defn millis->datetime
  [millis zone]
  (let [inst (Instant/ofEpochMilli millis)]
    (.atZone inst (zone-id zone))))

(defn find-format
  [spec]
  (case spec
    "instant" DateTimeFormatter/ISO_INSTANT
    "date" DateTimeFormatter/ISO_LOCAL_DATE
    "datetime" DateTimeFormatter/ISO_OFFSET_DATE_TIME
    (java.time.format.DateTimeFormatter/ofPattern spec)))

(defn format-datetime
  [datetime fmt]
  (.format datetime (find-format fmt)))


#_(-> (millis->datetime 1584806479743 "America/Los_Angeles")
      (format-datetime "instant"))
(ns lib.opts
  (:require
    [clojure.string :as string]
    ))

(defn print-usage
  [progname summary]
  (println "usage: " progname " [opts]")
  (println " ")
  (println "options:")
  (println summary))

(defn find-errors
  [parsed]
  (let [{:keys [errors options]} parsed
        {:keys [help]} options]
    (cond
      help
      {:exit 0}

      errors
      {:message (string/join "\n" errors)
       :exit 1}
      )))

(defn print-errors
  [progname parsed errors]
  (let [{:keys [summary]} parsed
        {:keys [message exit]} errors]
    (when message
      (println message)
      (println " "))
    (print-usage progname summary)
    exit))
(ns ftime
  (:require
    [clojure.tools.cli :refer [parse-opts]]

    [lib.opts :as opts]
    [lib.time :as time])
  (:gen-class))

(def progname "ftime")

(def cli-options
  [["-f" "--format FORMAT" "Format to use to print the time"
    :default "instant"]
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
(ns user (:require [ftime])) (apply ftime/-main *command-line-args*)