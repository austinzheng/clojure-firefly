;; tags.clj (clojure-firefly)
;; Copyright (c) 2014 Austin Zheng
;; Released under the terms of the MIT License

(ns clojure-blog.tags
  (:require 
    [clojure-blog.routes :as r]
    [clojure.string :as s]
    [ring.util.codec :as codec]))

(defn tag-valid? [tag]
  "Return whether a potential tag value is valid for further operations"
  (and 
    (string? tag)
    (> (count tag) 0)))

(defn remove-empty [tags-list]
  (filter tag-valid? tags-list))

(defn split-raw-tags [raw-tags]
  "Split a raw string containing comma-separated tags into a list of tags, excepting empty or blank tags"
  (remove-empty (s/split raw-tags #"\s*,\s*")))

(defn join-tags [tags-list]
  "Join a list of tags into a single string, with tags separated by commas"
  (if (= 0 (count tags-list))
    ""
    (s/join ", " tags-list)))

(defn tags-html-for-tags [tags-list]
  "Given a list of tags, turn it into HTML appropriate for the tags system"
  (if (or (nil? tags-list) (= 0 (count tags-list))) 
    "(No tags)"
    (let [
      preceding-tags (butlast tags-list)
      last-tag (last tags-list)
      formatted-preceding (reduce str (map #(reduce str ["<a href=\"" (r/blog-posts-for-tag-route (codec/url-encode %)) "\">" % "</a>, "]) preceding-tags))
      formatted-last (reduce str ["<a href=\"" (r/blog-posts-for-tag-route (codec/url-encode last-tag)) "\">" last-tag "</a>"])]
      (reduce str ["Tags: " formatted-preceding formatted-last]))))
