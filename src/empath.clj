(ns empath
  (:require
    [clojure.java.io :as io]
    [clojure.string :as string]
    [clojure.tools.cli :refer [parse-opts]]

    [cheshire.core :as json]
    [doric.org :as dorig.org]
    [doric.core :as doric]

    [lib.opts2 :as opts]
    [lib.string]
    ))

(def progname (str *ns*))


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

(def print-help
  (lib.string/dedent
    "    "
    "Print the elements of a path in various ways."))

(defn handle-print
  [global-options subargs]
  (let [parsed (parse-opts subargs print-options)
        {:keys [options]} parsed]
    (or (when-some [errors (opts/find-errors parsed)]
          (->> (opts/format-help (str progname " print") print-help parsed errors)
               (opts/print-and-exit)))
        (let [path (get-path global-options)
              analyzed (analyze path)]
          (when (seq analyzed)
            (cond
              (:plain options)
              (run! #(println (:element %)) analyzed)

              (:json options)
              (run! #(println (json/generate-string %)) analyzed)

              (:edn options)
              (run! prn analyzed)

              (or (empty? options) (:table options))
              (->> analyzed
                   (doric/table [:element :exists :dir :file :can-write])
                   println)
              ))))))


;; Edit subcommand

(def edit-options
  [["-h" "--help" "Show help"]
   ["-e" "--empty" "Start with empty path"]])

(def edit-help
  (lib.string/dedent
    "    "
    "Edit elements of a path.

    Takes a list of action/element pairs. Valid actions are:

      append [element] - append element to end of path
      remove [element] - remove element from path
      prepend [element] - prepsent element to beginning of path
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
  (or (opts/find-errors parsed)
      (let [{:keys [arguments]} parsed]
        (cond
          (odd? (count arguments))
          {:message "Even number of arguments expected."
           :exit 1}))))

(defn handle-edit
  [global-options subargs]
  (let [parsed (parse-opts subargs edit-options :in-order true)
        {:keys [options arguments]} parsed]
    (or (when-some [errors (find-edit-errors parsed)]
          (->> (opts/format-help (str progname " edit") edit-help parsed errors)
               (opts/print-and-exit)))
        (let [path (if (:empty options)
                     ":"
                     (get-path global-options))]
          (println (munge-path path arguments))))))


;; Main

(def cli-options
  [["-h" "--help" "Show help"]
   ["-p" "--path PATH" "Specify path to operate on"]])

(def help
  (lib.string/dedent
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
  (or (opts/find-errors parsed)
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
    (or (when-some [errors (find-errors parsed)]
          (->> (opts/format-help progname help parsed errors)
               (opts/print-and-exit)))
        (process options arguments))))
