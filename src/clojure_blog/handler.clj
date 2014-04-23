(ns clojure-blog.handler
  (:use compojure.core)
  (:require 
    [clojure-blog.util :as cbutil]
    [clojure-blog.database :as cbdb]
    [clojure-blog.auth :as cbauth]
    [clojure-blog.template :as cbtemplate]
    [compojure.handler :as handler]
    [compojure.route :as route]
    [ring.middleware.session :as session]
    [ring.util.response :as response]
    [ring.adapter.jetty :as jetty]
    [clj-time.format :as time-format]
    [clj-time.coerce :as time-coerce]))

; NOTE: http://clojuredocs.org/ring/ring.util.response/redirect

;; Config
(def posts-per-page 10)

;; TEMP
(defn not-found [] "404: not found")
(defn access-forbidden [session] 
  {:body "Please log in first.", :session session})

; TEMP --------
; Return a function that itself returns a pre-determined random number
(defn get-randn-fn []
  (let [rand-number (rand 1)]
    (fn [] (str rand-number))))

; /TEMP -------

(defn make-nav-map [session] 
  {:logged-in (cbauth/admin? session), :username "Admin", :login-route "/admin/login", :logout-route "/admin/logout"})

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
  (if params (apply str (concat ["Params: "] params)) "No params, for some reason"))

(defn action-delete-post! [session post-id]
  ; Stuff to check the session
  (if (cbdb/delete-post! post-id) "Post deleted" "Error deleting post"))

(defn handle-delete [session params]
  (action-delete-post! session (:id params)))

(defn handle-npsubmit [session params]
  "Handle the user pressing the 'preview', 'delete', or 'submit' buttons on the compose page"
  (cond 
    (contains? params :add-post) (action-create-post! session params)
    (contains? params :edit-post) (action-edit-post! session params)
    (contains? params :preview) (action-create-preview session params)
    (contains? params :delete) (action-delete-post! session (:post-id params)) 
    :else (not-found)))

(defn generate-edit-post-composer [session post-id title content]
  (reduce str (cbtemplate/post-compose {:should-show-delete true, :post-id post-id, :post-title title, :post-content content} (make-nav-map session))))

(defn generate-new-post-composer [session]
  (reduce str (cbtemplate/post-compose {:should-show-delete false} (make-nav-map session))))

(defn generate-post [session title date content]
  "Given raw data from the database, generate the HTML for a single post"
  (let [
    date-object (let [date-long (cbutil/parse-integer date)] (if date-long (time-coerce/from-long date-long) nil))
    date-formatter (time-format/formatter "yyyy-MM-dd HH:mm")
    post-map {
      :title title, 
      :date (if date-object (time-format/unparse date-formatter date-object) "(unknown)"), 
      :content content}]
    (reduce str (cbtemplate/post-page post-map (make-nav-map session)))))

(defn generate-blog [session post-maps]
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
    (reduce str (cbtemplate/blog-page (map map-transform-fn post-maps) (make-nav-map session)))))

(defn get-post-composer [session post-id]
  (if post-id 
    (let [post-data (cbdb/get-post post-id)]
      (if post-data
        (generate-edit-post-composer session post-id (:post-title post-data) (:post-content post-data))
        "Bad post ID"))
    (generate-new-post-composer session)))

(defn get-post [session post-id] 
  "Given a raw ID, retrieve a post from the database"
  (let [post-data (cbdb/get-post post-id)]
    (if post-data
      (generate-post session (:post-title post-data) (:post-date post-data) (:post-content post-data))
      "Couldn't find the specified post")))

(defn get-posts [session raw-start raw-post-count]
  "Given a start index and a number of posts, return as many posts as possible"
  (let [
    start (cbutil/parse-integer raw-start)
    post-count (cbutil/parse-integer raw-post-count)
    posts (if (and start post-count) (cbdb/get-posts start post-count) nil)]
    (if posts 
      (do (generate-blog session posts))
      "Unable to retrieve any posts")))

; App routes
(defroutes app-routes

  (GET "/" 
    {session :session}
    (let [c-session (if session session {})]
      (assoc (response/redirect (apply str ["/blog/0/" posts-per-page])) :session session)))

  (GET ["/test2/:id" :id #"[0-9]+"] 
    {session :session 
     params :params} 
    {:body (apply str [(:test session) " - session, id:" (:id params)]) 
     :session {:test "hello"}})

  (GET "/test3" {session :session, params :params}
    {:body (if (contains? session :next) (apply (:next session) []) (apply str ["No session yet"]))
     :session {:next (apply get-randn-fn [])}}
    )

;; TEST CODE
  (GET "/setsession" 
    {session :session, params :params}
    (if (cbauth/admin? session) 
      {:body "Already have session", 
       :session session}
      {:body "Created new session", 
       :session (cbauth/add-admin-session nil)}))

  ; Blog
  (GET "/blog"
    {session :session, params :params}
    {:body (get-posts session 0 posts-per-page), :session session})

  (GET "/blog/:start/:count" 
    {session :session, params :params}
    {:body (get-posts session (:start params) (:count params)) :session session})

  (GET "/post/:id"
    {session :session, params :params}
    {:body (get-post session (:id params)) :session session})

  ; Admin panel
  (POST "/admin/login"
    {session :session, params :params}
    (if (cbauth/credentials-valid? params)
      (assoc (response/redirect "/") :session (cbauth/add-admin-session session))
      "Invalid credentials, try again"))

  (GET "/admin/logout" 
    {session :session, params :params}
    (if (cbauth/admin? session)
      {:body "Logged out", 
       :session (dissoc session :admin-session)}
      {:body "Not logged in!",
       :session session}))

  (GET "/admin/edit/post/:id" 
    {session :session, params :params} 
    (if (cbauth/admin? session) 
      {:body (get-post-composer session (:id params)), :session session}
      (access-forbidden session)))

  (GET "/admin/new/post" 
    {session :session, params :params}
    (if (cbauth/admin? session) 
      {:body (get-post-composer session nil), :session session}
      (access-forbidden session)))

  (GET "/admin/delete/post/:id" 
    {session :session, params :params} 
    (if (cbauth/admin? session) 
      {:body (handle-delete session params), :session session} 
      (access-forbidden session)))

  (POST "/admin/submit/post"
    {session :session, params :params}
    (if (cbauth/admin? session) 
      {:body (handle-npsubmit session params), :session session}
      (access-forbidden session)))

  (route/resources "/")
  (route/not-found "Not found!"))

(def app
  ; Note: since we wrap in handler/site we shouldn't need to manually add session middleware
  (handler/site app-routes))
