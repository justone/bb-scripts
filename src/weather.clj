(ns weather
  (:require
    [clojure.tools.cli :refer [parse-opts]]

    [lib.opts :as opts]
    [environ.core :refer [env]]
    [cheshire.core :as json]
    [babashka.curl :as curl]
    [user]
    )
  (:gen-class))

(def progname (str *ns*))

(def weatherbit-key (:weatherbit-key env))

(def cli-options
  [["-h" "--help"]])

(defn daily-forecast
  [selection]
  (-> (curl/get "http://api.weatherbit.io/v2.0/forecast/daily" {:query-params (assoc selection "key" weatherbit-key)})
      :body
      (json/parse-string true)
      :data))


#_(daily-forecast {"postal_code" "90232"})

(defn process
  [options]
  (prn options)

  )

(defn -main [& args]
  (let [parsed (parse-opts args cli-options)
        {:keys [options]} parsed]
    (or (some->> (opts/find-errors parsed)
                 (opts/print-errors progname parsed)
                 (System/exit))
        (process options))))
