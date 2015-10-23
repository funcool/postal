(require '[cljs.build.api :as b])

(b/watch (b/inputs "test" "src")
  {:main 'postal.client-tests
   :target :nodejs
   :output-to "out/tests.js"
   :output-dir "out"
   :verbose true})
