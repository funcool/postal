(ns postal.frames
  "A frame types definition."
  (:refer-clojure :exclude [take]))

(defrecord Frame [command headers body])

(defn frame
  "A generic frame constructor."
  ([command]
   (frame command "" {}))
  ([command body]
   (frame command body ""))
  ([command body headers]
   (Frame. command headers body)))

(defn query
  "A QUERY frame constructor."
  ([body]
   (query body {}))
  ([body headers]
   (frame :query body headers)))

(defn novelty
  "A NOVELTY frame constructor."
  ([body]
   (novelty body {}))
  ([body headers]
   (frame :novelty body headers)))

(defn subscribe
  "A SUBSCRIBE frame constructor."
  ([body]
   (subscribe body {}))
  ([body headers]
   (frame :subscribe body headers)))

(defn unsubscribe
  "A UNSUBSCRIBE frame constructor."
  ([body]
   (subscribe body {}))
  ([body headers]
   (frame :unsubscribe body headers)))

(defn publish
  "A PUBLISH frame constructor."
  ([body]
   (subscribe body {}))
  ([body headers]
   (frame :publish body headers)))

(defn put
  "A PUT frame constructor."
  ([body]
   (subscribe body {}))
  ([body headers]
   (frame :put body headers)))

(defn take
  "A TAKE frame constructor."
  ([body]
   (subscribe body {}))
  ([body headers]
   (frame :take body headers)))

(defn consume
  "A CONSUME frame constructor."
  ([body]
   (subscribe body {}))
  ([body headers]
   (frame :consume body headers)))

(defn response
  "A RESPONSE frame constructor."
  ([body]
   (subscribe body {}))
  ([body headers]
   (frame :response body headers)))

(defn message
  "A MESSAGE frame constructor."
  ([body]
   (subscribe body {}))
  ([body headers]
   (frame :message body headers)))

(defn error
  "A ERROR frame constructor."
  ([body]
   (subscribe body {}))
  ([body headers]
   (frame :error body headers)))

(defn frame?
  "Return true if a provided frame is a true
  instance of Frame type."
  [frame]
  (instance? Frame frame))

(defn error?
  "Return true if a provided frame is a true
  instance of Frame and it is of error type."
  [frame]
  (and (frame? frame)
       (= (:command frame) :error)))

(defn response?
  "Return true if a provided frame is a true
  instance of Frame and it is of response type."
  [frame]
  (and (frame? frame)
       (= (:command frame) :response)))

(defn message?
  "Return true if a provided frame is a true
  instance of Frame and it is of message type."
  [frame]
  (and (frame? frame)
       (= (:command frame) :message)))
