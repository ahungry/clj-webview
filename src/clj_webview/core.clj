(ns clj-webview.core
  (:require [clj-webview.boot :as b]))

(defn -main []
  (b/async-load "http://ahungry.com"))
