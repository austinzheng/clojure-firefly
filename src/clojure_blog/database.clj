;; database.clj (clojure-firefly)
;; Copyright (c) 2014 Austin Zheng
;; Released under the terms of the MIT License

(ns clojure-blog.database
  (:require 
    [taoensso.carmine :as car :refer (wcar)]))

;; NOTE: Don't require this file directly. It contains shared definitions and functionality used by the module-specific
;; database handlers.

;; Database initialization
(def server1-conn {:pool {} :spec {}})
(defmacro wcar* [& body] `(car/wcar server1-conn ~@body))

;; Convenience macros
; TODO: Make these even better.
(defmacro op-get-or-nil* [operation]
  "Perform a checked get-type operation and return the result, or nil if the operation fails"
  `(try ~operation (catch Exception e# nil)))

(defmacro op-set-or-false* [operation]
  "Perform a checked set-type operation and return true if it succeeded, false if an exception was thrown"
  `(try (do ~operation true) (catch Exception e# false)))
