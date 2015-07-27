(ns postal.parser
  #?(:cljs (:import goog.string.StringBuffer))
  (:require [postal.reader :as reader]
            [postal.frames :as frames]
            [cuerdas.core :as str]))

;; this character denotes the end of a frame.
(def ^:dynamic *frame-end* nil) ;; ASCII null

;; this character denotes the end of a line
(def ^:dynamic *line-end* \newline) ;; ASCII linefeed

;; delimeter between attributes and values
(def ^:dynamic *header-delimiter* \:) ;; ASCII colon

(defrecord Frame [command headers body])

(defn peek-for
  "Returns the next character from the Reader, checks it against the
  expected value, then pushes it back on the reader."
  [peek-ch reader]
  (let [ch (reader/read-char reader)]
    (reader/unread reader ch)
    (if (= ch peek-ch) true false)))

(defn read-command
  "Reads in the command on the POSTAL frame."
  [reader]
  (loop [chr-in (reader/read-char reader)
         buffer []]
    (cond
      (= chr-in *frame-end*)
      (throw (#?(:clj RuntimeException. :cljs js/Error.)
                "End of frame reached while reading command"))

      (= chr-in *line-end*)
      (keyword (str/lower (apply str buffer)))

      :else
      (recur (reader/read-char reader)
             (conj buffer (char chr-in))))))

(defn read-header-key
  "Reads in the key name for a POSTAL header."
  [reader]
  (loop [chr-in (reader/read-char reader)
         buffer []]
    (cond
      (= chr-in *frame-end*)
      (throw (#?(:clj RuntimeException. :cljs js/Error.)
              "End of frame reached while reading header key"))

      (= chr-in *header-delimiter*)
      (apply str buffer)

      :else
      (recur (reader/read-char reader)
             (conj buffer (char chr-in))))))

(defn read-header-value
  "Reads in the value for a POSTAL header."
  [reader]
  (loop [chr-in (reader/read-char reader)
         buffer []]
    (cond
      (= chr-in *frame-end*)
      (throw (#?(:clj RuntimeException. :cljs js/Error.)
              "End of frame reached while reading header value"))

      (= chr-in *line-end*)
      (apply str buffer)

      :else
      (recur (reader/read-char reader)
             (conj buffer (char chr-in))))))

(defn read-body
  "Lazily reads in the body of a POSTAL frame."
  [reader]
  (let [sb (StringBuffer.)]
    (loop [chr-in (reader/read-char reader)]
      (cond
        ;; the frame end marker should be the last bit of data
        (and (= chr-in *frame-end*) (not (peek-for nil reader)))
        (throw (#?(:clj RuntimeException. :cljs js/Error.)
                "End of frame reached while reading body"))

        (= chr-in nil)
        (.toString sb)

        (not= chr-in *frame-end*)
        (do
          (.append sb (char chr-in))
          (recur (reader/read-char reader)))))))

(defn parse-frame
  "Parses the POSTAL frame data from the provided reader into a
  hash-map (frame-struct)."
  [reader]
  (loop [chr-in (reader/read-char reader)
         parsing :command
         frame {}]
    (cond
      (= chr-in nil)
      (throw (#?(:clj RuntimeException. :cljs js/Error.)
              "End of file reached without an end of frame"))

      (= parsing :command)
      (do
        (reader/unread reader chr-in)
        (recur ::continue :headers (assoc frame :command (read-command reader))))

      (= parsing :headers)
      (do
        (recur ::continue
               (if (peek-for *line-end* reader) :body :headers)
               (if (peek-for *line-end* reader) frame
                   (assoc frame :headers (assoc (:headers frame)
                                                (keyword (str/lower (read-header-key reader)))
                                                (read-header-value reader))))))

      (= parsing :body)
      (do
        (reader/read-char reader)
        (recur ::continue
               :complete
               (assoc frame :body (read-body reader))))

      (= parsing :complete)
      (frames/frame (:command frame)
                    (:headers frame)
                    (:body frame))

      (= chr-in ::continue)
      (recur (reader/read-char reader)
             parsing
             frame))))

