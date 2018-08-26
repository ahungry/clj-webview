(ns clj-webview.core
  (:require [clj-webview.lib :as l]
            [clj-webview.boot :as b]))

(defn -main []
  (l/async-load "http://ahungry.com" b/webengine))

;; (defn -main [] "Ok")
