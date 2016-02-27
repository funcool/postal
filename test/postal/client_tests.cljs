(ns postal.client-tests
  (:require [cljs.test :as t]
            [promesa.core :as p]
            [postal.client :as pc]
            [httpurr.errors :as e]
            [httpurr.client :as http]
            [httpurr.client.xhr :as xhr])
  (:import goog.testing.net.XhrIo))

;; (set! (.-XhrIo goog.net) (.-XhrIo goog.testing.net)) ;; mock XhrIo
(set! xhr/*xhr-impl* XhrIo)

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

(t/deftest send-query-that-fails-with-timeout-spec
  (t/async done
    (let [c (pc/client "http://localhost/api")
          r (pc/query c :users {:id 1})]
      (p/catch r (fn [err]
                   (t/is (= err e/timeout))
                   (done))))
    (let [xhr (raw-last-request)]
      (.simulateTimeout xhr))))

(t/deftest send-novelty-that-is-aborted-spec
  (t/async done
    (let [c (pc/client "http://localhost/api")
          r (pc/query c :users {:id 1})]
      (p/catch r (fn [err]
                   (t/is (= err e/abort))
                   (done)))
      (p/finally r done)
      (http/abort! r))))

(t/deftest send-novelty-and-recv-response-spec
  (t/async done
    (let [rframe (pc/encode {:type :response :data [1]})]
      (let [c (pc/client "http://localhost/api")
            r (pc/novelty c :users {:id 1})]
        (p/then r (fn [{:keys [type data]}]
                    (t/is (= type :response))
                    (t/is (= data [1]))
                    (done))))
      (let [xhr (raw-last-request)
            status 200
            body rframe
          headers #js {"Content-Type" "application/transit+json"}]
        (.simulateResponse xhr status body headers)))))

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
