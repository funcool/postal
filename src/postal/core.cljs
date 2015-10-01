(ns postal.core
  (:require [postal.socket :as ps]
            [postal.log :as plog :include-macros true]
            [cognitect.transit :as t]
            [promesa.core :as p]
            [beicon.core :as s]
            [cats.core :as m]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Serializers
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn frame-encode
  [data]
  (let [w (t/writer :json)]
    (t/write w data)))

(defn frame-decode
  [data]
  (let [r (t/reader :json {:handlers {"u" ->UUID}})]
    (t/read r data)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Encoding & Decoding
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn handle-socket-input
  [client input-bus]
  (letfn [(decode [{:keys [type payload] :as message}]
            {:type :client/message
             :payload (frame-decode payload)})]
    (let [socket (:socket client)
          stream (->> (ps/-stream socket)
                      (s/filter #(= (:type %) :socket/message))
                      (s/map decode))]
      (s/on-value stream #(s/push! input-bus %)))))

(declare handshake)
(declare wait-frame)
(declare fatal-state!)
(declare do-handshake)

(defn handle-handshake
  [client input-bus]
  (let [socket (:socket client)
        open (:open client)
        socket-stream (ps/-stream socket)
        close-stream (s/filter #(= (:type %) :socket/close) socket-stream)
        open-stream (s/filter #(= (:type %) :socket/open) socket-stream)]
    (letfn [(on-close-event [_]
              (let [msg {:type :client/status :payload {:open false}}]
                (s/push! input-bus msg)))
            (on-open-event [_]
              (-> (do-handshake client input-bus)
                  (p/then on-handshake-success)
                  (p/catch on-handshake-error)))
            (on-handshake-success [_]
              (let [msg {:type :client/status :payload {:open true}}]
                (vreset! open true)
                (s/push! input-bus msg)))
            (on-handshake-error [_]
              (vreset! open false)
              (s/end! input-bus)
              (fatal-state! client))]
      (s/on-value close-stream on-close-event)
      (s/on-value open-stream on-open-event))))

(defn do-handshake
  [client]
  (let [frame {:cmd :hello}
        socket (:socket client)]
    (ps/-send socket (frame-encode frame))
    (wait-frame client :hello nil)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Client Constructor.
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def ^:dynamic
  *default-config*
  {:output-buffersize 256
   :input-buffersize 256
   :handshake-timeout 1000
   :default-timeout 1000
   :debug false})

(defrecord Client [socket options open input-stream output-bus message-stream])

(defn client
  "Creates a new client instance from socket."
  ([uri]
   (client uri {}))
  ([uri options]
   (let [socket (ps/-create uri)
         open (volatile! false)
         input-bus (s/bus)
         output-bus (s/bus)
         input-stream (s/to-observable input-bus)
         message-stream (->> input-stream
                             (s/filter #(= (:type %) :client/message))
                             (s/map :payload))
         client (map->Client {:socket socket
                              :options options
                              :open open
                              :input-stream input-stream
                              :output-bus output-bus
                              :message-stream message-stream})]
     (handle-socket-input client input-bus)
     (handle-handshake client input-bus)
     client)))

(defn client?
  "Return true if a privided client is instance
  of Client type."
  [client]
  (instance? Client client))

(defn closed?
  [client]
  (let [open (:open client)]
    (not @open)))

(defn- fatal-state!
  "Set a client in fatal state.

  This can hapens in client initialization, initial handshake
  and other similar situations where the user can't take any
  action.  This closes the socket and set a client into no
  usable state."
  [client data]
  (let [socket (:socket client)]
    (plog/warn "The client enters in fatal state")
    (ps/-close socket)))

(defn- send-frame!
  [client frame]
  (let [socket (:socket client)]
    (ps/-send socket (frame-encode frame))
    (wait-frame client :response (:id frame))))

(defn query
  "Sends a :query frame to the server."
  ([client dest]
   (query client dest nil nil))
  ([client dest data]
   (query client dest data nil))
  ([client dest data opts]
   (let [socket (:socket client)
         frame {:cmd :query
                :id (random-uuid)
                :dest dest
                :data data}]
    (ps/-send socket (frame-encode frame))
    (wait-frame client :response (:id frame)))))

(defn novelty
  "Sends a :novelty frame to the server."
  ([client dest]
   (query client dest nil nil))
  ([client dest data]
   (query client dest data nil))
  ([client dest data opts]
   (let [socket (:socket client)
         frame {:cmd :novelty
                :id (random-uuid)
                :dest dest
                :data data}]
    (ps/-send socket (frame-encode frame))
    (wait-frame client :response (:id frame)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Helpers
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def ^:private noop (constantly nil))

(defn- wait-frame
  [client frametype msgid]
  (let [message-stream (:message-stream client)
        wait-frames #{frametype :error}]
    (p/promise
     (fn [resolve reject]
       (let [matchfn #(and (wait-frames (:cmd %)) (= (:id %) msgid))
             stream (->> message-stream
                         (s/filter matchfn)
                         (s/take 1))]
         (s/subscribe stream
                      #(condp = (:cmd %)
                         frametype (resolve %)
                         :error (reject %))
                      #(reject %)
                      noop))))))
