(defproject clj-webview "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.8.0"]
  ;               [WebViewSample "0.0.1-SNAPSHOT"]
  ;               [com.teamdev.jxbrowser/jxbrowser-mac "6.0"]
                 ]
  ;:repositories [["xx" "http://maven.teamdev.com/repository/products"]]
  :java-source-paths ["java-src"]
  ;; :main clj-webview.browser
  :main clj-webview.core
  :aot [clj-webview.browser]
  :profiles {:uberjar {:aot :all}}
  :jvm-opts ["-Dfile.encoding=UTF8"]
  )
