(ns postal.log
  "A lightweight logging abstraction."
  #?(:cljs (:require-macros [postal.log :refer [warn trace]])))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Global Vars declaration
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

#?(:cljs
   (defonce ^:dynamic
     *level* 5))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Helpers
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

#?(:clj
   (defmacro trace
     "Show a trace log message in a console."
     [& args]
     `(when (>= *level* 5)
        (println "[trace]:" ~@args))))

#?(:clj
   (defmacro warn
     "Show a error or warning message in a console.

     This macro baypasses any debug control flag
     and prints directly in a console."
     [& args]
     `(when (>= *level* 1)
        (println "[trace]:" ~@args))))
