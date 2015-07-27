(ns postal.renderer
  "A frame rendering implementation."
  (:require [cuerdas.core :as str]
            [postal.parser :as parser]))

(defn render
  [frame]
  (with-out-str
    (print (str (str/upper (name (:command frame))) parser/*line-end*))

    ;; the headers
    (doseq [[key val] (:headers frame)]
      (print (str (name key) ":" (str/trim val) parser/*line-end*)))

    ;; the body
    (print parser/*line-end*)
    (print (str (:body frame)))))

