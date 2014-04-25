(ns clojure-blog.handler
  (:use compojure.core)
  (:require 
    [clojure-blog.blog :as cbblog]
    [clojure-blog.auth :as cbauth]
    [clojure-blog.template :as cbtemplate]
    [clojure-blog.settings :as cbsettings]
    [compojure.handler :as handler]
    [compojure.route :as route]
    [ring.middleware.session :as session]
    [ring.util.response :as response]
    [ring.adapter.jetty :as jetty]))

; NOTE: http://clojuredocs.org/ring/ring.util.response/redirect

; TODO: these should be variable arity
(defn response-403
  [session message back-link back-msg]
  (let [
    session-info (cbauth/make-session-info session)
    error-msg (if message message "You must first log in.")]
    {:status 403
      :session session
      :body (cbtemplate/error-page session-info error-msg back-link back-msg)}))

(defn response-404 
  [session back-link back-msg]
  (let [
    session-info (cbauth/make-session-info session)
    error-msg "404: The requested resource couldn't be found."]
    {:status 404
      :session session
      :body (cbtemplate/error-page session-info error-msg back-link back-msg)}))
  

; TEMP --------
; Return a function that itself returns a pre-determined random number
(defn get-randn-fn []
  (let [rand-number (rand 1)]
    (fn [] (str rand-number))))

; /TEMP -------

; App routes
(defroutes app-routes

  (GET "/" 
    {session :session}
    (let [c-session (if session session {})]
      (assoc (response/redirect (apply str ["/blog/0/" cbsettings/posts-per-page])) :session session)))

;; TEST CODE
  (GET ["/test2/:id" :id #"[0-9]+"] 
    {session :session 
     params :params} 
    {:body (apply str [(:test session) " - session, id:" (:id params)]) 
     :session {:test "hello"}})

  (GET "/test3" {session :session, params :params}
    {:body (if (contains? session :next) (apply (:next session) []) (apply str ["No session yet"]))
     :session {:next (apply get-randn-fn [])}}
    )
;; END TEST CODE

  ; Blog
  (GET "/blog"
    {session :session, params :params}
    (let [session-info (cbauth/make-session-info session)]
      {:body (cbblog/get-posts session-info 0 cbsettings/posts-per-page)
        :session session}))

  (GET "/blog/archive" 
    {session :session, params :params}
    "hullo")

  (GET ["/blog/:start/:count" :start #"[0-9]+" :count #"[0-9]+"]
    {session :session, params :params}
    (let [session-info (cbauth/make-session-info session)]
      {:body (cbblog/get-posts session-info (:start params 0) (:count params 10))
        :session session}))

  (GET ["/post/:id" :id #"[0-9]+"]
    {session :session, params :params}
    (let [session-info (cbauth/make-session-info session)]
      {:body (cbblog/get-post session-info (:id params nil))
        :session session}))

  ; Admin panel
  (POST "/admin/login"
    {session :session, params :params}
    (if (cbauth/credentials-valid? params)
      ;; TODO: Redirect to the previous page.
      (assoc (response/redirect "/") :session (cbauth/add-admin-session session))
      ; TODO: Fix this
      "Invalid credentials, try again"))

  (GET "/admin/logout" 
    {session :session, params :params}
    (if (cbauth/admin? session)
      ; TODO: Fix this
      {:body "Logged out", 
       :session (dissoc session :admin-session)}
      {:body "Not logged in!",
       :session session}))

  (GET ["/admin/edit/post/:id" :id #"[0-9]+"]
    {session :session, params :params}
    (let [
      session-info (cbauth/make-session-info session)
      authorized (cbauth/admin? session)]
      (if authorized
        {:body (cbblog/get-post-composer session-info (:id params nil))
          :session session}
        (response-403 session "You must first log in to edit posts." nil nil))))

  (GET "/admin/new/post" 
    {session :session, params :params}
    (let [
      session-info (cbauth/make-session-info session)
      authorized (cbauth/admin? session)]
      (if authorized
        {:body (cbblog/get-post-composer session-info nil)
          :session session}
        (response-403 session "You must first log in to compose new posts." nil nil))))

  (GET ["/admin/delete/post/:id" :id #"[0-9]+"]
    {session :session, params :params} 
    (if (cbauth/admin? session) 
      {:body (cbblog/post-delete! session params)
        :session session} 
      (response-403 session "You must first log in to delete posts." nil nil)))

  (POST "/admin/submit/post"
    {session :session, params :params}
    (if (cbauth/admin? session) 
      {:body (cbblog/post-npsubmit! session params)
        :session session}
      (response-403 session nil nil nil)))

  (route/resources "/")
  (route/not-found
    (response-404 nil nil nil)))

(def app
  ; Note: since we wrap in handler/site we shouldn't need to manually add session middleware
  (handler/site app-routes))
