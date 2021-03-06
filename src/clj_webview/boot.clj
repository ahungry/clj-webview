(ns clj-webview.boot
  (:use clj-webview.lib)
  (:require [clj-webview.lib]))

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

;;launch calls the fxml which in turn loads WebUIController
(defn boot []
  (defonce launch (future (Application/launch com.ahungry.Browser (make-array String 0))))

  (defonce webengine (do
                       (Thread/sleep 1000)
                       WebUIController/engine
                       #_@(run-later (.getEngine (WebView.)))))

  ;; https://stackoverflow.com/questions/22778241/javafx-webview-scroll-to-desired-position
  (def webview WebUIController/view)

  (defonce cookie-manager
    (doto (java.net.CookieManager.)
      java.net.CookieHandler/setDefault))

  (bind-keys webview webengine)

  (defonce stream-handler-factory
    (URL/setURLStreamHandlerFactory
     (reify URLStreamHandlerFactory
       (createURLStreamHandler [this protocol] (#'my-connection-handler protocol))
       )))
  webengine)
