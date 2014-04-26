(ns clojure-blog.auth 
  (:require
    [clojure-blog.session :as cbsession]))

(declare admin?)
(declare username)

(defn make-session-info [session]
  "Build the 'session-info' map, which encapsulates session-specific info useful when rendering a response body"
  {:logged-in (admin? session)
    :username (username session)})

;; TODO: Actually implement this properly
(defn credentials-valid? [params]
  (and
   (= (:username params nil) "admin") 
   (= (:password params nil) "12345")))

(defn username [session]
  ; TODO 
  (when session "Admin"))

(defn admin? [session]
  (= (:admin-session session nil) "session-active"))


;; AUTH RESPONSE HANDLERS

(defn post-login [session params]
  (let [
    is-valid (credentials-valid? params)
    return-url (:return-url session "/")
    session (if is-valid (cbsession/session-added-admin-privileges session) (cbsession/session-removed-admin-privileges session))
    flash-msg (if is-valid "Logged in!" "Invalid credentials.")]
    (cbsession/redirect session flash-msg return-url)))

(defn post-logout [session params]
  (let [
    flash-msg (if (admin? session) "Logged out." "Already logged out!")
    session (cbsession/session-removed-admin-privileges session)
    return-url "/"]
    (cbsession/redirect session flash-msg return-url)))
