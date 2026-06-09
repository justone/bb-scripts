(ns cap
  (:require [cap.db :as db]
            [cap.multi :as multi]
            [cap.xdg :as xdg]
            [doric.org]
            [doric.core :as doric]
            [babashka.fs :as fs]
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
  [{:keys [id name line-count directory ago]}]
  (format "%d: %s (in %s, %d lines) %s" id name directory line-count ago))

(defn interactive-picker
  [data]
  (:out (p/shell {:continue true :in (str/join "\n" data) :out :string} (format "fzf --ansi --no-sort --reverse --tiebreak=index --bind=ctrl-d:preview-page-down --bind=ctrl-u:preview-page-up --header \"Enter prints lines, CTRL-C exits\" --preview \"echo {} | cut -d: -f1 | head -1 | xargs -I %% sh -c '%s get --limit 10 -c %%'\"" self-script))))

(defn- find-capture
  [config {:keys [all-sessions all-directories id interactive]}]
  (let [args (cond-> {}
               (not all-sessions) (assoc :session (current-session))
               (not all-directories) (assoc :directory (current-directory)))]
    (cond
      id (db/get-capture config id)
      interactive (let [result (interactive-picker (->> (db/find-captures config args {:limit 100})
                                                        (map #(assoc % :ago (calculate-ago (ZonedDateTime/now) (:created-at %))))
                                                        (map simple-line)))]
                    (when-some [picked (when (seq result)
                                         (-> (str/split result #":")
                                             first
                                             parse-long))]
                      (db/get-capture config picked)))
      :else (first (db/find-captures config args {:limit 1})))))

(defn get-captures
  [config {:keys [limit] :as opts}]
  (let [capture (find-capture config opts)]
   (when capture
    (run! println (map :line (db/get-lines config capture {:limit limit}))))))

(defn set-comment
  [config opts comment]
  (let [capture (find-capture config opts)]
    (when capture
      (db/set-comment config capture comment))))

(defn list-captures
  [config {:keys [all-sessions all-directories limit list-raw]}]
  (let [args (cond-> {}
               (not all-sessions) (assoc :session (current-session))
               (not all-directories) (assoc :directory (current-directory)))
        enriched-captures (->> (db/find-captures config args {:limit limit})
                               (map #(assoc % :ago (calculate-ago (ZonedDateTime/now) (:created-at %)))))]
    (cond
      list-raw (run! (comp println simple-line) enriched-captures)
      (empty? enriched-captures) (println "No captures.")
      :else (->> enriched-captures
                 (doric/table [{:name :id, :align :right}
                               :name
                               :directory
                               :session
                               :ago
                               {:name :line-count, :title "Lines", :align :right}
                               :comment])
                 println))))

(def opts
  {:cli-options [["-h" "--help" "Show help"]
                 ["-d" "--db DB" "Database file"]]
   :usage "Capture and retrieve output in the shell.

          Available subcommands:

          add - Add a new capture
          comment - Add comment to an existing capture
          get - Retrieve a previous capture
          init - Initialize database for capture data
          list - List previous captures
          shell-init - Print out exports to initialize the shell session.

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
                                     ["-c" "--id ID" "Retrieve capture by id"]
                                     ["-I" "--interactive" "Interactively select capture"]
                                     ["-n" "--limit LIMIT" "Number of lines to return" :default 100]]
                       :usage "Retrieve a capture.

                              This will print the most recent capture by default, or a given capture
                              by id. To interactively select a capture, use the -I flag."}
                 :comment {:cli-options [["-h" "--help" "Show help"]
                                         ["-S" "--all-sessions" "Return captures from all sessions."]
                                         ["-D" "--all-directories" "Return captures from all directories."]
                                         ["-c" "--id ID" "Retrieve capture by id"]
                                         ["-I" "--interactive" "Interactively select capture"]]
                           :validate-fn (fn [{:keys [arguments]}]
                                          (when-not (seq arguments)
                                            {:exit 1
                                             :message "Error: pass comment as first argument."}))
                           :extra-usage-args "[comment]"
                           :usage "Add comment on a capture.

                                  This will operate on the most recent capture by default, or a given
                                  capture by id. To interactively select a capture, use the -I flag."}
                 :shell-init {:cli-options [["-h" "--help" "Show help"]]
                        :usage "Emit shell initialization."}
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
        db-location (or (-> parsed first :options :db)
                        (str (fs/xdg-data-home "cap/captures.db")))
        conn (db/connect db-location)
        config {:db/location db-location :db/conn conn}]
    (when-not (fs/exists? db-location)
      (some->> db-location fs/parent fs/create-dirs))
    ; (pprint parsed)
    ; (pprint config)
    (try
      (case (-> parsed second :command)
        :add (capture config combined-options)
        :get (get-captures config combined-options)
        :comment (set-comment config combined-options (-> parsed second :arguments first))
        :list (list-captures config combined-options)
        :shell-init (println (shell-init-str))
        :init (init config combined-options))
      (finally
        (db/close conn)))))
