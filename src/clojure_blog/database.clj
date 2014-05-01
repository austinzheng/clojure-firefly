(ns clojure-blog.database
  (:require 
    [clojure-blog.tags :as cbtags]
    [clojure-blog.util :as cbutil]
    [taoensso.carmine :as car :refer (wcar)]
    [clj-time.core :as time-core]
    [clj-time.coerce :as time-coerce]
    [clojure.string :as string]
    [clojure.set :as cset]
    [clojure.data :as data]))

; Database initialization
(def server1-conn {:pool {} :spec {}})
(defmacro wcar* [& body] `(car/wcar server1-conn ~@body))

; Forward declarations
(declare tags-for-post-key)
(declare set-tags-for-post!)

; Posts API
(defn- post-base-key [post-id] 
  (apply str ["post:" post-id]))

(defn get-post [post-id]
  "Get a blog post (as post-map) from redis based on the post ID, or nil if it doesn't exist." 
  (let [
    raw-map (try 
      (wcar* 
        (car/parse-map 
          (car/hgetall 
            (post-base-key post-id)))) (catch Exception e nil))
    tag-list-key (tags-for-post-key post-id)
    tags (try (wcar* (car/lrange tag-list-key 0 -1)) (catch Exception e nil))]
    (when raw-map
      (assoc (cbutil/map-function-on-map-keys raw-map keyword) :post-tags (reverse tags)))))

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
        (try
          [post-data {:post-count post-count :id-seq id-seq}]
          (catch Exception e nil))))))

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
    (when tags (set-tags-for-post! (cbutil/parse-integer post-id) tags ()))
    (try 
      (do (wcar* (car/exec)) (wcar* (car/bgsave)) post-id) 
      (catch Exception e nil))))

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
        (set-tags-for-post! (cbutil/parse-integer post-id) tags prev-tags)
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
    tags (try (wcar* (car/lrange tag-list-key 0 -1)) (catch Exception e nil))]
    (if key-exists
      (do
        (wcar* 
          (car/multi)
          (car/lrem :post-list 0 post-id)
          (car/del (post-base-key post-id)))
        (set-tags-for-post! (cbutil/parse-integer post-id) () tags)
        (try 
          (do (wcar* (car/exec)) (wcar* (car/bgsave)) true) 
          (catch Exception e false)))
      false)))


; Image-related API


; Tag-related API
(defn- tags-for-post-key [post-id]
  (apply str ["post:tags:" post-id]))

(defn- posts-for-tag-key [tag-name]
  (apply str ["tag:" tag-name]))

(defn- set-tags-for-post! [post-id tags prev-tags]
  "Given a post and a number of tags, register the tags for the post."
  (let [
    tags-set (set (cbtags/remove-empty tags))
    prev-tags-set (set (cbtags/remove-empty prev-tags))
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


; Links-related API

; Comments?
