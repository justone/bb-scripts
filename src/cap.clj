(ns cap
  (:require [cap.db :as db]
            [cap.multi :as multi]
            [doric.core :as doric]
            [clojure.pprint :refer [pprint]])
  (:import (java.time Duration ZonedDateTime)))

(defn current-directory [] (System/getenv "PWD"))
(defn current-session [] (or (System/getenv "CAP_SESSION_ID") "no-session"))

(defn capture
  [config options]
  (let [capture-name (:name options)
        capture (db/add-capture config capture-name (current-directory) (current-session) {})]
    (loop [line (read-line)]
      (when line (db/add-line config capture line)
        (println line)
        (recur (read-line))))))

(defn format-duration
  [duration]
  (cond
    (> 1 (.toHours duration))
    (str (.toMinutesPart duration) "m" (.toSecondsPart duration) "s")

    (> 1 (.toDays duration))
    (str (.toHoursPart duration) "h" (.toMinutesPart duration) "m")

    (> 7 (.toDays duration))
    (str (.toDaysPart duration) "d" (.toHoursPart duration) "h")

    :else
    (str (.toDaysPart duration) "d")))

(defn calculate-ago
  [now dt]
  (format-duration (Duration/between dt now)))

(defn shell-init-str
  []
  (format "export CAP_SESSION_ID=%s" (random-uuid)))

(defn get-captures
  [config {:keys [all-sessions all-directories previous]}]
  (let [args (cond-> {}
               (not all-sessions) (assoc :session (current-session))
               (not all-directories) (assoc :directory (current-directory)))
        captures (db/find-captures config args {:limit (if previous 1 100)})]
    (cond
      previous (run! println (map :line (db/get-lines config (first captures) nil)))
      :else (->> captures
                 (map #(assoc % :ago (calculate-ago (ZonedDateTime/now) (:created-at %))))
                 (doric/table [:name :directory :session :ago])
                 println))))

(def opts
  {:cli-options [["-h" "--help" "Show help"]
                 ["-d" "--db DB" "Database file"]]
   :usage "Capture and retrieve output in the shell.

          Available subcommands:

          add - Add a new capture
          get - Retrieve a previous capture

          Pass '-h' to see further help on each subcommand."
   :subcommands {:add {:cli-options [["-h" "--help" "Show help"]
                                     ["-n" "--name NAME" "Capture name."]
                                     ["-d" "--directory DIRECTORY" "Directory to associate with capture."]
                                     ["-s" "--session SESSION" "Session to associate with capture."]]
                       :usage "Adds a capture."}
                 :get {:cli-options [["-h" "--help" "Show help"]
                                     ["-S" "--all-sessions" "Return captures from all sessions."]
                                     ["-D" "--all-directories" "Return captures from all directories."]
                                     ["-p" "--previous" "Retrieve the most previous capture"]
                                     ["-l" "--list" "List recent captures"]]
                       ; :validate-fn (fn find-errors
                       ;                [parsed]
                       ;                (let [{{:keys [previous list]} :options} parsed]
                       ;                  (cond
                       ;                    (and (not previous) (not list))
                       ;                    {:exit 1
                       ;                     :message "Must chose --list or --previous"})))
                       :usage "Retrieve captures."}}})

(defn -main [& _args]
  (let [parsed (multi/entry opts)
        combined-options (apply merge (map :options parsed))
        config {:db/location "cap.db"}]
    (case (-> parsed second :command)
      :add (capture config combined-options)
      :get (get-captures config combined-options))))
