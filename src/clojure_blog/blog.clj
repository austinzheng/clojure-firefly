;; blog.clj (clojure-firefly)
;; Copyright (c) 2014 Austin Zheng
;; Released under the terms of the MIT License

(ns clojure-blog.blog
  (:require
    [clojure-blog.tags :as tags]
    [clojure-blog.session :as ss]
    [clojure-blog.blog-database :as db]
    [clojure-blog.blog-template :as bt]
    [clojure-blog.template :as t]
    [clojure-blog.auth :as auth]
    [clojure-blog.routes :as r]
    [clojure-blog.util :as util]
    [clojure-blog.settings :as settings]
    [ring.middleware.flash :as flash]))

;; Utility functions
(defn- next-route [start number total]
  (let [
    next-start (+ start number)
    next-number (min (- total next-start) settings/posts-per-page)
    has-next (> total next-start)]
    (when has-next (r/blog-route next-start next-number))))

(defn- prev-route [start number total]
  (let [
    prev-start (max 0 (- start settings/posts-per-page))
    prev-number (min start settings/posts-per-page)
    has-prev (> start 0)]
    (when has-prev (r/blog-route prev-start prev-number))))

(defn- nav-description [start number]
  (if (= 1 number)
    (reduce str ["Currently showing post " (+ 1 start)])
    (reduce str ["Currently showing posts " (+ 1 start) " through " (+ start number)])))

(defn- tag-description [tag]
  (reduce str ["Currently showing posts for tag '" tag "'"]))


;; 'action-' functions handle processing/state mutation related to an action.

(defn action-create-post! [session params]
  "Create a post and save it to the database"
  (let [
    return-url (:return-url session "/")
    post-title (:post-title params "")
    post-content (:post-content params "")
    raw-post-tags (:post-tags params nil)
    post-tags (when raw-post-tags (tags/split-raw-tags raw-post-tags))
    can-create (and (seq post-title) (seq post-content))
    post-id (when can-create (db/add-post! post-title post-content post-tags))
    flash-msg (if post-id "Post created." "Couldn't create post!")]
    (ss/redirect session flash-msg return-url)))

(defn action-edit-post! [session params]
  "Edit an existing post"
  (let [
    new-title (:post-title params nil)
    new-content (:post-content params nil)
    post-id (:post-id params nil)
    raw-old-tags (:post-old-tags params nil)
    raw-new-tags (:post-tags params nil)
    old-tags (when raw-old-tags (tags/split-raw-tags raw-old-tags))
    new-tags (when raw-new-tags (tags/split-raw-tags raw-new-tags))
    can-edit (and 
      (seq new-title) 
      (seq new-content)
      (seq post-id))
    success (when can-edit (db/edit-post! post-id new-title new-content new-tags old-tags))
    flash-msg (if success "Post edited." "Couldn't edit post!")]
    (ss/redirect session flash-msg (:return-url session "/"))))

(defn action-create-preview [session params]
  (let [
    session-info (auth/make-session-info session)
    title (:post-title params)
    content (:post-content params nil)
    post-id (:post-id params nil)
    tags (:post-tags params nil)
    old-tags (:post-old-tags params nil)
    can-submit (and (seq title) (seq content))
    response-body (bt/post-preview session-info nil can-submit post-id title content tags old-tags)]
    {
      :body response-body
      :session session
    }))

(defn action-resume-editing [session params]
  (let [
    session-info (auth/make-session-info session)
    post-id (:post-id params nil)
    show-delete (not (nil? post-id))
    title (:post-title params)
    content (:post-content params)
    tags (:post-tags params nil)
    old-tags (:post-old-tags params nil)
    response-body (bt/post-compose session-info nil post-id show-delete title content tags old-tags)]
    {
      :body response-body
      :session session  
    }))

(defn action-delete-post! [session post-id]
  (let [
    success (db/delete-post! post-id)
    flash-msg (if success "Post deleted." "Error deleting post.")
    return-url "/blog"]
    (ss/redirect session flash-msg return-url)))


;; 'post-' functions generate the :body of the response to a POST request.

; Optimally, delete should be a POST instead of a GET, w/o using js.
(defn post-delete! [session params]
  "Delete a post and return the entire response map"
  (action-delete-post! session (:id params)))

