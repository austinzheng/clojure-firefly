(ns clojure-blog.tags
  (:require 
    [clojure.string :as s]))

(defn remove-empty [tags-list]
  (filter #(and (not (nil? %)) (> (count %) 0)) tags-list))

(defn split-raw-tags [raw-tags]
  "Split a raw string containing comma-separated tags into a list of tags, excepting empty or blank tags"
  (remove-empty (s/split raw-tags #"\s*,\s*")))

(defn join-tags [tags-list]
  "Join a list of tags into a single string, with tags separated by commas"
  (if (= 0 (count tags-list))
    ""
    (s/join ", " tags-list)))
