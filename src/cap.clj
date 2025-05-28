(ns cap
  (:require [cap.db :as db]
            [cap.multi :as multi]
            [doric.org]
            [doric.core :as doric]
            [babashka.process :as p]
            [clojure.string :as str]
            [clojure.pprint :refer [pprint]])
  (:import (java.time Duration ZonedDateTime)))

(def self-script (System/getProperty "babashka.file"))

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

(defn simple-line
  [{:keys [id name directory ago]}]
  (format "%d: %s (in %s) %s" id name directory ago))

(defn interactive-picker
  [data]
  (p/shell {:continue true :in (str/join "\n" data)} (format "fzf --ansi --no-sort --reverse --tiebreak=index --bind=ctrl-d:preview-page-down --bind=ctrl-u:preview-page-up --header \"Enter prints lines, CTRL-C exits\" --preview \"echo {} | cut -d: -f1 | head -1 | xargs -I %% sh -c '%s get --limit 10 -c %%'\" --bind \"enter:execute(echo {} | cut -d: -f1 | head -1 | xargs -I %% sh -c '%s get -c %%')+abort\"" self-script self-script)))

(defn get-captures
  [config {:keys [all-sessions all-directories previous limit id interactive]}]
  (let [args (cond-> {}
               (not all-sessions) (assoc :session (current-session))
               (not all-directories) (assoc :directory (current-directory)))
        captures (db/find-captures config args {:limit (if previous 1 limit)})]
    (cond
      id (run! println (map :line (db/get-lines config (db/get-capture config id) {:limit limit})))
      interactive (interactive-picker (->> (map #(assoc % :ago (calculate-ago (ZonedDateTime/now) (:created-at %))) captures)
                    (map simple-line)))
      previous (run! println (map :line (db/get-lines config (first captures) {:limit limit}))))))

(defn list-captures
  [config {:keys [all-sessions all-directories previous limit list-raw]}]
  (let [args (cond-> {}
               (not all-sessions) (assoc :session (current-session))
               (not all-directories) (assoc :directory (current-directory)))
        captures (db/find-captures config args {:limit (if previous 1 limit)})]
    (cond
      list-raw (->> (map #(assoc % :ago (calculate-ago (ZonedDateTime/now) (:created-at %))) captures)
                    (run! (comp println simple-line)))
      :else (->> (map #(assoc % :ago (calculate-ago (ZonedDateTime/now) (:created-at %))) captures)
                 (doric/table [:name :directory :session :ago {:name :line_count :title "Line Count"}])
                 println))))

(def opts
  {:cli-options [["-h" "--help" "Show help"]
                 ["-d" "--db DB" "Database file"
                  :default "cap.db"]]
   :usage "Capture and retrieve output in the shell.

          Available subcommands:

          add - Add a new capture
          get - Retrieve a previous capture
          init - Initialize database for capture data
          list - List previous captures

          Pass '-h' to see further help on each subcommand."
   :subcommands {:add {:cli-options [["-h" "--help" "Show help"]
                                     ["-n" "--name NAME" "Capture name."]
                                     ["-d" "--directory DIRECTORY" "Directory to associate with capture."]
                                     ["-s" "--session SESSION" "Session to associate with capture."]]
                       :usage "Adds a capture."}
                 :init {:cli-options [["-h" "--help" "Show help"]]
                        :usage "Initialize."}
                 :get {:cli-options [["-h" "--help" "Show help"]
                                     ["-S" "--all-sessions" "Return captures from all sessions."]
                                     ["-D" "--all-directories" "Return captures from all directories."]
                                     ["-p" "--previous" "Retrieve the most previous capture" :default true]
                                     ["-c" "--id ID" "Retrieve capture by id"]
                                     ["-I" "--interactive" "Interactively select capture"]
                                     ["-n" "--limit LIMIT" "Number of lines to return" :default 100]]
                       :usage "Retrieve a capture."}
                 :list {:cli-options [["-h" "--help" "Show help"]
                                      ["-S" "--all-sessions" "Return captures from all sessions."]
                                      ["-D" "--all-directories" "Return captures from all directories."]
                                      ["-r" "--list-raw" "List captures 1 per line"]
                                      ["-n" "--limit LIMIT" "Number of captures to list" :default 10]]
                        :usage "List captures."}
                 }})

(defn init
  [config _opts]
  (db/init config)
  (println "Initialization complete."))

(defn -main [& _args]
  (let [parsed (multi/entry opts)
        combined-options (apply merge (map :options parsed))
        config {:db/location (-> parsed first :options :db)}]
    ; (pprint parsed)
    ; (pprint config)
    (case (-> parsed second :command)
      :add (capture config combined-options)
      :get (get-captures config combined-options)
      :list (list-captures config combined-options)
      :init (init config combined-options))))
