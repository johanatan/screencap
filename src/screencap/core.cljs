(ns screencap.core
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [planck.core :refer [slurp file-seq]]
            [planck.shell :refer [sh] :refer-macros [with-sh-dir]]
            [cljs.core.async :refer [<! chan]]
            [goog.string.format]
            [cljs.reader]
            [cljs.js]))

(def config (cljs.reader/read-string (slurp "./config.edn")))

(defn format
  "Formats a string using goog.string.format."
  [fmt & args]
  (apply goog.string/format fmt args))

(defn get-date [date]
  [(.getFullYear date) (.getMonth date) (.getDate date) (.getHours date)])

(defn ensure-output-dir-exists [date]
  (let [dir (apply format "%s/%s/%s/%s/%s" (concat [(config :output-dir)] (get-date date)))]
    (sh "mkdir" "-p" dir)
    dir))

(defn capture-screen [filename]
  (sh "screencapture" "-x" filename))

(defn user-active? []
  (let [res (sh "osascript"
                "-e" "tell application \"System Events\""
                "-e" "get running of screen saver preferences"
                "-e" "end tell")]
  (and (= 0 (res :exit)) (= "false\n" (res :out)))))

(defn remove-duplicates [dir]
  (sh "fdupes" "-d" "-N" dir))

(defn get-files [dir extension]
  (filter #(.endsWith % extension) (map :path (file-seq dir))))

(defn get-date-sleep [start]
  (let [date (js/Date.)
        interval-millis (* 1000 (config :interval-seconds))]
    [date (if (= start 0) 0 (- interval-millis (max 0 (- (.getTime date) start))))]))

(defn convert-to-gif [append? files output-file]
  (let [options (if append? [] ["-set" "delay" "3" "-colorspace" "GRAY" "-colors"
                                "256" "-dispose" "1" "-loop" "0" "-scale" "50%"])
        res (apply sh (concat ["convert"] options files [output-file]))]
    (if (= 0 (res :exit)) (= 0 ((apply sh "rm" (remove #(= % output-file) files)) :exit)))))

(defn -main []
  (go
    (loop [[date sleep] (get-date-sleep 0)]
      (<! (cljs.core.async/timeout sleep))
      (let [output-dir (ensure-output-dir-exists date)]
        (when (user-active?)
          (capture-screen (format "%s/%02d_%02d_%02d.png" output-dir
                                  (.getHours date) (.getMinutes date) (.getSeconds date))))
        (remove-duplicates output-dir)
        (let [pngs (get-files output-dir ".png")
              gifs #(get-files output-dir ".gif")
              tmp-gif (format "%s/video_%02d.gif" output-dir (+ 1 (count (gifs))))
              final-gif (format "%s/video.gif" output-dir)]
          (if (convert-to-gif false pngs tmp-gif)
            (convert-to-gif true (gifs) final-gif)))
        (recur (get-date-sleep (.getTime date)))))))
