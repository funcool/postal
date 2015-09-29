(ns postal.core
  (:require-macros [cljs.core.async.macros :refer [go-loop go]])
  (:require [postal.frames :as pf]
            [postal.core :as pc]
            [igorle.socket :as is]
            [igorle.log :as log :include-macros true]
            [cuerdas.core :as str]
            [promesa.core :as p]
            [cljs.core.async :as a]
            [cats.core :as m]
            [cats.monad.either :as either]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Serializers
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn frame-encode
  [data]
  )

(defn frame-decode
  [data]
  )

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Encoding & Decoding
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn handle-socket-input
  [client input-bus]
  (letfn [(decode [message]
            (let [payload (:payload message)]
              (assoc message :payload (frame-decode payload))))]
    (let [socket (:socket client)
          socket-stream (is/-stream socket)
          message-stream (->> socket-stream
                              (s/filter #(= (:type %) :socket/message))
                              (s/map #(assoc % :type :message))
                              (s/map decode)
                              (s/filter (comp not nil?)))]
      (s/on-value message-stream #(s/push input-bus %)))))

(declare handshake)
(declare wait-frame)
(declare fatal-state!)

(defn handle-handshake
  [client input-bus]
  (let [socket (:socket client)
        open (:open client)
        socket-stream (is/-stream socket)
        close-stream (s/filter socket-stream #(= (:type %) :socket/close))
        open-stream (s/filter socket-stream #(= (:type %) :socket/open))]
    (letfn [(on-close-event [_]
              (let [msg {:type :connected :payload false}]
                (s/push! input-bus msg)))
            (on-open-event [_]
              (-> (do-handshake client input-bus)
                  (p/then on-handshake-success)
                  (p/catch on-handshake-error)))
            (on-handshake-success [_]
              (let [msg {:type :connected :payload true}]
                (vreset! open true)
                (s/push! input-bus msg)))
            (on-handshake-error [_]
              (vreset! open false)
              (s/end! input-bus)
              (fatal-state! client))]
      (s/on-value close-stream on-close-event)
      (s/on-value open-stream on-open-event))))

(defn handshake
  [client]
  (let [frame {:cmd :hello}
        socket (:socket client)]
    (is/-send sock (frame-encode frame))
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
   (let [socket (is/-create uri)
         open (volatile! false)
         input-bus (s/bus)
         output-bus (s/bus)
         input-stream (s/to-observable input-bus)
         message-stream (->> input-stream
                             (s/filter #(= (:type %) :message))
                             (s/map :payload))
         client (map->Client {:socket socket
                              :options options
                              :open open
                              :input-stream input-stream
                              :output-bus output-bus
                              :message-stream message-stream})]
     (handle-socket-input client input-bus)
     (handle-handshake client)
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

(defn frame-with-id?
  "A predicate for check that frame comes with id."
  [frame]
  (boolean (:id frame)))

(defn- fatal-state!
  "Set a client in fatal state.

  This can hapens in client initialization, initial handshake
  and other similar situations where the user can't take any
  action.  This closes the socket and set a client into no
  usable state."
  [client data]
  (let [socket (:socket client)
        input-bus (:input-bus client)]
    (log/warn "The client enters in fatal state")

    (s/push! input-bus {:type :client/error :payload data})
    (s/end! input-bus)
    (is/-close sock)))

(defn- send-frame!
  [client frame]
  (m/mlet [frame (wait-frame client :response (:id frame))]
    ;; TODO: debug
    (m/return frame)))

(def random-id (comp str random-uuid))

(defn query
  "Sends a QUERY frame to the server."
  ([client dest]
   (query client dest nil nil))
  ([client dest data]
   (query client dest data nil))
  ([client dest data opts]
   (let [frame {:cmd :query
                :id (random-id)
                :dest dest
                :data data}]
     (send-frame! client frame))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Helpers
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- wait-frame
  [client frametype msgid]
  (let [busout (:output-bus client)
        message-stream (:message-stream client)
        wait-frames #{:response :error}]
    (p/promise
     (fn [resolve reject]
       (let [matchfn #(and (wait-frames (:cmd %)) (= (:id %) msgid))
             stream (->> message-stream
                         (s/filter matchfn)
                         (s/take 1))]
         (s/subscribe stream #(resolve %) #(reject %)))))))
