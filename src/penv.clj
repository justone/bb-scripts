(ns penv
  (:require
    [clojure.java.shell :as sh]
    [clojure.string :as string]
    [clojure.tools.cli :refer [parse-opts]]

    [lib.opts :as opts]
    [user]
    )
  (:gen-class))

(def script-name "penv")

(def cli-options
  [["-h" "--help"]])

(defn parse-env
  [env]
  (let [[k v] (string/split env #"=")]
    {:key k
     :value v}))


#_(parse-env "FOO=bar")

(defn protect
  [env]
  (let [{:keys [key value]} env]
    (if (and (some? value) (re-matches #".*(TOKEN|KEY).*" key))
      (assoc env :value (str (subs value 0 4) "-xxxx"))
      env)))


#_(protect (parse-env "FOO=bar"))
#_(protect (parse-env "SLACK_TOKEN=bar"))

(defn env->str
  [env]
  (apply format "%s=%s" ((juxt :key :value) env)))


#_(env->str (protect (parse-env "SLACK_TOKEN=foobarbaz")))

(defn process
  [_options]
  (let [result (sh/sh "env")
        lines (->> (string/split (:out result) #"\n")
                   (map parse-env)
                   (map protect)
                   (map env->str))]
    (run! println lines)))

(defn -main [& args]
  (let [parsed (parse-opts args cli-options)
        {:keys [options]} parsed]
    (or (some->> (opts/find-errors parsed)
                 (opts/print-errors script-name parsed)
                 (System/exit))
        (process options))))
