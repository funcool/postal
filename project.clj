(defproject funcool/postal "0.2.0-SNAPSHOT"
  :description "A parser, renderer and validation layer for POSTAL protocol for Clojure and ClojureScript."
  :url "http://github.com/funcool/postal"
  :license {:name "Public Domain"
            :url "http://unlicense.org/"}
  :source-paths ["src"]
  :jar-exclusions [#"\.swp|\.swo|user.clj"]
  :javac-options ["-target" "1.8" "-source" "1.8" "-Xlint:-options"]
  :plugins [[lein-ancient "0.6.7"]]
  :dependencies [[org.clojure/clojure "1.7.0" :scope "provided"]
                 [org.clojure/clojurescript "1.7.145" :scope "provided"]
                 [funcool/httpurr "0.1.1"]
                 [com.cognitect/transit-cljs "0.8.225"]
                 [funcool/cats "1.0.0"]
                 [funcool/promesa "0.5.1"]])
