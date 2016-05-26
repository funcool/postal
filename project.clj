(defproject funcool/postal "0.6.0"
  :description "postal client for clojurescript"
  :url "http://github.com/funcool/postal"
  :license {:name "Public Domain" :url "http://unlicense.org/"}
  :source-paths ["src"]
  :jar-exclusions [#"\.swp|\.swo|user.clj"]
  :plugins [[lein-ancient "0.6.10"]]
  :dependencies [[org.clojure/clojure "1.8.0" :scope "provided"]
                 [org.clojure/clojurescript "1.8.51" :scope "provided"]
                 [com.cognitect/transit-cljs "0.8.237"]
                 [funcool/httpurr "0.6.0"]
                 [funcool/beicon "1.3.0"]])
