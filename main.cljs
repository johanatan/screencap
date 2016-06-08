#!/usr/bin/env planck

(ns screencap
  (:require [planck.core :refer [slurp]]
            [planck.shell :refer [sh] :refer-macros [with-sh-dir]]
            [goog.string.format]
            [cljs.js]))

(def st (cljs.js/empty-state planck.core/init-empty-state))
(defn- eval [str] ((cljs.js/eval-str st str nil {:eval cljs.js/js-eval :context :expr} identity) :value))

(def config (eval (slurp "./config.edn")))

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
  (sh "screencapture" "-x" "-T" (str (config :interval-seconds)) filename))

;; TODO : issue following command at end of each hour
;; (sh "convert" "-set" "delay" "3" "-colorspace" "GRAY" "-colors" "256" "-dispose 1" "-loop" "0" "-scale" "50%" "*.png" filename)

(defn user-active? []
  (let [res (sh "osascript"
                "-e" "tell application \"System Events\""
                "-e" "get running of screen saver preferences"
                "-e" "end tell")]
  (and (= 0 (res :exit)) (= "false\n" (res :out)))))

(loop [date (js/Date.)]
  (let [output-dir (ensure-output-dir-exists date)]
    (when (user-active?)
      (capture-screen (format "%s/%02d_%02d_%02d" output-dir (.getHours date) (.getMinutes date) (.getSeconds date))))
    (recur (js/Date.))))

