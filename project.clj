(defproject funcool/postal "0.2.0-SNAPSHOT"
  :description "A parser, renderer and validation layer for POSTAL protocol for Clojure and ClojureScript."
  :url "http://github.com/funcool/postal"
  :license {:name "BSD (2-Clause)"
            :url "http://opensource.org/licenses/BSD-2-Clause"}
  :source-paths ["src"]
  :jar-exclusions [#"\.swp|\.swo|user.clj"]
  :javac-options ["-target" "1.8" "-source" "1.8" "-Xlint:-options"]
  :dependencies [[org.clojure/clojure "1.7.0" :scope "provided"]
                 [org.clojure/clojurescript "1.7.107" :scope "provided"]
                 [org.clojure/tools.reader "0.10.0-alpha3"]
                 [funcool/cuerdas "0.6.0"]])
