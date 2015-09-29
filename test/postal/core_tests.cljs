(ns postal.core-tests
  (:require [cljs.test :as t]
            [cats.core :as m]
            [promesa.core :as p]
            [beicon.core :as s]
            [postal.core :as pc]
            [postal.socket :as ps]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Tests
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn to-promise
  [ob]
  {:pre [(s/observable? ob)]}
  (let [s (s/take 1 ob)]
    (p/promise (fn [resolve reject]
                 (s/subscribe s #(resolve %) #(reject %) (constantly nil))))))

(defn send-handshake
  ([sock]
   (send-handshake sock :hello))
  ([sock type]
   (let [busin (:busin sock)
         busout (:busout sock)
         result-frame (case type
                        :error {:cmd :error :data {:message "Unexpected"}}
                        :hello {:cmd :hello})]
     (m/mlet [hello-frame (to-promise busout)
              :let [hello-frame (pc/frame-decode hello-frame)]]

       (assert (= (:cmd hello-frame) :hello))
       (s/push! busin {:type :socket/message
                       :payload(pc/frame-encode result-frame)})))))

(defn make-client
  []
  (let [busin (s/bus)
        busout (s/bus)
        sock (ps/fake-websocket busin busout)
        client (pc/client sock)]
    [busin busout sock client]))

(t/deftest error-on-handshake-test
  (t/async done
    (let [[busin busout sock client] (make-client)]
      (m/mlet [_ (send-handshake sock :error)]
        (t/is (pc/closed? client))
        (done))
      (s/push! busin {:type :socket/open}))))

(t/deftest query-frame-success-test
  (t/async done
    (let [[busin busout sock client] (make-client)]
      (m/mlet [frame (send-handshake sock)
               result (pc/query client "/foobar")]
        (t/is (= (:data result) "foobar"))
        (done))

      (s/push! busin {:type :socket/open})
      (m/mlet [frame (to-promise busout)
               :let [frame (pc/frame-decode frame)]]
        (s/push! busin {:type :socket/message
                        :payload (pc/frame-encode
                                  {:cmd :response
                                   :id (:id frame)
                                   :data "foobar"})}))
      )))

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