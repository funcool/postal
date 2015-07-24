(ns postal.parser-tests
  (:require #?(:clj[clojure.test :as t]
               :cljs [cljs.test :as t])
            [postal.parser :as p]))

#?(:cljs (enable-console-print!))

(def message1
  (str "COMMAND\n"
       "header1:value1\n"
       "header2:value2\n"
       "\n"
       "body value"))

(def message2
  (str "COMMAND\n"
       "header1:value1\n"
       "header2:value2\n"
       "\n"))

(def message3
  (str "COMMAND\n"
       "header1:value1\n"
       "header2:value2\n"))

(t/deftest read-complete-frame
  (let [frame (p/parse message1)]
    (t/is (= (:command frame) "COMMAND"))
    (t/is (= (:headers frame) {:header1 "value1" :header2 "value2"}))
    (t/is (= (:body frame) "body value"))))

(t/deftest read-frame-without-body
  (let [frame (p/parse message2)]
    (t/is (= (:command frame) "COMMAND"))
    (t/is (= (:headers frame) {:header1 "value1" :header2 "value2"}))
    (t/is (= (:body frame) ""))))

(t/deftest read-incomplete-frame
  (t/is (thrown? #?(:clj java.lang.RuntimeException
                    :cljs js/Error)
                 (p/parse message3))))

#?(:cljs
   (defmethod t/report [:cljs.test/default :end-run-tests]
     [m]
     (if (t/successful? m)
       (set! (.-exitCode js/process) 0)
       (set! (.-exitCode js/process) 1))))

#?(:cljs
   (set! *main-cli-fn* #(t/run-tests)))
