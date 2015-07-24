(require '[cljs.build.api :as b])

(b/watch (b/inputs "test" "src")
  {:main 'postal.parser-tests
   :target :nodejs
   :output-to "tests.js"
   :output-dir "out"
   :verbose true})
