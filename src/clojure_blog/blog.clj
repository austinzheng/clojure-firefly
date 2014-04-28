(ns clojure-blog.blog
  (:require
    [clojure-blog.session :as cbsession]
    [clojure-blog.database :as cbdb]
    [clojure-blog.template :as cbtemplate]
    [clojure-blog.auth :as cbauth]
    [clojure-blog.routes :as cbroutes]
    [clojure-blog.util :as cbutil]
    [clojure-blog.settings :as cbsettings]
    [ring.middleware.flash :as flash]))

;; Utility functions
(defn- next-route [start number total]
  (let [
    next-start (+ start number)
    next-number (min (- total next-start) cbsettings/posts-per-page)
    has-next (> total next-start)]
    (when has-next (cbroutes/blog-route next-start next-number))))

(defn- prev-route [start number total]
  (let [
    prev-start (max 0 (- start cbsettings/posts-per-page))
    prev-number (min start cbsettings/posts-per-page)
    has-prev (> start 0)]
    (when has-prev (cbroutes/blog-route prev-start prev-number))))

(defn- nav-description [start number]
  (if (= 1 number)
    (reduce str ["Currently showing post " (+ 1 start)])
    (reduce str ["Currently showing posts " (+ 1 start) " through " (+ start number)])))


;; 'action-' functions handle processing/state mutation related to an action.

(defn action-create-post! [session params]
  "Create a post and save it to the database"
  (let [
    return-url (:return-url session "/")
    post-title (:post-title params "")
    post-content (:post-content params "")
    can-create (and (cbutil/nonempty? post-title) (cbutil/nonempty? post-content))
    post-id (when can-create (cbdb/add-post! post-title post-content))
    flash-msg (if post-id "Post created." "Couldn't create post!")]
    (cbsession/redirect session flash-msg return-url)))

(defn action-edit-post! [session params]
  "Edit an existing post"
  (let [
    new-title (:post-title params nil)
    new-content (:post-content params nil)
    post-id (:post-id params nil)
    can-edit (and 
      (cbutil/nonempty? new-title) 
      (cbutil/nonempty? new-content) 
      (cbutil/nonempty? post-id))
    success (when can-edit (cbdb/edit-post! post-id new-title new-content))
    flash-msg (if success "Post edited." "Couldn't edit post!")]
    (cbsession/redirect session flash-msg (:return-url session "/"))))

(defn action-create-preview [session params]
  (let [
    session-info (cbauth/make-session-info session)
    title (:post-title params)
    content (:post-content params nil)
    post-id (:post-id params nil)
    can-submit (and (cbutil/nonempty? title) (cbutil/nonempty? content))
    response-body (cbtemplate/post-preview session-info nil can-submit post-id title content)]
    {
      :body response-body
      :session session
    }))

(defn action-resume-editing [session params]
  (let [
    session-info (cbauth/make-session-info session)
    post-id (:post-id params nil)
    show-delete (not (nil? post-id))
    title (:post-title params)
    content (:post-content params)
    response-body (cbtemplate/post-compose session-info nil post-id show-delete title content)]
    {
      :body response-body
      :session session  
    }))

(defn action-delete-post! [session post-id]
  (let [
    success (cbdb/delete-post! post-id)
    flash-msg (if success "Post deleted." "Error deleting post.")
    return-url "/blog"]
    (cbsession/redirect session flash-msg return-url)))


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
    :else (cbsession/redirect session "An error occurred" (:return-url session "/"))))


;; 'format-' functions generate HTML for a given blog page.

(defn format-composer-edit [session-info flash-msg post-id post-map] 
  (reduce str (cbtemplate/build-composer session-info flash-msg post-id post-map)))

(defn format-composer-new [session-info flash-msg]
  (reduce str (cbtemplate/build-composer session-info flash-msg nil nil)))

(defn format-post [session-info flash-msg post-id post-map]
  "Given raw data from the database, generate the HTML for a single post"
  (reduce str (cbtemplate/post-page session-info flash-msg post-id post-map)))

(defn format-blog [session-info flash-msg post-id-list post-map-list start number total]
  "Given raw data from the database, generate the HTML for a page of posts"
  (let [
    desc (nav-description start number)
    prev-url (prev-route start number total)
    next-url (next-route start number total)]
    (reduce str (cbtemplate/blog-page session-info flash-msg post-id-list post-map-list prev-url "Newer" desc next-url "Older"))))


;; 'get-' functions provide an interface for getting the :body of a response to a GET request.

(defn get-post-composer [session-info flash-msg post-id]
  (if post-id 
    (let [post-map (cbdb/get-post post-id)]
      (if post-map
        (format-composer-edit session-info flash-msg post-id post-map)
        (cbtemplate/error-page session-info flash-msg "Cannot edit post. Invalid post ID." nil nil)))
    (format-composer-new session-info flash-msg)))

(defn get-post [session-info flash-msg post-id] 
  "Given a raw ID, retrieve a post from the database"
  (let [post-map (cbdb/get-post post-id)]
    (if post-map
      (format-post session-info flash-msg post-id post-map)
      (cbtemplate/error-page session-info flash-msg "Could not retrieve post." nil nil))))

(defn get-posts [session-info flash-msg raw-start raw-number]
  "Given a start index and a number of posts, return as many posts as possible"
  (let [
    start (cbutil/parse-integer raw-start)
    n (cbutil/parse-integer raw-number)
    [post-map-list {:keys [id-seq post-count]}] (when (and start n) (cbdb/get-posts start n))]
    (if post-map-list
      (format-blog session-info flash-msg id-seq post-map-list start (count post-map-list) post-count)
      (cbtemplate/error-page session-info flash-msg "Could not retrieve the requested posts." nil nil))))
