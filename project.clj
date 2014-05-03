(defproject clojure-blog "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [compojure "1.1.6"]
                 [enlive "1.1.5"]
                 [ring "1.2.2"]
                 [com.taoensso/carmine "2.6.0"]
                 [clj-time "0.7.0"]
                 [clojurewerkz/scrypt "1.1.0"]]
  :plugins [[lein-ring "0.8.10"]]
  :ring {:handler clojure-blog.handler/app}
  :profiles
  {:dev {:dependencies [[javax.servlet/servlet-api "2.5"]
                        [ring-mock "0.1.5"]]}})
