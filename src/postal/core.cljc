(ns postal.core
  "A public api for POSTAL frames parser and renderer."
  (:require [postal.reader :as reader]
            [postal.frames :as frames]
            [postal.renderer :as renderer]
            [postal.parser :as parser]))

(defn parse
  "Parses the provided POSTAL frame data into a
  hash-map (frame-struct). It will read the headers greedily but
  return the body of the frame lazily; the body will be a sequence."
  [data]
  (let [reader (reader/pushback-reader data)]
    (parser/parse-frame reader)))

(defn render
  "Render a provided frame instance into a string."
  [frame]
  {:pre [(frames/frame? frame)]}
  (renderer/render frame))
