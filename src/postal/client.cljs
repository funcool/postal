(ns postal.client
  (:require [cognitect.transit :as t]
            [promesa.core :as p]
            [beicon.core :as s]
            [goog.crypt.base64 :as b64]
            [httpurr.client :as http]
            [httpurr.status :as http-status]
            [httpurr.client.xhr :as xhr])
  (:import goog.Uri.QueryData
           goog.Uri))

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
   (query client dest nil {}))
  ([client dest data]
   (query client dest data {}))
  ([client dest data opts]
   (send! client (merge {:type :novelty
                         :dest dest
                         :data data}
                        opts))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; EventSource
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defprotocol ISubscription
  (-stream [_] "Get the stream")
  (-close [_] "Close subscription"))

(deftype Subscription [bus closed evs]
  (-stream [_]
    (s/to-observable bus))
  (-close [_]
    (.close evs)
    (s/end! bus)))

(defn subscribe
  [client dest data {:keys [params] :or {params {}}}]
  (let [req (prepare-request client data :get params)
        uri (Uri. (:url req))
        bus (s/bus)]
    (.setQuery uri (:query-string req))
    (letfn [(on-message [event]
              (let [message (decode (.-data event))]
                (s/push bus message)))
            (on-error [event]
              (s/error! bus event))]
      (let [evs (js/EventSOurce (.toString uri))]
        (.addEventListener evs "open" on-open)
        (.addEventListener evs "message" on-message)
        (.addEventListener evs "error" on-error)
        (Subscription. bus (atom false) evs)))))
