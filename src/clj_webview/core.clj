(ns clj-webview.core)

(import javafx.application.Application)
(import javafx.application.Platform)
(import javafx.scene.web.WebView)
                                        ;(import hk.molloy.MyApplication)
(import netscape.javascript.JSObject)
(import javafx.beans.value.ChangeListener)
(import javafx.event.EventHandler)
(import javafx.scene.input.KeyEvent)
(import javafx.concurrent.Worker$State)
(import WebUIController)
(import MyEventDispatcher)

;;launch calls the fxml which in turn loads WebUIController
(defonce launch (future (Application/launch hk.molloy.Browser (make-array String 0))))

(def url "http://ahungry.com")

(defmacro run-later [& forms]
  `(let [
         p# (promise)
         ]
     (Platform/runLater
      (fn []
        (deliver p# (try ~@forms (catch Throwable t# t#)))))
     p#))

(defonce webengine (do
                     (Thread/sleep 1000)
                     WebUIController/engine
                     #_@(run-later (.getEngine (WebView.)))))

;; https://stackoverflow.com/questions/22778241/javafx-webview-scroll-to-desired-position
(def webview WebUIController/view)

(defn execute-script [s]
  (run-later
   (let [
         result (.executeScript webengine s)
         ]
     (if (instance? JSObject result)
       (str result)
       result))))

(defn inject-firebug []
  (execute-script (slurp "js-src/inject-firebug.js")))

(defn execute-script-async [s]
  (let [
        p (promise)
        *out* *out*
        ]
    (Platform/runLater
     (fn []
       (let [
             o (.executeScript webengine "new Object()")
             ]
         (.setMember o "cb" (fn [s] (deliver p s)))
         (.setMember o "println" (fn [s] (println s)))
         (.eval o s))))
    @p))

(defn repl []
  (let [s (read-line)]
    (when (not= "" (.trim s))
      (println @(execute-script s))
      (recur))))

(defn bind [s obj]
  (run-later
   (.setMember
    (.executeScript webengine "window")
    s obj)))

(defonce cookie-manager
  (doto (java.net.CookieManager.)
    java.net.CookieHandler/setDefault))

(defn clear-cookies []
  (-> cookie-manager .getCookieStore .removeAll))

(defn async-load [url]
  (let [
        p (promise)
        f (fn [s]
            (binding [*out* *out*] (println s)))
        listener (reify ChangeListener
                   (changed [this observable old-value new-value]
                     (when (= new-value Worker$State/SUCCEEDED)
                                        ;first remove this listener
                       (.removeListener observable this)
                                        ;and then redefine log and error (fresh page)
                       (bind "println" f)
                       (future
                         (Thread/sleep 1000)
                         (execute-script (slurp "js-src/disable-inputs.js"))
                         (execute-script "console.log = function(s) {println.invoke(s)};
                                                 console.error = function(s) {println.invoke(s)};
                                                 "))
                       (deliver p true))))
        ]

    ;; (run-later
    ;;  (doto webengine
    ;;    (-> .getLoadWorker .stateProperty (.addListener key-listener))))
    (run-later
     (doto webengine
       ;; (-> .getLoadWorker .stateProperty (.addListener key-listener))
       (-> .getLoadWorker .stateProperty (.addListener listener))
       (.load url)))

    @p
    ))

(defn back []
  (execute-script "window.history.back()"))

(def js-disable-inputs (slurp "js-src/disable-inputs.js"))

;; https://docs.oracle.com/javafx/2/events/filters.htm
(doto webview
  (->
   (.addEventFilter
    (. KeyEvent KEY_PRESSED)
    (reify EventHandler ;; EventHandler
      (handle [this event]
        ;; (println "Clojure keypress detected\n")
        ;; (println (-> event .getCode .toString))
        (println (-> event .getText .toString))
        (.consume event)
        ;; disable webview here, until some delay was met
        ;; https://stackoverflow.com/questions/27038443/javafx-disable-highlight-and-copy-mode-in-webengine
        ;; https://docs.oracle.com/javase/8/javafx/api/javafx/scene/web/WebView.html
        (execute-script js-disable-inputs)
        (case (-> event .getText .toString)
          "k" (execute-script "window.scrollTo(window.scrollX, window.scrollY - 50)")
          "j" (execute-script "window.scrollTo(window.scrollX, window.scrollY + 50)")
          "c" (execute-script "document.body.innerHTML=''")
          "r" (execute-script "window.location.reload()")
          false)
        )))))

(defn -main []
  (println "Begin.")
  (async-load "http://ahungry.com"))

;; (-main)


;; https://www.java-forums.org/javafx/93113-custom-javafx-webview-protocol-handler-print.html
                                        ;over riding URL handlers
(import sun.net.www.protocol.http.Handler)
(import sun.net.www.protocol.http.HttpURLConnection)
(import java.net.URL)
(import java.net.URLConnection)
;; (import java.net.HttpURLConnection)
(import java.io.File)
(import java.net.URLStreamHandlerFactory)
(import java.net.URLStreamHandler)

(defn my-connection-handler [protocol]
  (proxy [Handler] []
    (openConnection [& [url proxy :as args]]
      (println args)
      ;; #_(HttpURLConnection. url proxy)
      ;; Not a bad way to just never resolve URLs we want to skip (advertising ones)
      (if (re-matches #".*.css" (.toString url)) (HttpURLConnection. (URL. "http://0.0.0.0") proxy)
          (HttpURLConnection. url proxy))
      )))

(defonce stream-handler-factory
  (URL/setURLStreamHandlerFactory
   (reify URLStreamHandlerFactory
     ;; (createURLStreamHandler [this protocol] (#'my-connection-handler protocol))
     (createURLStreamHandler [this protocol] (#'my-connection-handler protocol))
     )))
