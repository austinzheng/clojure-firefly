;; util.clj (clojure-firefly)
;; Copyright (c) 2014 Austin Zheng
;; Released under the terms of the MIT License

(ns clojure-blog.util
  (:require
    [clojure-blog.settings :as settings]
    [clj-time.core :as t]
    [clj-time.format :as tf]
    [clj-time.coerce :as tc]))

(defn parse-integer [raw]
  (if (integer? raw) raw 
    (try (Long/parseLong raw)
      (catch Exception e nil))))

(defn map-function-on-map-keys [m func]
  "Take a map and return another map, with the keys transformed by some function"
  (reduce (fn [new-map [k v]] (assoc new-map (apply func [k]) v)) {} m))

(defn format-date [raw-date]
  "Given a raw date string (long as string), turn it into a formatted date-time string"
  (let [
    time-zone (t/time-zone-for-id settings/time-zone-id)
    f (tf/with-zone (tf/formatter "MM/dd/yyyy HH:mm") time-zone)
    as-long (parse-integer raw-date)
    as-obj (when as-long (tc/from-long as-long))]
    (if as-obj (tf/unparse f as-obj) "(Unknown)")))
