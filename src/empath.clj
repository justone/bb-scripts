(ns empath
  (:require
    [clojure.java.io :as io]
    [clojure.string :as string]

    [doric.core :as doric]
    ))

(defn analyze
  [path]
  (for [part (string/split path #":")]
    (let [f (io/file part)]
      {:element part
       :exists (.exists f)
       :dir (.isDirectory f)
       :file (.isFile f)
       :can-write (.canWrite f)})))

(defn -main [& args]
  (->> (or (first args) (System/getenv "PATH"))
       (analyze)
       (doric/table [:element :exists :dir :file :can-write])
       println))
