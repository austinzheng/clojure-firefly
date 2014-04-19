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


; Post-related helpers
(defn- post-base-key [post-id] (apply str ["post:" post-id]))

; Post-related API
(defn get-post
	"Get a blog post from redis based on the post ID, or nil if it doesn't exist." 
	[post-id]
	(let [raw-map (try (wcar* (car/parse-map (car/hgetall (post-base-key post-id)))) (catch Exception e nil))]
		(if raw-map (cbutil/map-function-on-map-keys raw-map keyword) nil)))

(defn get-posts
	; TODO
	"Get a number of blog posts from redis, with a start index and a count. If either the start index or count are invalid, returns nil."
	[start num-posts]
	nil)

(defn add-post!
	"Add a new post to the database; returns the post ID, or nil if failed."
	[post-title post-body]
	(let [post-id (wcar* (car/incr :post-next-id)) post-date (time-coerce/to-long (time-local/local-now))]
		(wcar* 
			(car/multi))
		(wcar* 
			(car/lpush :post-list post-id))
		(wcar* 
			(car/hmset* 
				(post-base-key post-id) 
				{
					:post-title post-title,
					:post-date post-date, 
					:post-body post-body, 
					:post-edited false, 
					:post-edit-date nil}))
		(try 
			(do (wcar* (car/exec)) (wcar* (car/bgsave)) post-id) 
			(catch Exception e nil))))

(defn edit-post!
	; TODO
	"Replaces the contents of post post-id with post-body, and the names as well. Returns true if successful, false otherwise. If post-id does not exist, it will not be created."
	[post-id post-title post-body]
	nil)

(defn delete-post!
	; TODO
	"Delete a post from the database. Returns true if successful, false otherwise."
	[post-id]
	nil)

; Comment-related API

; Tag-related API

; Links-related API
