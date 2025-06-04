(ns cap.xdg)

(defn dirs
  [env]
  (let [home (get env "HOME")
        data-home (get env "XDG_DATA_HOME")
        config-home (get env "XDG_CONFIG_HOME")
        cache-home (get env "XDG_CACHE_HOME")
        state-home (get env "XDG_STATE_HOME")]
    #:xdg{:data-home (or data-home (format "%s%s" home "/.local/share"))
          :config-home (or config-home (format "%s%s" home "/.config"))
          :cache-home (or cache-home (format "%s%s" home "/.cache"))
          :state-home (or state-home (format "%s%s" home "/.local/state"))}))


(comment
  (into {} (get (System/getenv) "HOME"))
  (dirs (System/getenv))
  (dirs {"HOME" "/somewhere/else"
         "XDG_DATA_HOME" "/foo/data"})
  )
