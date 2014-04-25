(ns clojure-blog.database
  (:require 
  	[clojure-blog.util :as cbutil]
    [taoensso.carmine :as car :refer (wcar)]
    [clj-time.local :as time-local]
    [clj-time.coerce :as time-coerce]
    [clojure.string :as string]))

; Database initialization
(def server1-conn {:pool {} :spec {}})
(defmacro wcar* [& body] `(car/wcar server1-conn ~@body))

; Helpers
(defn- post-base-key [post-id] 
  (apply str ["post:" post-id]))

; Post-related API
(defn get-post [post-id]
  "Get a blog post (as post-map) from redis based on the post ID, or nil if it doesn't exist." 
  (let [
    raw-map (try 
      (wcar* 
        (car/parse-map 
          (car/hgetall 
            (post-base-key post-id)))) (catch Exception e nil))]
    (if 
      raw-map 
      (cbutil/map-function-on-map-keys raw-map keyword) 
      nil)))

(defn get-posts [start num-posts]
  "Get a number of blog posts from redis, with a start index and a count. If either the start index or count are
  invalid, returns nil. Returns a tuple, the first element is a list of IDs and the second element is a list of
  post-map objects."
  (let [
    post-count (wcar* (car/llen :post-list))
    adj-num-posts (min num-posts (- post-count start))
    can-retrieve (and (>= start 0) (< start post-count))]
    (if can-retrieve 
      (let [
        id-seq (seq (wcar* (car/lrange :post-list start (+ -1 start adj-num-posts))))
        getter-func (defn gf [so-far remaining]
          ; Doing this recursively, because nested macros and map failed hilariously
          (if (= remaining ()) 
            so-far
            (gf 
              (cons 
                (cbutil/map-function-on-map-keys 
                  (-> remaining
                    first
                    post-base-key
                    car/hgetall
                    car/parse-map
                    wcar*) keyword) so-far)
              (rest remaining))))]
        (try
          [id-seq (gf [] (reverse id-seq))]
          (catch Exception e nil))))))

(defn add-post! [post-title post-content]
  "Add a new post to the database; returns the post ID, or nil if failed."
  (let [
    post-id (wcar* (car/incr :post-next-id))
    post-date (time-coerce/to-long (time-local/local-now))
    hash-key (post-base-key post-id)]
    (wcar* 
      (car/multi)
      (car/lpush :post-list post-id)
      (car/hset hash-key :post-title post-title)
      (car/hset hash-key :post-date post-date)
      (car/hset hash-key :post-content post-content)
      (car/hset hash-key :post-edited false)
      (car/hset hash-key :post-edit-date nil))
    (try 
      (do (wcar* (car/exec)) (wcar* (car/bgsave)) post-id) 
      (catch Exception e nil))))

(defn edit-post! [post-id post-title post-content]
  "Replaces the contents of post post-id with post-content, and the names as well. Returns true if successful, false
  otherwise. If post-id does not exist, it will not be created."
  (let [
    edit-date (time-coerce/to-long (time-local/local-now))
    hash-key (post-base-key post-id)
    key-exists (= 1 (wcar* (car/exists hash-key)))]
    (if key-exists
      (do 
        (wcar*
          (car/multi)
          (car/hset hash-key :post-title post-title)
          (car/hset hash-key :post-content post-content)
          (car/hset hash-key :post-edited true)
          (car/hset hash-key :post-edit-date edit-date))
        (try 
          (do (wcar* (car/exec)) (wcar* (car/bgsave)) true) 
          (catch Exception e false)))
      false)))

(defn delete-post! [post-id]
  "Delete a post from the database. Returns true if successful, false otherwise."
  (let [
    hash-key (post-base-key post-id)
    key-exists (= 1 (wcar* (car/exists hash-key)))]
    (if key-exists
      (do
        (wcar* 
          (car/multi)
          (car/lrem :post-list 0 post-id)
          (car/del (post-base-key post-id)))
        (try 
          (do (wcar* (car/exec)) (wcar* (car/bgsave)) true) 
          (catch Exception e false)))
      false)))


; Image-related API

; Tag-related API

; Links-related API

; Comments?
