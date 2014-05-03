(ns clojure-blog.handler
  (:use compojure.core)
  (:require 
    [clojure-blog.blog :as blog]
    [clojure-blog.session :as ss]
    [clojure-blog.auth :as auth]
    [clojure-blog.template :as template]
    [clojure-blog.routes :as r]
    [compojure.handler :as handler]
    [compojure.route :as route]
    [ring.middleware.session :as session]
    [ring.adapter.jetty :as jetty]))

;; SHARED PAGES
(defn- response-403
  [session flash & {:keys [message back-link back-msg]}]
  (let [
    flash-msg flash
    session-info (auth/make-session-info session)
    error-msg (if message message "You must first log in.")]
    {
      :status 403
      :session session
      :body (template/error-page session-info flash-msg error-msg back-link back-msg)
    }))

(defn- response-404 
  [session flash & {:keys [back-link back-msg]}]
  (let [
    flash-msg flash
    session-info (auth/make-session-info session)
    error-msg "404: The requested resource couldn't be found."]
    {
      :status 404
      :session session
      :body (template/error-page session-info flash-msg error-msg back-link back-msg)
    }))

(defn- admin-page
  [session flash]
  (let [
    flash-msg flash
    session-info (auth/make-session-info session)
    is-first-time (auth/first-time?)
    authorized (or is-first-time (auth/admin? session))]
    (if authorized
      {
        :session session
        :body (template/admin-page session-info flash-msg is-first-time)
      }
      (response-403 session flash :message "You must first log in to view the control panel."))))


;; APP ROUTES
(defroutes app-routes

  (GET "/" 
    {session :session, flash :flash}
    (ss/redirect 
      (ss/session-with-return-url session r/home-route) 
      flash
      r/home-route))

  ;; BLOG
  (GET "/blog/" {session :session, flash :flash} (ss/redirect session flash "/blog"))
  (GET "/blog"
    {session :session, flash :flash}
    (ss/redirect 
      (ss/session-with-return-url session r/home-route) 
      flash 
      r/home-route))

  (GET "/blog/archive/" {session :session, flash :flash} (ss/redirect session flash "/blog/archive"))
  (GET "/blog/archive" 
    {session :session, params :params, flash :flash, uri :uri}
    (let [session-info (auth/make-session-info session)]
      {
        :body (blog/get-archive session-info flash)
        :session (ss/session-with-return-url session uri)
      }))

  (GET ["/blog/:start/:count/" :start #"[0-9]+" :count #"[0-9]+"] {session :session, flash :flash, params :params}
    (ss/redirect session flash (apply str ["/blog/" (:start params) "/" (:count params)])))
  (GET ["/blog/:start/:count" :start #"[0-9]+" :count #"[0-9]+"]
    {session :session, params :params, flash :flash, uri :uri}
    (let [session-info (auth/make-session-info session)]
      {
        :body (blog/get-posts session-info flash (:start params 0) (:count params 10))
        :session (ss/session-with-return-url session uri)
      }))

  (GET ["/post/:id/" :id #"[0-9]+"] {session :session, flash :flash, params :params}
    (ss/redirect session flash (apply str ["/post/" (:id params)])))
  (GET ["/post/:id" :id #"[0-9]+"]
    {session :session, params :params, flash :flash, uri :uri}
    (let [session-info (auth/make-session-info session)]
      {
        :body (blog/get-post session-info flash (:id params nil))
        :session (ss/session-with-return-url session uri)
      }))

  (GET "/posts/tag/:tag/" {session :session, flash :flash, params :params}
    (ss/redirect session flash (apply str ["/post/tag/" (:tag params)])))
  (GET "/posts/tag/:tag"
    {session :session, params :params, flash :flash, uri :uri}
    (let [session-info (auth/make-session-info session)] 
      {
        :body (blog/get-posts-for-tag session-info flash (:tag params))
        :session (ss/session-with-return-url session uri)
      }))

  ;; ADMIN (GENERAL)
  (GET "/admin"
    {session :session, params :params, flash :flash, uri :uri}
    (admin-page session flash))

  (POST "/admin/first-time"
    {session :session, params :params, flash :flash, uri :uri}
    (auth/post-first-time session params))

  (POST "/admin/login"
    {session :session, params :params, flash :flash, uri :uri}
    (auth/post-login session params))

  (GET "/admin/logout" 
    {session :session, params :params, flash :flash, uri :uri}
    (auth/post-logout session params))

  ;; ADMIN (BLOG)
  (GET ["/admin/edit/post/:id" :id #"[0-9]+"]
    {session :session, params :params, flash :flash, uri :uri}
    (let [
      session-info (auth/make-session-info session)
      authorized (auth/admin? session)]
      (if authorized
        {
          :body (blog/get-post-composer session-info flash (:id params nil))
          :session session
        }
        (response-403 
          session
          flash
          :message "You must first log in to edit posts."))))

  (GET "/admin/new/post" 
    {session :session, params :params, flash :flash, uri :uri}
    (let [
      session-info (auth/make-session-info session)
      authorized (auth/admin? session)]
      (if authorized
        {
          :body (blog/get-post-composer session-info flash nil)
          :session session
        }
        (response-403
          session
          flash
          :message "You must first log in to compose new posts."))))

  (GET ["/admin/delete/post/:id" :id #"[0-9]+"]
    {session :session, params :params, flash :flash, uri :uri} 
    (if (auth/admin? session)
      (blog/post-delete! session params)
      (response-403 
        session 
        flash 
        :message "You must first log in to delete posts.")))

  (POST "/admin/submit/post"
    {session :session, params :params, flash :flash, uri :uri}
    (if (auth/admin? session) 
      (blog/post-npsubmit! session params)
      (response-403 session flash)))

  ;; Resources, must go before the catchall.
  (route/resources "/")

  ;; Catchall
  (GET "/*" {session :session, flash :flash} 
    (response-404 session flash))

  (route/not-found "???"))

(def app
  ; Note: since we wrap in handler/site we shouldn't need to manually add session middleware
  (handler/site app-routes))
