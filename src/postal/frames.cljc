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

(defn frame?
  "Return true if a provided frame is a true
  instance of Frame type."
  [frame]
  (instance? Frame frame))
