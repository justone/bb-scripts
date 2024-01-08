(ns empath
  (:require
    [clojure.java.io :as io]
    [clojure.string :as string]
    [clojure.tools.cli :refer [parse-opts]]

    [cheshire.core :as json]
    [doric.org :as dorig.org]
    [doric.core :as doric]

    [scribe.opts :as opts]
    [scribe.string]
    ))

(def script-name (opts/detect-script-name))


;; Common utilities

(defn analyze
  [path]
  (for [part (string/split path #":")]
    (let [f (io/file part)]
      {:element part
       :exists (.exists f)
       :dir (.isDirectory f)
       :file (.isFile f)
       :can-write (.canWrite f)})))

(defn get-path
  [options]
  (or (:path options)
      (string/trim (slurp *in*))))


;; Print subcommand

(def print-options
  [["-h" "--help" "Show help"]
   ["-t" "--table" "Print in a table"]
   ["-e" "--edn" "Print raw edn"]
   ["-j" "--json" "Print raw json"]
   ["-p" "--plain" "Print one entry per line"]])

(def print-usage
  (scribe.string/dedent
    "    "
    "Print the elements of a path in various ways."))

(defn prepare-output
  [options analyzed]
  (when (seq analyzed)
    (cond
      (:plain options)
      (->> (map :element analyzed)
           (string/join "\n"))

      (:json options)
      (->> (map #(json/generate-string %) analyzed)
           (string/join "\n"))

      (:edn options)
      (->> (map pr-str analyzed)
           (string/join "\n"))

      (or (empty? options) (:table options))
      (doric/table [:element :exists :dir :file :can-write] analyzed))))

(defn handle-print
  [global-options subargs]
  (let [parsed (parse-opts subargs print-options)
        {:keys [options]} parsed]
    (or (some-> (opts/validate parsed print-usage)
                (opts/format-help (str script-name " print") parsed)
                (opts/print-and-exit))
        (->> (get-path global-options)
             (analyze)
             (prepare-output options)
             (println)))))


;; Edit subcommand

(def edit-options
  [["-h" "--help" "Show help"]
   ["-e" "--empty" "Start with empty path"]])

(def edit-usage
  (scribe.string/dedent
    "    "
    "Edit elements of a path.

    Takes a list of action/element pairs. Valid actions are:

      append [element] - append element to end of path
      remove [element] - remove element from path
      prepend [element] - prepend element to beginning of path
      xappend [element] - append element after removing from rest of path
      xprepend [element] - prepend element after removing from rest of path
    "))

(defn munge-path
  [path args]
  (let [parts (into [] (string/split path #":"))]
    (string/join
      ":"
      (reduce
        (fn [result [op arg]]
          (case op
            "prepend" (into [arg] result)
            "append" (conj result arg)
            "remove" (into [] (remove #(= arg %)) result)
            "xappend" (conj (into [] (remove #(= arg %)) result) arg)
            "xprepend" (into [arg] (remove #(= arg %) result))))
        parts (partition 2 args)))))

(defn find-edit-errors
  [parsed]
  (or (opts/validate parsed edit-usage)
      (let [{:keys [arguments]} parsed]
        (cond
          (odd? (count arguments))
          {:message "Even number of arguments expected."
           :exit 1}))))

(defn handle-edit
  [global-options subargs]
  (let [parsed (parse-opts subargs edit-options :in-order true)
        {:keys [options arguments]} parsed]
    (or (some-> (find-edit-errors parsed)
                (opts/format-help (str script-name " edit") parsed)
                (opts/print-and-exit))
        (let [path (if (:empty options)
                     ":"
                     (get-path global-options))]
          (println (munge-path path arguments))))))


;; Main

(def cli-options
  [["-h" "--help" "Show help"]
   ["-p" "--path PATH" "Specify path to operate on"]])

(def global-usage
  (scribe.string/dedent
    "    "
    "Path inspection and manipulation tool.

    Manipulate and inspect path-like data with ease. Path data is a
    string delimited by colons.

    To specify which path to use, pass it via stdin or use the -p global
    option.

    Available subcommands:

      print - print a path in a variety of ways
      edit - edit a path by adding and removing elements

    Pass '-h' to see further help on each subcommand."))

(defn process
  [options arguments]
  (let [[subcommand & subargs] arguments]
    (case subcommand
      "print" (handle-print options subargs)
      "edit" (handle-edit options subargs)
      )))

(def subcommands #{:print :edit})

(defn find-errors
  [parsed]
  (or (opts/validate parsed global-usage)
      (let [{:keys [arguments]} parsed
            subcommand (-> arguments first keyword)]
        (cond
          (nil? subcommand)
          {:exit 1}

          (not (contains? subcommands subcommand))
          {:message (str "Invalid subcommand: " (name subcommand))
           :exit 1}))))

(defn -main [& args]
  (let [parsed (parse-opts args cli-options :in-order true)
        {:keys [options arguments]} parsed]
    (or (some-> (find-errors parsed)
                (opts/format-help script-name parsed)
                (opts/print-and-exit))
        (process options arguments))))
