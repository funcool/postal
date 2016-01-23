(defproject funcool/postal "0.4.0"
  :description "postal client for clojurescript"
  :url "http://github.com/funcool/postal"
  :license {:name "Public Domain"
            :url "http://unlicense.org/"}
  :source-paths ["src"]
  :jar-exclusions [#"\.swp|\.swo|user.clj"]
  :javac-options ["-target" "1.8" "-source" "1.8" "-Xlint:-options"]
  :plugins [[lein-ancient "0.6.7"]]
  :dependencies [[org.clojure/clojure "1.8.0" :scope "provided"]
                 [org.clojure/clojurescript "1.7.228" :scope "provided"]
                 [com.cognitect/transit-cljs "0.8.237"]
                 [funcool/cats "1.2.1"]
                 [funcool/httpurr "0.3.0"]
                 [funcool/beicon "0.6.0"]
                 [funcool/promesa "0.7.0"]])
