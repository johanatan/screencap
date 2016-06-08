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

;; TODO : issue following command at end of each hour
;; (sh "convert" "-set" "delay" "3" "-colorspace" "GRAY" "-colors" "256" "-dispose 1" "-loop" "0" "-scale" "50%" "*.png" filename)

(defn user-active? []
  (let [res (sh "osascript"
                "-e" "tell application \"System Events\""
                "-e" "get running of screen saver preferences"
                "-e" "end tell")]
  (and (= 0 (res :exit)) (= "false\n" (res :out)))))

(defn remove-duplicates [dir]
  (sh "fdupes" "-d" "-N" dir))

(defn get-pngs [dir]
  (filter #(.endsWith % ".png") (map :path (file-seq dir))))

(defn get-date-sleep [start]
  (let [date (js/Date.)
        interval-millis (* 1000 (config :interval-seconds))]
    [date (if (= start 0) 0 (- interval-millis (max 0 (- (.getTime date) start))))]))

(defn -main []
  (go
    (loop [[date sleep] (get-date-sleep 0)]
      (<! (cljs.core.async/timeout sleep))
      (let [output-dir (ensure-output-dir-exists date)]
        (when (user-active?)
          (capture-screen (format "%s/%02d_%02d_%02d.png" output-dir
                                  (.getHours date) (.getMinutes date) (.getSeconds date))))
        (remove-duplicates output-dir)
        #_(let [pngs (get-pngs output-dir)])
        (recur (get-date-sleep (.getTime date)))))))