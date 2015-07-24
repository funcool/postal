(require '[cljs.build.api :as b]
         '[cljs.closure]
         '[clojure.java.io :as io])

(println "Building ...")

(let [start (System/nanoTime)]
  (b/build
   (b/inputs "test" "src")
   {:main 'postal.parser-tests
    :output-to "tests.js"
    :output-dir "out"
    :target :nodejs
    :verbose true})
  (println "... done. Elapsed" (/ (- (System/nanoTime) start) 1e9) "seconds"))
