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

;; (t/deftest error-on-handshake-test
;;   (t/async done
;;     (let [[busin busout sock client] (make-client)]
;;       (m/mlet [_ (send-handshake sock :error)]
;;         (t/is (pc/closed? client))
;;         (done))
;;       (s/push! busin {:type :socket/open}))))

;; (t/deftest query-frame-success-test
;;   (t/async done
;;     (let [[busin busout sock client] (make-client)]
;;       (m/mlet [frame (send-handshake sock)
;;                result (pc/query client "/foobar")]
;;         (t/is (= (:data result) "foobar"))
;;         (done))

;;       (s/push! busin {:type :socket/open})
;;       (m/mlet [frame (to-promise busout)
;;                :let [frame (pc/frame-decode frame)]]
;;         (t/is (= (:cmd frame) :query))
;;         (t/is (= (:data frame) nil))
;;         (s/push! busin {:type :socket/message
;;                         :payload (pc/frame-encode
;;                                   {:cmd :response
;;                                    :id (:id frame)
;;                                    :data "foobar"})}))
;;       )))

;; (t/deftest novelty-frame-success-test
;;   (t/async done
;;     (let [[busin busout sock client] (make-client)]
;;       (m/mlet [frame (send-handshake sock)
;;                result (pc/novelty client :users {:id 1})]
;;         (t/is (= (:data result) {:ok true}))
;;         (done))

;;       (s/push! busin {:type :socket/open})
;;       (m/mlet [frame (to-promise busout)
;;                :let [frame (pc/frame-decode frame)]]
;;         (t/is (= (:cmd frame) :novelty))
;;         (t/is (= (:data frame) {:id 1}))
;;         (s/push! busin {:type :socket/message
;;                         :payload (pc/frame-encode
;;                                   {:cmd :response
;;                                    :id (:id frame)
;;                                    :data {:ok true}})}))
;;       )))


(t/deftest subscribe-acction-test
  (t/async done
    (let [[busin busout sock client] (make-client)]
      (m/mlet [frame (send-handshake sock)]
        (let [stream (pc/subscribe client :users)
              stream (s/log "foobar" stream)
              values (atom [])]
          (s/subscribe (s/take 2 stream)
                       #(swap! values conj %)
                       (constantly nil)
                       (fn []
                         (t/is (= 2 (count @values)))))))

      (s/push! busin {:type :socket/open})

      (m/mlet [frame1 (to-promise busout)
               :let [frame1 (pc/frame-decode frame1)
                     _ (t/is (= (:cmd frame1) :subscribe))
                     _ (t/is (= (:dest frame1) :users))
                     _ (js/setTimeout
                        (fn []
                          (s/push! busin {:type :socket/message
                                          :payload (pc/frame-encode
                                                    {:cmd :message
                                                     :id (random-uuid)
                                                     :subscription (:id frame1)
                                                     :data {:foo 1}})})
                          (s/push! busin {:type :socket/message
                                          :payload (pc/frame-encode
                                                    {:cmd :message
                                                     :id (random-uuid)
                                                     :subscription (:id frame1)
                                                     :data {:foo 1}})}))
                        0)]
               frame2 (to-promise busout)
               :let [frame2 (pc/frame-decode frame2)]]
        (t/is (= (:cmd frame2) :unsubscribe))
        (t/is (= (:id frame2) (:id frame1)))
        (done)))))

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
