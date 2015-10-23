(ns postal.client
  (:require [cognitect.transit :as t]
            [promesa.core :as p]
            [cats.core :as m]
            [httpurr.client :as http]
            [httpurr.status :as http-status]
            [httpurr.client.xhr :as xhr]))

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
;; Encoding & Decoding
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defrecord Client [headers method url])

(defn client
  "Creates a new client instance from socket."
  ([url]
   (client url {}))
  ([url {:keys [headers method] :or {headers {} method :put}}]
   (Client. headers method url)))

(defn client?
  "Return true if a privided client is instance
  of Client type."
  [client]
  (instance? Client client))

(def ^:private
  +default-headers+
  {"content-type" "application/transit+json"})

(defn- process-response
  [response]
  (if (http-status/success? response)
    (let [message (decode (:body response))]
      (if (identical? (:type message) :error)
        (p/rejected message)
        (p/resolved message)))
    (p/rejected (ex-info "Unexpected" response))))

(defn- send-message
  [client type dest data {:keys [headers] :as opts}]
  {:pre [(or (map? data)
             (nil? data))
         (keyword? dest)
         (client? client)]}
  (let [hds (merge +default-headers+
                   (:headers client))
        req {:url (:url client)
             :method (:method client)
             :body (encode {:data data
                            :dest dest
                            :type type})
             :headers hds}]
    (-> (http/send! xhr/client req)
        (p/then process-response))))

(defn query
  "Sends a query message to a server."
  ([client dest]
   (query client dest nil {}))
  ([client dest data]
   (query client dest data {}))
  ([client dest data opts]
   (send-message client :query dest data opts)))

(defn novelty
  "Sends a novelty message to a server."
  ([client dest]
   (query client dest nil {}))
  ([client dest data]
   (query client dest data {}))
  ([client dest data opts]
   (send-message client :novelty dest data opts)))
