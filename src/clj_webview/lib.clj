(ns clj-webview.lib
  (:require [clojure.string :as str]))

(import javafx.application.Application)
(import javafx.application.Platform)
(import javafx.scene.web.WebView)
(import netscape.javascript.JSObject)
(import javafx.beans.value.ChangeListener)
(import javafx.event.EventHandler)
(import javafx.scene.input.KeyEvent)
(import javafx.concurrent.Worker$State)
(import WebUIController)
(import MyEventDispatcher)

;; https://www.java-forums.org/javafx/93113-custom-javafx-webview-protocol-handler-print.html
;; ;over riding URL handlers
;; (import sun.net.www.protocol.http.Handler)
;; (import sun.net.www.protocol.http.HttpURLConnection)
(import sun.net.www.protocol.https.Handler)
;; (import sun.net.www.protocol.https.HttpsURLConnectionImpl)
(import java.net.URL)
(import java.net.URLConnection)
(import java.net.HttpURLConnection)
(import javax.net.ssl.HttpsURLConnection)
(import java.io.File)
(import java.net.URLStreamHandlerFactory)
(import java.net.URLStreamHandler)

(defmacro run-later [& forms]
  `(let [
         p# (promise)
         ]
     (Platform/runLater
      (fn []
        (deliver p# (try ~@forms (catch Throwable t# t#)))))
     p#))

(defn execute-script [w-engine s]
  (run-later
   (let [
         result (.executeScript w-engine s)
         ]
     (if (instance? JSObject result)
       (str result)
       result))))

(defn inject-firebug [w-engine]
  (execute-script w-engine (slurp "js-src/inject-firebug.js")))

(defn execute-script-async [w-engine s]
  (let [
        p (promise)
        *out* *out*
        ]
    (Platform/runLater
     (fn []
       (let [
             o (.executeScript w-engine "new Object()")
             ]
         (.setMember o "cb" (fn [s] (deliver p s)))
         (.setMember o "println" (fn [s] (println s)))
         (.eval o s))))
    @p))

(defn repl [webengine]
  (let [s (read-line)]
    (when (not= "" (.trim s))
      (println @(execute-script webengine s))
      (recur webengine))))

(defn bind [s obj webengine]
  (run-later
   (.setMember
    (.executeScript webengine "window")
    s obj)))

(defn clear-cookies [cookie-manager]
  (-> cookie-manager .getCookieStore .removeAll))

(defmacro compile-time-slurp [file]
  (slurp file))

(def js-disable-inputs (compile-time-slurp "js-src/disable-inputs.js"))

(defn async-load [url webengine]
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
                       (bind "println" f webengine)
                       (future
                         (Thread/sleep 1000)
                         (execute-script webengine js-disable-inputs)
                         (execute-script webengine "console.log = function(s) {println.invoke(s)};
                                                 console.error = function(s) {println.invoke(s)};
                                                 "))
                       (deliver p true))))
        ]
    (run-later
     (doto webengine
       (-> .getLoadWorker .stateProperty (.addListener listener))
       (.load url)))
    @p))

(defn back [webengine]
  (execute-script webengine "window.history.back()"))

(declare keys-g-map)
(declare keys-default)
(declare bind-keys)

;; Atomic (thread safe), pretty neat.
(def key-map-current (atom :default))
(defn key-map-set [which] (swap! key-map-current (fn [_] which)))
(defn key-map-get [] @key-map-current)
;; TODO: Add numeric prefixes for repeatables

(defn keys-g-map [key]
  (key-map-set :default)
  (case key
    "g" "window.scrollTo(0, 0)"
    false))

(defn keys-def-map [key]
  (case key
    "g" (key-map-set :g)
    "G" "window.scrollTo(0, window.scrollY + 5000)"
    "k" "window.scrollTo(window.scrollX, window.scrollY - 50)"
    "j" "window.scrollTo(window.scrollX, window.scrollY + 50)"
    "c" "document.body.innerHTML=''"
    "r" "window.location.reload()"
    false))

(defn key-map-dispatcher []
  (case (key-map-get)
    :default keys-def-map
    :g keys-g-map
    keys-def-map))

(defn key-map-op [key]
  (let [fn-map (key-map-dispatcher)]
    (fn-map key)))

(defn key-map-handler [key webview webengine]
  (let [op (key-map-op key )]
    (println (format "KM OP: %s" op))
    (when (= java.lang.String (type op))
      (execute-script webengine op))))

;; https://docs.oracle.com/javafx/2/events/filters.htm
(defn bind-keys [wv webengine]
  (doto wv
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
          (execute-script webengine js-disable-inputs)
          (key-map-handler (-> event .getText .toString) wv webengine)
          ))))))

(defn url-ignore-regexes-from-file [file]
  (map re-pattern (str/split (slurp file) #"\n")))

(defn url-ignore-regexes []
  (url-ignore-regexes-from-file "conf/url-ignore-regexes.txt"))

(defn matching-regexes [url regexes]
  (filter #(re-matches % url) regexes))

(defn url-ignorable? [url]
  (let [ignorables (matching-regexes url (url-ignore-regexes))]
    (if (> (count ignorables) 0)
      (do
        (println (format "Ignoring URL: %s, hit %d matchers." url (count ignorables)))
        true)
      false)))

(defn url-or-no [url proto]
  (let [url (.toString url)]
    (URL.
     (if (url-ignorable? url)
       (format "%s://0.0.0.0:65535" proto)
       url))))

;; Hmm, we could hide things we do not want to see.
(defn my-connection-handler [protocol]
  (case protocol
    "http" (proxy [sun.net.www.protocol.http.Handler] []
             (openConnection [& [url proxy :as args]]
               (println url)
               (proxy-super openConnection (url-or-no url protocol) proxy)))
    "https" (proxy [sun.net.www.protocol.https.Handler] []
              (openConnection [& [url proxy :as args]]
                (println url)
                (proxy-super openConnection (url-or-no url protocol) proxy)))
    nil
    ))
