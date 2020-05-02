#!/usr/bin/env bash
bb -cp $(clojure -A:test -Spath) \
   -e "(require '[clojure.test :as t]
                '[empath-test])
       (let [{:keys [:fail :error]} (t/run-tests 'empath-test)]
         (System/exit (+ fail error)))"
