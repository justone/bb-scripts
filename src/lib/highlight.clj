(ns lib.highlight
  (:require [clojure.string :as string]))

;; RGB color selection and DJB2 hashing is a direct port from the original
;; batchcolor. All credit for it's coolness goes to Steve Losh. Any bugs are mine.

(defn rgb-code
  "Generate 8-bit RGB code.

  From: https://en.wikipedia.org/wiki/ANSI_escape_code#8-bit"
  [r g b]
  (+ (* r 36)
     (* g 6)
     (* b 1)
     16))

(defn- make-colors
  [include-fn]
  (for [r (range 6)
        g (range 6)
        b (range 6)
        :when (include-fn (+ r g b))]
    (rgb-code r g b)))

;; Cut off the dark corner of the cube for dark terminals...
(def colors-for-dark (make-colors #(> % 3)))

;; ...and cut off the light corner for light terminals
(def colors-for-light (make-colors #(< % 11)))

(defn- djb2
  [string]
  (reduce
    (fn [h v]
      (mod (+ (* 33 h) v) (Math/pow 2 32)))
    5381
    (map int string)))

;; End ported coolness from batchcolor.

(defn fg
  "Return a string wrapped in the proper escape codes to set the foreground
  color in the passed string."
  [string color]
  (format "\033[38;5;%dm%s\033[0m" color string))

(defn bg
  "Return a string wrapped in the proper escape codes to set the background
  color in the passed string."
  [string color]
  (format "\033[48;5;%dm%s\033[0m" color string))

(defn- wrap
  [match opts]
  (let [{:keys [reverse? offset colors explicit]} opts
        ; _ (println match)
        string (cond-> match
                 (and (vector? match)
                      (some? (second match))) second
                 (and (vector? match)
                      (nil? (second match))) first)
        ; _ (println string)
        match (cond-> match
                 (vector? match) first)
        ; _ (println match)
        color (or (get explicit string)
                  (nth colors
                       (mod (cond-> string
                              reverse? reverse
                              :always djb2
                              offset (+ offset))
                            (count colors))))]
    ; (println match string)
    (string/replace match string (fg string color))))

(def default-opts
  {:colors colors-for-dark
   :explicit {}
   :offset 0
   :reverse? false})

(defn add
  "Highlight regex matches in line string by adding color. All instances of the
  same match are colored the same. The color is picked by hashing the match
  into an index into available colors, which makes the coloring stable across
  multiple runs.

  Options include:
  * :colors   - Which set of colors to use. Default is colors suitable for a
                dark background (colors-for-dark). For light backgrounds, use
                colors-for-light.
  * :explicit - Explicit colors for specific matched strings. Map of string to
                color code.
  * :offset   - Additional offset after calculating color code. Defaults to 0.
  * :reverse? - Should matches be reversed before selecting a color. Setting
                this to true can help differentiate matches that share a common
                prefix.
  "
  ([string regex]
   (add string regex nil))
  ([string regex opts]
   (let [final-opts (merge default-opts opts)]
     (string/replace string regex #(wrap % final-opts)))))