(defn post-npsubmit! [session params]
  "Handle the user pressing the 'preview', 'delete', or 'submit' buttons on the compose page"
  (cond 
    (contains? params :add-post) (action-create-post! session params)
    (contains? params :edit-post) (action-edit-post! session params)
    (contains? params :preview) (action-create-preview session params)
    (contains? params :continue-editing) (action-resume-editing session params)
    (contains? params :delete) (action-delete-post! session (:post-id params)) 
    :else (ss/redirect session "An error occurred" (:return-url session "/"))))


;; 'format-' functions generate HTML for a given blog page.

(defn format-composer-edit [session-info flash-msg post-id post-map]
  (reduce str (bt/build-composer session-info flash-msg post-id post-map)))

(defn format-composer-new [session-info flash-msg]
  (reduce str (bt/build-composer session-info flash-msg nil nil)))

(defn format-post [session-info flash-msg post-id post-map]
  "Given raw data from the database, generate the HTML for a single post"
  (reduce str (bt/post-page session-info flash-msg post-id post-map)))

(defn format-blog [session-info flash-msg post-id-list post-map-list start number total]
  "Given raw data from the database, generate the HTML for a page of posts"
  (let [
    c-total (if (nil? total) 0 total)
    desc (when (> c-total 0) (nav-description start number))
    prev-url (when (> c-total 0) (prev-route start number total))
    next-url (when (> c-total 0) (next-route start number total))
    no-posts-msg "(No posts)"]
    (reduce str
      (bt/blog-page
        session-info
        flash-msg
        post-id-list
        post-map-list
        no-posts-msg
        prev-url
        "Newer"
        desc
        next-url
        "Older"))))

(defn format-blog-for-tag [session-info flash-msg post-id-list post-map-list tag]
  "Given raw data from the database, generate the HTML for a page of posts"
  (let [
    desc (tag-description tag)]
    (reduce str 
      (bt/blog-page
        session-info
        flash-msg
        post-id-list
        post-map-list
        "(No posts)"
        nil
        nil
        desc
        nil
        nil))))

(defn format-archive [session-info flash-msg metadata-list]
  "Given raw data from the database, generate the HTML for the posts archive page"
  (reduce str (bt/archive-page session-info flash-msg metadata-list "(No posts)")))


;; 'get-' functions provide an interface for getting the :body of a response to a GET request.

(defn get-post-composer [session-info flash-msg post-id]
  (if post-id 
    (let [post-map (db/get-post post-id)]
      (if post-map
        (format-composer-edit session-info flash-msg post-id post-map)
        (t/error-page session-info flash-msg "Cannot edit post. Invalid post ID." nil nil)))
    (format-composer-new session-info flash-msg)))

(defn get-post [session-info flash-msg post-id] 
  "Given a raw ID, retrieve a post from the database"
  (let [post-map (db/get-post post-id)]
    (if post-map
      (format-post session-info flash-msg post-id post-map)
      (t/error-page session-info flash-msg "Could not retrieve post." nil nil))))

(defn get-posts [session-info flash-msg raw-start raw-number]
  "Given a start index and a number of posts, return as many posts as possible"
  (let [
    start (util/parse-integer raw-start)
    n (util/parse-integer raw-number)
    no-posts (= 0 (db/total-post-count))
    [post-map-list {:keys [id-seq post-count]}] (when (and start n) (db/get-posts start n))
    final-count (if no-posts 0 post-count)]
    (if (or no-posts post-map-list) 
      (format-blog session-info flash-msg id-seq post-map-list start (count post-map-list) final-count)
      (t/error-page session-info flash-msg "Could not retrieve the requested posts." nil nil))))

(defn get-posts-for-tag [session-info flash-msg tag]
  "Given a tag, return the posts for that tag or an error."
  (let [
    valid (tags/tag-valid? tag)
    [post-map-list {:keys [id-seq]}] (when valid (db/get-posts-for-tag tag))
    error-msg (reduce str ["Could not retrieve posts for the tag '" tag "'"])]
    (if post-map-list
      (format-blog-for-tag session-info flash-msg id-seq post-map-list tag)
      (t/error-page session-info flash-msg error-msg nil nil))))

(defn get-archive [session-info flash-msg]
  "Return the post archive."
  (let [
    metadata-list (db/get-all-metadata)
    error-msg "Could not retrieve blog archive"]
    (if metadata-list
      (format-archive session-info flash-msg metadata-list)
      (t/error-page session-info flash-msg error-msg nil nil))))
