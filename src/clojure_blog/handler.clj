(ns clojure-blog.handler
  (:use compojure.core)
  (:require 
    [clojure-blog.blog :as cbblog]
    [clojure-blog.session :as cbsession]
    [clojure-blog.auth :as cbauth]
    [clojure-blog.template :as cbtemplate]
    [clojure-blog.settings :as cbsettings]
    [compojure.handler :as handler]
    [compojure.route :as route]
    [ring.middleware.session :as session]
    [ring.adapter.jetty :as jetty]))

;; UTILITY GENERATORS
(def home-route (apply str ["/blog/0/" cbsettings/posts-per-page]))

(defn- response-403
  [session flash & {:keys [message back-link back-msg]}]
  (let [
    flash-msg flash
    session-info (cbauth/make-session-info session)
    error-msg (if message message "You must first log in.")]
    {
      :status 403
      :session session
      :body (cbtemplate/error-page session-info flash-msg error-msg back-link back-msg)
    }))

(defn- response-404 
  [session flash & {:keys [back-link back-msg]}]
  (let [
    flash-msg flash
    session-info (cbauth/make-session-info session)
    error-msg "404: The requested resource couldn't be found."]
    {
      :status 404
      :session session
      :body (cbtemplate/error-page session-info flash-msg error-msg back-link back-msg)
    }))


;; APP ROUTES
(defroutes app-routes

  (GET "/" 
    {session :session, flash :flash}
    (cbsession/redirect 
      (cbsession/session-with-return-url session home-route) 
      flash
      home-route))

  ;; BLOG
  (GET "/blog/" {session :session, flash :flash} (cbsession/redirect session flash "/blog"))
  (GET "/blog"
    {session :session, flash :flash}
    (cbsession/redirect 
      (cbsession/session-with-return-url session home-route) 
      flash 
      home-route))

  (GET "/blog/archive/" {session :session, flash :flash} (cbsession/redirect session flash "/blog/archive"))
  (GET "/blog/archive" 
    {session :session, params :params, flash :flash, uri :uri}
    ; TODO
    "hullo")

  (GET ["/blog/:start/:count/" :start #"[0-9]+" :count #"[0-9]+"] {session :session, flash :flash, params :params}
    (cbsession/redirect session flash (apply str ["/blog/" (:start params) "/" (:count params)])))
  (GET ["/blog/:start/:count" :start #"[0-9]+" :count #"[0-9]+"]
    {session :session, params :params, flash :flash, uri :uri}
    (let [session-info (cbauth/make-session-info session)]
      {
        :body (cbblog/get-posts session-info flash (:start params 0) (:count params 10))
        :session (cbsession/session-with-return-url session uri)
      }))

  (GET ["/post/:id/" :id #"[0-9]+"] {session :session, flash :flash, params :params}
    (cbsession/redirect session flash (apply str ["/post/" (:id params)])))
  (GET ["/post/:id" :id #"[0-9]+"]
    {session :session, params :params, flash :flash, uri :uri}
    (let [session-info (cbauth/make-session-info session)]
      {
        :body (cbblog/get-post session-info flash (:id params nil))
        :session (cbsession/session-with-return-url session uri)
      }))

  (GET "/posts/tag/:tag/" {session :session, flash :flash, params :params}
    (cbsession/redirect session flash (apply str ["/post/tag/" (:tag params)])))
  (GET "/posts/tag/:tag"
    {session :session, params :params, flash :flash, uri :uri}
    (let [session-info (cbauth/make-session-info session)] 
      {
        :body (cbblog/get-posts-for-tag session-info flash (:tag params))
        :session (cbsession/session-with-return-url session uri)
      }))

  ;; ADMIN FUNCTIONALITY
  (POST "/admin/login"
    {session :session, params :params, flash :flash, uri :uri}
    (cbauth/post-login session params))

  (GET "/admin/logout" 
    {session :session, params :params, flash :flash, uri :uri}
    (cbauth/post-logout session params))

  (GET ["/admin/edit/post/:id" :id #"[0-9]+"]
    {session :session, params :params, flash :flash, uri :uri}
    (let [
      session-info (cbauth/make-session-info session)
      authorized (cbauth/admin? session)]
      (if authorized
        {
          :body (cbblog/get-post-composer session-info flash (:id params nil))
          :session session
        }
        (response-403 
          session
          flash
          :message "You must first log in to edit posts."))))

  (GET "/admin/new/post" 
    {session :session, params :params, flash :flash, uri :uri}
    (let [
      session-info (cbauth/make-session-info session)
      authorized (cbauth/admin? session)]
      (if authorized
        {
          :body (cbblog/get-post-composer session-info flash nil)
          :session session
        }
        (response-403
          session
          flash
          :message "You must first log in to compose new posts."))))

  (GET ["/admin/delete/post/:id" :id #"[0-9]+"]
    {session :session, params :params, flash :flash, uri :uri} 
    (if (cbauth/admin? session)
      (cbblog/post-delete! session params)
      (response-403 
        session 
        flash 
        :message "You must first log in to delete posts.")))

  (POST "/admin/submit/post"
    {session :session, params :params, flash :flash, uri :uri}
    (if (cbauth/admin? session) 
      (cbblog/post-npsubmit! session params)
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
