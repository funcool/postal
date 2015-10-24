(ns postal.client
  (:require [cognitect.transit :as t]
            [promesa.core :as p]
            [cats.core :as m]
            [goog.crypt.base64 :as b64]
            [httpurr.client :as http]
            [httpurr.status :as http-status]
            [httpurr.client.xhr :as xhr]))

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
  [client data]
  (let [req {:headers (merge +default-headers+ @(:headers client))
             :method (:method client)
             :url (:url client)}]
    (if (= :method :get)
      (merge req {:query-string (str "d=" (b64/encodeString data true))})
      (merge req {:body data}))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; The basic client interface.
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defrecord Client [headers method url])

(defn client
  "Creates a new client instance from socket."
  ([url]
   (client url {}))
  ([url {:keys [headers method] :or {headers {} method :put}}]
   (Client. (atom headers) method url)))

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
