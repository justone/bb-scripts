(ns comb.template
  "Clojure templating library."
  (:refer-clojure :exclude [fn eval])
  (:require [clojure.core :as core]))

(defn- read-source [source]
  (if (string? source)
    source
    (slurp source)))

(def delimiters ["<%" "%>"])

(def parser-regex
  (re-pattern
   (str "(?s)\\A"
        "(?:" "(.*?)"
        (first delimiters) "(.*?)" (last delimiters)
        ")?"
        "(.*)\\z")))

(defn emit-string [s]
  (print "(print " (pr-str s) ")"))

(defn emit-expr [expr]
  (if (.startsWith expr "=")
    (print "(print " (subs expr 1) ")")
    (print expr)))

(defn- parse-string [src]
  (with-out-str
    (print "(do ")
    (loop [src src]
      (let [[_ before expr after] (re-matches parser-regex src)]
        (if expr
          (do (emit-string before)
              (emit-expr expr)
              (recur after))
          (do (emit-string after)
              (print ")")))))))

(defn compile-fn [args src]
  (core/eval
   `(core/fn ~args
      (with-out-str
        ~(-> src read-source parse-string read-string)))))

(defmacro fn
  "Compile a template into a function that takes the supplied arguments. The
  template source may be a string, or an I/O source such as a File, Reader or
  InputStream."
  [args source]
  `(compile-fn '~args ~source))

(defn eval
  "Evaluate a template using the supplied bindings. The template source may
  be a string, or an I/O source such as a File, Reader or InputStream."
  ([source]
     (eval source {}))
  ([source bindings]
     (let [keys (map (comp symbol name) (keys bindings))
           func (compile-fn [{:keys (vec keys)}] source)]
       (func bindings))))
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
(ns comb
  (:require
    [clojure.tools.cli :refer [parse-opts]]

    [lib.opts :as opts]
    [comb.template :as template]
    [user]
    )
  (:gen-class))

(def progname "comb")

(def cli-options
  [["-t" "--template TEMPLATE" "Template to process"]
   ["-h" "--help"]])

(defn process
  [data options]
  (let [{:keys [template]} options]
    (binding [*out* *err*]
      (println "data:" (pr-str data)))
    (template/eval template data)))

(defn -main [& args]
  (let [parsed (parse-opts args cli-options)
        {:keys [options]} parsed]
    (or (some->> (opts/find-errors parsed)
                 (opts/print-errors progname parsed)
                 (System/exit))
        (-> (process user/*input* options)
            (println)))))
(ns user (:require [comb])) (apply comb/-main *command-line-args*)