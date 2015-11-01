(ns postal.client
  (:require [cognitect.transit :as t]
            [promesa.core :as p]
            [beicon.core :as s]
            [goog.crypt.base64 :as b64]
            [goog.events :as events]
            [httpurr.client :as http]
            [httpurr.status :as http-status]
            [httpurr.client.xhr :as xhr])
  (:import [goog.net WebSocket]
           [goog.net.WebSocket EventType]
           [goog.Uri QueryData]
           [goog Uri Timer]))

(def ^:private
  +default-headers+
  {"content-type" "application/transit+json"})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Data encoding/decoding
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn decode
  [data]
  (let [r (t/reader :json {:handlers {"u" ->UUID}})]
    (t/read r data)))

(defn encode
  [data]
  (let [w (t/writer :json)]
    (t/write w data)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Implementation Details
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- process-response
  [response]
  (if (http-status/success? response)
    (let [message (decode (:body response))]
      (if (identical? (:type message) :error)
        (p/rejected message)
        (p/resolved message)))
    (p/rejected (ex-info "Unexpected" response))))

(defn- prepare-request
  ([client data]
   (prepare-request client data (:method client) {}))
  ([client data method params]
   (let [req {:headers (merge +default-headers+ @(:headers client))
              :method method
              :url (:url client)}]
     (if (= method :get)
       (let [pd (.clone (:params client))]
         (when-not (empty? params)
           (.extend pd (clj->js params)))
         (.set pd "d" (b64/encodeString data true))
         (merge req {:query-string (.toString pd)}))
       (if (empty? params)
         (let [pd (:params client)]
           (merge req {:body data :query-string (.toString pd)}))
         (let [pd (.clone (:params client))]
           (.extend pd (clj->js params))
           (merge req {:body data :query-string (.toString pd)})))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; The basic client interface.
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defrecord Client [headers method url params])

(defn client
  "Creates a new client instance from socket."
  ([url]
   (client url {}))
  ([url {:keys [headers method params]
         :or {headers {} method :put params {}}}]
   (let [paramsdata (QueryData.)]
     (.extend paramsdata (clj->js params))
     (Client. (atom headers) method url paramsdata))))

(defn client?
  "Return true if a privided client is instance
  of Client type."
  [client]
  (instance? Client client))

(defn update-headers!
  "Update the headers on the client instance."
  [c headers]
  {:pre [(client? c)]}
  (let [ha (:headers c)]
    (swap! ha merge headers)))

(defn reset-headers!
  "Reset the headers on the client instance."
  [c headers]
  {:pre [(client? c)]}
  (let [ha (:headers c)]
    (reset! ha headers)))

(defn send!
  [client {:keys [type dest data headers] :as opts}]
  {:pre [(or (map? data)
             (nil? data))
         (keyword? dest)
         (client? client)]}
  (let [data (encode {:data data :dest dest :type type})
        req (prepare-request client data)]
    (-> (http/send! xhr/client req)
        (p/then process-response))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Reques/Reply Pattern
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn query
  "Sends a query message to a server."
  ([client dest]
   (query client dest nil {}))
  ([client dest data]
   (query client dest data {}))
  ([client dest data opts]
   (send! client (merge {:type :query
                         :dest dest
                         :data data}
                        opts))))

(defn novelty
  "Sends a novelty message to a server."
  ([client dest]
   (novelty client dest nil {}))
  ([client dest data]
   (novelty client dest data {}))
  ([client dest data opts]
   (send! client (merge {:type :novelty
                         :dest dest
                         :data data}
                        opts))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Socket
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn socket
  ([client dest]
   (socket client dest nil nil))
  ([client dest data]
   (socket client dest data nil))
  ([client dest data {:keys [params _type] :or {params {} _type :socket}}]
   (let [frame (encode {:data data :dest dest :type _type})
         req (prepare-request client frame :get params)
         uri (Uri. (:url req))]
     (.setQuery uri (:query-string req))
     (.setScheme uri (if (= (.getScheme uri) "http") "ws" "wss"))
     (let [ws (WebSocket. false)
           timer (Timer. 5000)
           busin (s/bus)
           streamin (s/filter #(not= (:type %) :ping) busin)
           busout (s/bus)]
       (letfn [(on-ws-message [event]
                 (let [frame (decode (.-message event))]
                   (s/push! busin frame)))
               (on-ws-error [event]
                 (s/error! busin event)
                 (.close ws))
               (on-ws-closed [event]
                 (s/end! busout)
                 (s/end! busin))
               (on-timer-tick [_]
                 (let [frame {:type :ping}]
                   (s/push! busout frame)))
               (on-busout-value [msg]
                 (let [data (encode msg)]
                   (.send ws data)))
               (on-busout-end []
                 (.close ws))]

         (s/on-end busout on-busout-end)
         (s/on-value busout on-busout-value)

         (events/listen ws EventType.MESSAGE on-ws-message)
         (events/listen ws EventType.ERROR on-ws-error)
         (events/listen ws EventType.CLOSED on-ws-closed)
         (events/listen timer Timer.TICK on-timer-tick)

         (.start timer)
         (.open ws (.toString uri))

         [streamin busout])))))

(defn subscribe
  ([client dest]
   (subscribe client dest nil nil))
  ([client dest data]
   (subscribe client dest data nil))
  ([client dest data {:keys [params] :or {params {}} :as opts}]
   (let [[in out] (socket client dest data (assoc opts :_type :subscribe))
         bus (s/bus)]
     (s/subscribe in #(s/push! bus %) #(s/error! bus %) #(s/end! bus))
     (s/on-end bus #(s/end! out))
     bus)))
