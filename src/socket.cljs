(ns postal.socket
  "A basic socket interface abstraction."
  (:require [beicon.core :as s]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; The socket abstraction
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defprotocol IWebSocket
  (-stream [_] "Get the socket stream.")
  (-send [_ data] "Send data to the socket.")
  (-close [_] "Close the socket."))

(defprotocol IWebSocketFactory
  (-create [_] "Create a websocket instance."))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Implementation
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defrecord WebSocket [ws bus]
  IWebSocket
  (-stream [_]
    (s/to-observable bus))

  (-send [_ data]
    (.send ws data))

  (-close [_]
    (.close ws)
    (s/end! bus)))

(defn- listener
  [type bus event]
  (let [data (.-data event)]
    (s/push! bus {:type type :payload data ::event event})))

(defn- websocket*
  [ws]
  (let [bus (a/bus)]
    (set! (.-onmessage ws) (partial listener bus :socket/message))
    (set! (.-onclose ws) (partial listener bus :socket/close))
    (set! (.-onopen ws) (partial listener bus :socket/open))
    (set! (.-onerror ws) (partial listener bus :socket/error))
    (WebSocket. ws bus)))

(defrecord FakeWebSocket [busin busout]
  IWebSocket
  (-stream [_]
    (s/to-observable busin))

  (-send [_ data]
    (s/push! busout data))

  (-close [_]
    (s/end! busin)
    (s/end! busout)))

(declare websocket)

(extend-protocol IWebSocketFactory
  string
  (-create [uri]
    (websocket uri))

  WebSocket
  (-create [it]
    it)

  FakeWebSocket
  (-create [it]
    it))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Public Api
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn websocket
  [url]
  (let [ws (js/WebSocket. url)]
    (websocket* url)))

(defn fake-websocket
  [busin busout]
  (let [busin (s/bus)
        busout (s/bus)]
    (FakeWebSocket. busin busout)))
