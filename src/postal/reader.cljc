(ns postal.reader
  "A lightweight api for the push back reader
  defined in clojure.tools.reader."
  #?(:cljs
     (:require [cljs.tools.reader.reader-types :as reader])
     :clj
     (:require [clojure.tools.reader.reader-types :as reader])))

(defn pushback-reader
  "Create an instance of the push back reader
  for the provided data."
  [data]
  (reader/string-push-back-reader data))

(defn read-char
  "Read a char from the reader."
  [reader]
  (reader/read-char reader))

(defn unread
  "Push back the previously readed char."
  [reader char]
  (reader/unread reader char))
