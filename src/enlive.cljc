(ns enlive
  (:require
    #?(:bb nil
       :clj [bootleg.utils :as utils])
    #?(:bb [babashka.pods :as pods]
       :clj [bootleg.enlive :as enlive])))

#?(:bb
   (do
     (pods/load-pod "bootleg")
     (require '[pod.retrogradeorbit.bootleg.enlive :as enlive])))

(defn -main [& _args]
  (println (enlive/at "<p>{{here comes foo}}</p>"
                      [:p] (enlive/content "foo"))))
