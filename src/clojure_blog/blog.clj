(ns clojure-blog.blog
  (:require
    [clojure-blog.database :as cbdb]
    [clojure-blog.template :as cbtemplate]
    [clojure-blog.util :as cbutil]
    [clj-time.format :as time-format]
    [clj-time.coerce :as time-coerce]))


;; 'action-' functions handle processing/state mutation related to an action.

(defn action-create-post! [session params]
  "Create a post and save it to the database"
  (let [post-id (cbdb/add-post! (:post-title params) (:post-content params))]
    (if post-id (apply str ["Post created. ID: " post-id]) "Couldn't create post!")))

(defn action-edit-post! [session params]
  "Edit an existing post"
  (let [
    new-title (:post-title params)
    new-content (:post-content params)
    post-id (:post-id params)]
    (if (cbdb/edit-post! post-id new-title new-content) "Post edited." "Couldn't edit post!")))

(defn action-create-preview [session params]
  ; Stuff to check the session
  ;; TODO
  (if params (apply str (concat ["Params: "] params)) "No params, for some reason"))

(defn action-delete-post! [session post-id]
  ; Stuff to check the session
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
    :else "Blog post not found"))


;; 'format-' functions generate HTML for a given blog page.

(defn format-composer-edit [session-info post-id title content]
  (reduce str 
    (cbtemplate/post-compose 
      {:should-show-delete true, 
        :post-id post-id, 
        :post-title title, 
        :post-content content} session-info)))

(defn format-composer-new [session-info]
  (reduce str (cbtemplate/post-compose {:should-show-delete false} session-info)))

(defn format-post [session-info title date content]
  "Given raw data from the database, generate the HTML for a single post"
  (let [
    date-object (let [date-long (cbutil/parse-integer date)] (if date-long (time-coerce/from-long date-long) nil))
    date-formatter (time-format/formatter "yyyy-MM-dd HH:mm")
    post-map {
      :title title, 
      :date (if date-object (time-format/unparse date-formatter date-object) "(unknown)"), 
      :content content}]
    (reduce str (cbtemplate/post-page post-map session-info))))

(defn format-blog [session-info post-maps]
  "Given raw data from the database, generate the HTML for a page of posts"
  (let [
    map-transform-fn (defn maptfn [raw-map]
      (let [
        date-object (let [date-long (cbutil/parse-integer (:post-date raw-map))] (if date-long (time-coerce/from-long date-long) nil))
        date-formatter (time-format/formatter "yyyy-MM-dd HH:mm")
        post-title (:post-title raw-map)
        post-content (:post-content raw-map)] 
        {
          :title post-title, 
          :date (if date-object (time-format/unparse date-formatter date-object) "(unknown)"),
          :content post-content}))
    ] 
    (reduce str (cbtemplate/blog-page (map map-transform-fn post-maps) session-info))))


;; 'get-' functions provide an interface for getting the :body of a response to a GET request.

(defn get-post-composer [session-info post-id]
  (if post-id 
    (let [post-data (cbdb/get-post post-id)]
      (if post-data
        (format-composer-edit session-info post-id (:post-title post-data) (:post-content post-data))
        "Bad post ID"))
    (format-composer-new session-info)))

(defn get-post [session-info post-id] 
  "Given a raw ID, retrieve a post from the database"
  (let [post-data (cbdb/get-post post-id)]
    (if post-data
      (format-post 
        session-info 
        (:post-title post-data) 
        (:post-date post-data)
        (:post-content post-data))
      "Couldn't find the specified post")))

(defn get-posts [session-info raw-start raw-post-count]
  "Given a start index and a number of posts, return as many posts as possible"
  (let [
    start (cbutil/parse-integer raw-start)
    post-count (cbutil/parse-integer raw-post-count)
    posts (if (and start post-count) (cbdb/get-posts start post-count) nil)]
    (if posts 
      (do (format-blog session-info posts))
      "Unable to retrieve any posts")))
