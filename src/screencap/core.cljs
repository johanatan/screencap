(ns screencap.core
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [planck.core :refer [slurp file-seq]]
            [planck.shell :refer [sh] :refer-macros [with-sh-dir]]
            [cljs.core.async :refer [<! chan]]
            [goog.string.format]
            [clojure.string :refer [split join]]
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
  (println (format "Taking screenshot: %s" filename))
  (sh "screencapture" "-x" filename))

(defn check-res [res]
  (if (= 0 (res :exit)) (res :out)))

(defn user-active? []
  (and (= "false\n" (check-res
                     (sh "osascript"
                         "-e" "tell application \"System Events\""
                         "-e" "get running of screen saver preferences"
                         "-e" "end tell")))
       (if-let [res (check-res (sh "ioreg" "-n" "IODisplayWrangler"))]
         (= 4 ((js->clj (js/JSON.parse
                         (clojure.string/replace
                          (second (re-find #"IOPowerManagement.*({.*})" res)) "=" ":")))
               "DevicePowerState")))))

(defn current-application []
  ((sh "osascript"
       "-e" "tell application \"System Events\""
       "set appname to name of (first application process whose frontmost is true)"
       "-e" "end tell") :out))

(defn remove-duplicates [dir]
  (sh "fdupes" "-d" "-N" dir))

(defn get-files [dir extension]
  (filter #(.endsWith % extension) (map :path (file-seq dir))))

(defn convert-to-gif [append? files output-file]
  (let [options (if append? [] ["-set" "delay" "3" "-colorspace" "GRAY" "-colors"
                                "256" "-dispose" "1" "-loop" "0" "-scale" "50%"])
        res (apply sh (concat ["convert"] options files [output-file]))]
    (if (= 0 (res :exit)) (= 0 ((apply sh "rm" (remove #(= % output-file) files)) :exit)))))

(defn get-elapsed [block]
  (let [start (.getTime (js/Date.)) _ (block)]
    (max 0 (- (.getTime (js/Date.)) start))))

(defn run-loop [interval-millis callback]
  (go
    (loop [sleep 0]
      (<! (cljs.core.async/timeout sleep))
      (let [elapsed (get-elapsed (fn [] (callback (js/Date.))))]
        (recur (max 0 (- interval-millis elapsed)))))))

(defn encode-dir [dir date]
  (println (format "Encoding directory: %s at %s" dir date))
  (let [pngs (get-files dir ".png")
        gifs #(get-files dir ".gif")
        tmp-gif (format "%s/video_%02d.gif" dir (+ 1 (count (gifs))))
        final-gif (format "%s/video.gif" dir)]
    (if (convert-to-gif false pngs tmp-gif)
      (convert-to-gif true (gifs) final-gif))))

(defn -main []
  (run-loop
   (config :screenshot-interval-millis)
   (fn [date]
     (when (user-active?)
       (let [output-dir (ensure-output-dir-exists date)
             filename (format "%s/%02d_%02d_%02d" output-dir
                              (.getHours date) (.getMinutes date) (.getSeconds date))]
         (capture-screen (format "%s.png" filename))
         (remove-duplicates output-dir)))))
  (run-loop
   (config :encode-interval-millis)
   (fn [date]
     (let [pngs (get-files (config :output-dir) ".png")
           dirs (distinct (map (comp #(join "/" %) drop-last #(split % #"/")) pngs))]
       (doseq [dir dirs] (encode-dir dir date))))))
