(ns postal.client-tests
  (:require [cljs.test :as t]
            [cats.core :as m]
            [promesa.core :as p]
            [postal.client :as pc])
  (:import [goog.testing.net XhrIo]
           [goog.net]))

(set! (.-XhrIo goog.net) (.-XhrIo goog.testing.net)) ;; mock XhrIo

(defn raw-last-request
  []
  (aget (.getSendInstances XhrIo) 0))

(defn last-request
  []
  (let [r (raw-last-request)]
    {:method  (.getLastMethod r)
     :uri     (.toString (.getLastUri r))
     :headers (js->clj (.getLastRequestHeaders r))
     :body    (.getLastContent r)}))

(defn cleanup
  []
  (.cleanup XhrIo))

(t/use-fixtures :each
  {:after #(cleanup)})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Tests
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(t/deftest send-query-spec
  (let [c (pc/client "http://localhost/api")
        r (pc/query c :users {:id 1})]
    (let [req (last-request)]
      (t/is (= (:method req) "PUT"))
      (t/is (= (:uri req) (:url c)))
      (t/is (not (empty? (:headers req)))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Initial Setup & Entry Point
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(enable-console-print!)

(defmethod t/report [:cljs.test/default :end-run-tests]
  [m]
  (if (t/successful? m)
    (set! (.-exitCode js/process) 0)
    (set! (.-exitCode js/process) 1)))

(set! *main-cli-fn* #(t/run-tests))
