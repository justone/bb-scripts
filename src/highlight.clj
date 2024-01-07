(ns highlight
  (:require [clojure.string :as string]
            [clojure.tools.cli :refer [parse-opts]]
            [scribe.highlight :as highlight]
            [scribe.opts :as opts]
            [scribe.string]))

(def cli-options
  [["-h" "--help" "Show help."]
   [nil "--dark" "Use dark mode. (default)"
    :default-desc ""
    :default true]
   [nil "--light" "Use light mode."]
   ["-o" "--offset OFFSET" "Specify the color offset. (default: 0)"
    :default 0
    :default-desc ""
    :parse-fn parse-long]
   ["-r" "--randomize-offset" "Randomize the color offset."]
   ["-R" "--reverse" "Reverse matched strings before assigning a color."
    :default-desc ""
    :default false]
   ["-e" "--explicit R,G,B:STRING" "Colorize a match explicitly."
    :default []
    :default-desc ""
    :update-fn conj
    :multi true]
   [nil "--print-colors" "Print color reference."]])

(defn parse-explicit
  [opt]
  (let [[_ r g b m1] (re-matches #"([0-5]),([0-5]),([0-5]):(.*)" opt)
        [_ rgb m2] (re-matches #"(\d{1,3}):(.*)" opt)]
    (cond
      rgb         {:match m2 :rgb-code (parse-long rgb)}
      (and r g b) {:match m1 :rgb-code (highlight/rgb-code (parse-long r)
                                                          (parse-long g)
                                                          (parse-long b))})))

(def usage
  "Colorize matches in streaming text.

  highlight takes a regular expression and highlights any matches in piped
  input. Each match string gets a unique color, making it useful for quickly
  categorizing information.

  For example, to compare sha256 sums and identify identical files:

    sha256sum * | highlight '[0-9a-ef]{40,}'

  Or, to track IP addresses in a request log:

    tail -F /var/log/apache/access_log | highlight '\\d+\\.\\d+\\.\\d+\\.\\d+'

  highlight picks colors by hashing matches and using that to index into a set
  of colors. Two different sets of colors are supported, one for light
  backgrounds and one for dark backgrounds. By default, colors that will look
  good on dark backgrounds are used. This indexing will be consistent between
  runs and can be altered via the --offset and --randomize-offset flags.

  Due to highlight's hashing, it will pick adjacent colors for strings that have
  identical prefixes and only vary by a few characters. Since this can make
  differentiating colors difficult, use the --reverse flag to reverse matches
  before hashing. This will result in highlight selecting colors further apart.

  A match's color can be explicitly specified with the --explicit flag. The
  color can be specified by comma delimited triplet from 0-5 (e.g. 5,0,0) or
  the RGB code directly (16-255).

    tail -F foo.log | highlight 'WARN|INFO|ERROR' -e 160:ERROR -e 220:WARN -e 99:INFO

  Use the --print-colors option to print a color table with numbers to use.

  highlight is *heavily* inspired by batchcolor, as detailed in Steve Losh's
  blog post: https://stevelosh.com/blog/2021/03/small-common-lisp-cli-programs/")

(defn find-errors
  [parsed]
  (or (opts/find-errors parsed usage)
      (let [{:keys [arguments options]} parsed
            {:keys [explicit]} options
            regex (first arguments)
            failed-explicits (remove parse-explicit explicit)]
        (cond
          (nil? regex)
          {:message "Pass regex to match."
           :exit 1}

          (seq failed-explicits)
          {:message (str "Bad explicit arguments: " (string/join ", " failed-explicits))
           :exit 1}))))

(defn parsed->color-opts
  [{:keys [options arguments]}]
  (let [regex (some-> arguments first re-pattern)
        {:keys [dark light offset randomize-offset reverse explicit]} options]
    [regex
     {:reverse? reverse
      :explicit (->> (map parse-explicit explicit)
                     (reduce
                       (fn [r {:keys [match rgb-code]}]
                         (assoc r match rgb-code))
                       {}))
      :offset (cond
                randomize-offset (rand-int 256)
                :else offset)
      :colors (cond
                light highlight/colors-for-light
                dark highlight/colors-for-dark)}]))

(defn format-color
  [bg-color fb-color string]
  (-> string
      (highlight/fg fb-color)
      (highlight/bg bg-color)))

(defn print-colors
  []
  (println "Base colors:")
  (doseq [base (range 16)
          :let [fb-color (if (< 7 base) 0 15)]]
    (print (format-color base fb-color (format " %2s" base))))
  (newline)
  (newline)
  (println "216 colors:")
  (doseq [r (range 6)]
    (doseq [g (range 6)]
      (doseq [b (range 6)
              :let [color (highlight/rgb-code r g b)
                    fb-color (if (< 2 g) 0 15)]]
        (print (format-color color fb-color (format " %3s" color)))
        (when (and (= g 5) (= b 5))
          (newline)))))
  (newline)
  (println "Grayscale:")
  (doseq [gray (range 232 256)
          :let [fb-color (if (< 243 gray) 0 15)]]
    (print (format-color gray fb-color (format " %3d" gray))))
  (newline))

(defn -main [& args]
  (let [parsed (parse-opts args cli-options)
        [regex opts] (parsed->color-opts parsed)]
    (cond
      (-> parsed :options :print-colors)
      (print-colors)

      :else
      (do
        (some-> (find-errors parsed)
                (opts/format-help parsed)
                (opts/print-and-exit))
        (loop []
          (when-let [line (read-line)]
            (println (highlight/add line regex opts))
            (recur)))))))
