(ns clojure-blog.handler
  (:use compojure.core)
  (:require 
    [clojure-blog.blog :as blog]
    [clojure-blog.auth :as cbauth]
    [compojure.handler :as handler]
    [compojure.route :as route]
    [ring.middleware.session :as session]
    [ring.util.response :as response]
    [ring.adapter.jetty :as jetty]))

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

(defn make-session-info [session]
  "Build the 'session-info' map, which encapsulates session-specific info useful when rendering a response body"
  (let []
    {:logged-in (cbauth/admin? session),
      :username "Admin"}))

; App routes
(defroutes app-routes

  (GET "/" 
    {session :session}
    (let [c-session (if session session {})]
      (assoc (response/redirect (apply str ["/blog/0/" posts-per-page])) :session session)))

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
    (let [session-info (make-session-info session)]
      {:body (blog/get-posts session-info 0 posts-per-page)
        :session session}))

  (GET "/blog/archive" 
    {session :session, params :params}
    "hullo")

  (GET ["/blog/:start/:count" :start #"[0-9]+" :count #"[0-9]+"]
    {session :session, params :params}
    (let [session-info (make-session-info session)]
      {:body (blog/get-posts session-info (:start params 0) (:count params 10))
        :session session}))

  (GET ["/post/:id" :id #"[0-9]+"]
    {session :session, params :params}
    (let [session-info (make-session-info session)]
      {:body (blog/get-post session-info (:id params nil))
        :session session}))

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

  (GET ["/admin/edit/post/:id" :id #"[0-9]+"]
    {session :session, params :params}
    (let [
      session-info (make-session-info session)
      authorized (cbauth/admin? session)]
      (if authorized
        {:body (blog/get-post-composer session-info (:id params nil))
          :session session}
        (access-forbidden session))))

  (GET "/admin/new/post" 
    {session :session, params :params}
    (let [
      session-info (make-session-info session)
      authorized (cbauth/admin? session)]
      (if authorized
        {:body (blog/get-post-composer session-info nil)
          :session session}
        (access-forbidden session))))

  (GET ["/admin/delete/post/:id" :id #"[0-9]+"]
    {session :session, params :params} 
    (if (cbauth/admin? session) 
      {:body (blog/post-delete! session params), :session session} 
      (access-forbidden session)))

  (POST "/admin/submit/post"
    {session :session, params :params}
    (if (cbauth/admin? session) 
      {:body (blog/post-npsubmit! session params), :session session}
      (access-forbidden session)))

  (route/resources "/")
  (route/not-found "Not found!"))

(def app
  ; Note: since we wrap in handler/site we shouldn't need to manually add session middleware
  (handler/site app-routes))
