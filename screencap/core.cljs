(ns screencap.core
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require
   [lumo.io :as io :refer [slurp]]
   [lumo.util :as util :refer [file-seq]]
   [cljs.core.async :refer [<!]]
   [goog.string]
   [clojure.string :as string :refer [ends-with? join split]]
   [child_process :as child-process]
   [cljs.reader]
   [cljs.js]))

(def config (cljs.reader/read-string (slurp "./config.edn")))

(defn sh [cmd & args]
  (let [command (str cmd " " (join " " args))]
    (try
      (let [result (.toString (.execSync child-process command {:encoding "utf8" :stdio "pipe"}))]
        {:exit 0
         :out result
         :err ""})
      (catch js/Error e
       {:exit (.-status e)
          :out ""
          :err (.-message e)}))))

(defn sq [v]
  (str "'" v "'"))

(defn format
  "Formats a string using goog.string.format."
  [fmt & args]
  (apply goog.string/format fmt args))

(defn get-date [date]
  [(.getFullYear date) (+ 1 (.getMonth date)) (.getDate date) (.getHours date)])

(defn ensure-output-dir-exists [date]
  (let [dir (apply format "%s/%s/%s/%s/%s" (concat [(config :output-dir)] (get-date date)))]
    (sh "mkdir" "-p" dir)
    dir))

(defn capture-screen [filename]
  (println (format "Taking screenshot: %s" filename))
  (sh "screencapture" "-x" filename))

(defn check-res [res]
  (when (= 0 (res :exit)) (res :out)))

(defn display-active? []
  (= "on\n" (check-res (sh "pmset -g log | grep -E 'Display is turned (on|off)' | tail -n 1 | awk '{print $NF}'"))))

(defn screen-saver-active? []
  (= "true\n" (check-res
                (sh "osascript"
                    "-e" (sq "tell application \"System Events\"")
                    "-e" (sq "set isRunning to (count of (every process whose name is \"ScreenSaverEngine\")) > 0")
                    "-e" (sq "end tell")
                    "-e" (sq "return isRunning")))))

(defn user-active? []
  (and display-active? (not (screen-saver-active?))))

(defn current-application []
  ((sh "osascript"
       "-e" (sq "tell application \"System Events\"")
       "-e" (sq "set appname to name of (first application process whose frontmost is true)")
       "-e" (sq "end tell")) :out))

(defn remove-duplicates [dir]
  (sh "fdupes" "-d" "-N" dir))

(defn get-files [dir extension]
  (filter #(ends-with? %1 extension) (file-seq dir)))

(defn convert-to-gif [append? files output-file]
  (let [options (if append? [] ["-set" "delay" "3" "-colorspace" "GRAY" "-colors"
                                "256" "-dispose" "1" "-loop" "0" "-scale" "50%"])
        res (apply sh (concat ["convert"] options files [output-file]))]
    (when (= 0 (res :exit)) (= 0 ((apply sh "rm" (remove #(= %1 output-file) files)) :exit)))))

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
    (when (convert-to-gif false pngs tmp-gif)
      (convert-to-gif true (gifs) final-gif))))

(defn encode-once [date]
  (let [pngs (get-files (config :output-dir) ".png")
        dirs (distinct (map (comp #(join "/" %) drop-last #(split % #"/")) pngs))]
    (doseq [dir dirs]
      (encode-dir dir date)
      (remove-duplicates dir))))

(defn -main [cmd & _]
  (case cmd
    "screenshot"
    (run-loop
     (config :screenshot-interval-millis)
     (fn [date]
       (when (user-active?)
         (let [output-dir (ensure-output-dir-exists date)
               filename (format "%s/%02d_%02d_%02d" output-dir
                                (.getHours date) (.getMinutes date) (.getSeconds date))]
           (capture-screen (format "%s.png" filename))
           (remove-duplicates output-dir)))))
    "encode-once"
    (encode-once (js/Date.))
    "encode"
    (run-loop
     (config :encode-interval-millis)
     encode-once)))
