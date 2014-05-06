;; blog_database.clj (clojure-firefly)
;; Copyright (c) 2014 Austin Zheng
;; Released under the terms of the MIT License

(ns clojure-blog.blog-database
  (:use clojure-blog.database)
  (:require 
    [clojure-blog.tags :as tags]
    [clojure-blog.util :as util]
    [taoensso.carmine :as car :refer (wcar)]
    [clj-time.core :as time-core]
    [clj-time.coerce :as time-coerce]
    [clojure.set :as cset]))

;; Forward declarations
(declare tags-for-post-key)
(declare set-tags-for-post!)


;; API (posts)
(defn- post-base-key [post-id] 
  (apply str ["post:" post-id]))

(defn total-post-count []
  (try (wcar* (car/llen :post-list)) (catch Exception e 0)))

(defn get-post [post-id]
  "Get a blog post (as post-map) from redis based on the post ID, or nil if it doesn't exist." 
  (let [
    raw-map (op-get-or-nil* (wcar* (car/parse-map (car/hgetall (post-base-key post-id)))))
    tag-list-key (tags-for-post-key post-id)
    tags (op-get-or-nil* (wcar* (car/lrange tag-list-key 0 -1)))]
    (when raw-map
      (assoc (util/map-function-on-map-keys raw-map keyword) :post-tags (reverse tags)))))

(defn get-posts [start num-posts]
  "Get a number of blog posts from redis, with a start index and a count. If either the start index or count are
  invalid, returns nil. Returns a 2-tuple consisting of a list of post-data maps and a metadata map."
  (let [
    post-count (try (wcar* (car/llen :post-list)) (catch Exception e 0))
    adj-num-posts (min num-posts (- post-count start))
    can-retrieve (and (>= start 0) (< start post-count))]
    (when can-retrieve 
      (let [
        id-seq (seq (wcar* (car/lrange :post-list start (+ -1 start adj-num-posts))))
        post-data (map get-post id-seq)] 
        ;; Honestly, would prefer to get post-data pipelined. But I couldn't get macros to do what I wanted.
        [post-data {:post-count post-count :id-seq id-seq}]))))

(defn get-posts-for-ids [post-ids]
  "Get a number of blog posts from redis, same as get-posts, but specifying a list of post IDs."
  (if 
    (or (nil? post-ids) (= 0 (count post-ids)))
    nil
    (let [
      post-data (map get-post post-ids)
      post-count (count post-data)]
      [post-data {:post-count post-count :id-seq post-ids}])))

(defn add-post! [post-title post-content tags]
  "Add a new post to the database; returns the post ID, or nil if failed."
  (let [
    post-id (wcar* (car/incr :post-next-id))
    post-date (time-coerce/to-long (time-core/now))
    hash-key (post-base-key post-id)]
    (wcar* 
      (car/multi)
      (car/lpush :post-list post-id)
      (car/hset hash-key :post-title post-title)
      (car/hset hash-key :post-date post-date)
      (car/hset hash-key :post-content post-content)
      (car/hset hash-key :post-edited false)
      (car/hset hash-key :post-edit-date nil))
    ; Add the keys
    (when tags (set-tags-for-post! (util/parse-integer post-id) tags ()))
    (try 
      (do (wcar* (car/exec)) (wcar* (car/bgsave)) post-id) 
      (catch Exception e 
        (do 
          (println (reduce str ["Received an exception when adding a post. Message: " (.getMessage e)]))
          nil)))))

(defn edit-post! [post-id post-title post-content tags prev-tags]
  "Replaces the contents of post post-id with post-content, and the names as well. Returns true if successful, false
  otherwise. If post-id does not exist, it will not be created."
  (let [
    edit-date (time-coerce/to-long (time-core/now))
    hash-key (post-base-key post-id)
    key-exists (= 1 (wcar* (car/exists hash-key)))
    tags (if tags tags ())
    prev-tags (if prev-tags prev-tags ())]
    (if key-exists
      (do 
        (wcar*
          (car/multi)
          (car/hset hash-key :post-title post-title)
          (car/hset hash-key :post-content post-content)
          (car/hset hash-key :post-edited true)
          (car/hset hash-key :post-edit-date edit-date))
        (set-tags-for-post! (util/parse-integer post-id) tags prev-tags)
        (try 
          (do (wcar* (car/exec)) (wcar* (car/bgsave)) true) 
          (catch Exception e false)))
      false)))

(defn delete-post! [post-id]
  "Delete a post from the database. Returns true if successful, false otherwise."
  (let [
    hash-key (post-base-key post-id)
    key-exists (= 1 (wcar* (car/exists hash-key)))
    tag-list-key (tags-for-post-key post-id)
    tags (op-get-or-nil* (wcar* (car/lrange tag-list-key 0 -1)))]
    (if key-exists
      (do
        (wcar* 
          (car/multi)
          (car/lrem :post-list 0 post-id)
          (car/del (post-base-key post-id)))
        (set-tags-for-post! (util/parse-integer post-id) () tags)
        (try 
          (do (wcar* (car/exec)) (wcar* (car/bgsave)) true) 
          (catch Exception e false)))
      false)))


;; API (metadata)
(defn get-post-metadata [post-id]
  "Get a blog post's creation date and title, based on the post ID"
  (let [
    post-key (post-base-key post-id)
    [post-title post-date] (wcar* (car/hget post-key :post-title) (car/hget post-key :post-date))]
    {:post-id post-id, :post-title post-title, :post-date post-date}))

(defn get-metadata-for-ids [post-ids]
  "Get metadata for a vector of post ID values."
  (doall (map #(get-post-metadata %) post-ids)))

(defn get-all-metadata []
  "Get metadata for all posts"
  (let [all-ids (op-get-or-nil* (wcar* (car/lrange :post-list 0 -1)))]
    (when all-ids (get-metadata-for-ids all-ids))))


;; API (tags)
(defn- tags-for-post-key [post-id]
  (apply str ["post:tags:" post-id]))

(defn- posts-for-tag-key [tag-name]
  (apply str ["tag:" tag-name]))

(defn- set-tags-for-post! [post-id tags prev-tags]
  "Given a post and a number of tags, register the tags for the post."
  (let [
    tags-set (set (tags/remove-empty tags))
    prev-tags-set (set (tags/remove-empty prev-tags))
    added (sort (into [] (cset/difference tags-set prev-tags-set)))
    removed (sort (into [] (cset/difference prev-tags-set tags-set)))
    list-key (tags-for-post-key post-id)]
    (try 
      ; Update the post's tag list by adding new tags and removing old ones
      (doall (map #(wcar* (car/lpush list-key %)) added))
      (doall (map #(wcar* (car/lrem list-key 0 %)) removed))
      (doall (map #(let [k (posts-for-tag-key %)] (wcar* (car/lpush k post-id))) added))
      (doall (map #(let [k (posts-for-tag-key %)] (wcar* (car/lrem k 0 post-id))) removed))
      true
      (catch Exception e false))))

(defn- post-ids-for-tag [tag-name]
  (let [
    valid (tags/tag-valid? tag-name)
    k (when valid (posts-for-tag-key tag-name))]
    (when k (op-get-or-nil* (wcar* (car/lrange k 0 -1))))))

(defn get-posts-for-tag [tag-name]
  (get-posts-for-ids (post-ids-for-tag tag-name)))
