(ns postal.frames
  "A frame types definition.")

(defrecord Frame [command headers body])

(defn frame
  "A generic frame constructor."
  ([command headers]
   (frame command headers ""))
  ([command headers body]
   (Frame. command headers body)))

(defn query
  "A QUERY frame constructor."
  ([headers]
   (query headers ""))
  ([headers body]
   (frame :query headers body)))

(defn novelty
  "A NOVELTY frame constructor."
  ([headers]
   (novelty headers ""))
  ([headers body]
   (frame :novelty headers body)))

(defn subscribe
  "A SUBSCRIBE frame constructor."
  ([headers]
   (subscribe headers ""))
  ([headers body]
   (frame :subscribe headers body)))

(defn unsubscribe
  "A UNSUBSCRIBE frame constructor."
  ([headers]
   (unsubscribe headers ""))
  ([headers body]
   (frame :unsubscribe headers body)))

(defn publish
  "A PUBLISH frame constructor."
  ([headers]
   (publish headers ""))
  ([headers body]
   (frame :publish headers body)))

(defn put
  "A PUT frame constructor."
  ([headers]
   (put headers ""))
  ([headers body]
   (frame :put headers body)))

(defn take
  "A TAKE frame constructor."
  ([headers]
   (take headers ""))
  ([headers body]
   (frame :take headers body)))

(defn consume
  "A TAKE frame constructor."
  ([headers]
   (consume headers ""))
  ([headers body]
   (frame :consume headers body)))

(defn frame?
  "Return true if a provided frame is a true
  instance of Frame type."
  [frame]
  (instance? Frame frame))
