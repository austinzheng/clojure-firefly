(ns clojure-blog.blog
  (:require
    [clojure-blog.database :as cbdb]
    [clojure-blog.template :as cbtemplate]
    [clojure-blog.auth :as cbauth]
    [clojure-blog.util :as cbutil]))


;; 'action-' functions handle processing/state mutation related to an action.

(defn action-create-post! [session params]
  "Create a post and save it to the database"
  (let [
    post-title (:post-title params "")
    post-content (:post-content params "")
    can-create (and (cbutil/nonempty? post-title) (cbutil/nonempty? post-content))
    post-id (if can-create (cbdb/add-post! post-title post-content) nil)]
    ;; TODO: Should go back to wherever
    (if post-id (apply str ["Post created. ID: " post-id]) "Couldn't create post!")))

(defn action-edit-post! [session params]
  "Edit an existing post"
  (let [
    new-title (:post-title params "")
    new-content (:post-content params "")
    post-id (:post-id params nil)
    can-edit (and 
      (cbutil/nonempty? new-title) 
      (cbutil/nonempty? new-content) 
      (cbutil/nonempty? post-id))
    edit-result (if can-edit (cbdb/edit-post! post-id new-title new-content) nil)]
    ;; TODO: should go back to wherever
    (if edit-result "Post edited." "Couldn't edit post!")))

(defn action-create-preview [session params]
  ; Stuff to check the session
  ;; TODO
  (if params (apply str (concat ["Params: "] params)) "No params, for some reason"))

(defn action-delete-post! [session post-id]
  ; Stuff to check the session
  ;; TODO: SHould go back to wherever
  (if (cbdb/delete-post! post-id) "Post deleted" "Error deleting post"))


;; 'post-' functions generate the :body of the response to a POST request.

; TODO: See if delete can be a POST instead of a GET, w/o using js.
(defn post-delete! [session params]
  (action-delete-post! session (:id params)))

(defn post-npsubmit! [session params]
  "Handle the user pressing the 'preview', 'delete', or 'submit' buttons on the compose page"
  (cond 
    (contains? params :add-post) (action-create-post! session params)
    (contains? params :edit-post) (action-edit-post! session params)
    (contains? params :preview) (action-create-preview session params)
    (contains? params :delete) (action-delete-post! session (:post-id params)) 
    :else (cbtemplate/error-page (cbauth/make-session-info session) "An error occurred." nil nil)))


;; 'format-' functions generate HTML for a given blog page.

(defn format-composer-edit [session-info post-id post-map] 
  (reduce str (cbtemplate/build-composer session-info post-id post-map)))

(defn format-composer-new [session-info]
  (reduce str (cbtemplate/build-composer session-info nil nil)))

(defn format-post [session-info post-id post-map]
  "Given raw data from the database, generate the HTML for a single post"
  (reduce str (cbtemplate/post-page session-info post-id post-map)))

(defn format-blog [session-info post-id-list post-map-list]
  "Given raw data from the database, generate the HTML for a page of posts"
  (reduce str (cbtemplate/blog-page session-info post-id-list post-map-list)))


;; 'get-' functions provide an interface for getting the :body of a response to a GET request.

(defn get-post-composer [session-info post-id]
  (if post-id 
    (let [post-map (cbdb/get-post post-id)]
      (if post-map
        (format-composer-edit session-info post-id post-map)
        (cbtemplate/error-page session-info "Cannot edit post. Invalid post ID." nil nil)))
    (format-composer-new session-info)))

(defn get-post [session-info post-id] 
  "Given a raw ID, retrieve a post from the database"
  (let [post-map (cbdb/get-post post-id)]
    (if post-map
      (format-post session-info post-id post-map)
      (cbtemplate/error-page session-info "Could not retrieve post." nil nil))))

(defn get-posts [session-info raw-start raw-post-count]
  "Given a start index and a number of posts, return as many posts as possible"
  (let [
    start (cbutil/parse-integer raw-start)
    post-count (cbutil/parse-integer raw-post-count)
    [post-id-list post-map-list] (if (and start post-count) (cbdb/get-posts start post-count) nil)]
    (if post-map-list
      (format-blog session-info post-id-list post-map-list)
      (cbtemplate/error-page session-info "Could not retrieve the requested posts." nil nil))))
