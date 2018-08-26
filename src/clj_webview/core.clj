(ns clj-webview.core
  (:require [clj-webview.lib :as l]
            [clj-webview.boot :as b])
  (:gen-class))

(defn -main []
  (println "Starting version 0.0.0")
  (l/async-load "http://ahungry.com" (b/boot)))
