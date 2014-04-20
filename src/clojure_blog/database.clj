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

(def ^{:private true} to-delete "__TO_DELETE__")

; Helpers
(defn- post-base-key [post-id] 
	(apply str ["post:" post-id]))

(defn- get-post-fn [post-id]
	(fn [] (wcar* (car/parse-map (car/hgetall (post-base-key post-id))))))

; Post-related API
(defn get-post [post-id]
	"Get a blog post from redis based on the post ID, or nil if it doesn't exist." 
	(let [
		raw-map (try 
			(wcar* (car/parse-map (car/hgetall (post-base-key post-id)))) (catch Exception e nil))]
		(if raw-map (cbutil/map-function-on-map-keys raw-map keyword) nil)))

(defn get-posts [start num-posts]
	"Get a number of blog posts from redis, with a start index and a count. If either the start index or count are
	invalid, returns nil."
	(let [
		post-count (wcar* (car/llen :post-list))
		adj-num-posts (min num-posts (post-count - start))
		can-retrieve (and (> start 0) (< start post-count))]
		(if can-retrieve 
			(let [
				id-vector (wcar* (car/lrange :post-list start (+ start adj-num-posts)))
				commands (map get-post-fn id-vector)] 
				(try
					(wcar* commands)
					(catch Exception e nil))) 
			nil)))

(defn add-post! [post-title post-content]
	"Add a new post to the database; returns the post ID, or nil if failed."
	(let [
		post-id (wcar* (car/incr :post-next-id))
		post-date (time-coerce/to-long (time-local/local-now))
		hash-key (post-base-key post-id)]
		(wcar* (car/multi))
		(wcar* (car/lpush :post-list post-id))
		(wcar* 
			(car/hmset* 
				hash-key
				{
					:post-title post-title,
					:post-date post-date, 
					:post-content post-content, 
					:post-edited false, 
					:post-edit-date nil}))
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
				(wcar* (car/hset hash-key :post-title post-title))
				(wcar* (car/hset hash-key :post-content post-content))
				(wcar* (car/hset hash-key :post-edited true))
				(wcar* (car/hset hash-key :post-edit-date edit-date))
				true)
			false)))

(defn delete-post! [post-id]
	"Delete a post from the database. Returns true if successful, false otherwise."
	(let [
		hash-key (post-base-key post-id)
		key-exists (= 1 (wcar* (car/exists hash-key)))]
		(if key-exists
			(do
				(wcar* (car/multi))
				(wcar* (car/lrem :post-list 0 post-id))
				(wcar* (car/del (post-base-key post-id)))
				(try 
					(do (wcar* (car/exec)) (wcar* (car/bgsave)) true) 
					(catch Exception e false)))
			false)))


; Comment-related API

; Tag-related API

; Links-related API
