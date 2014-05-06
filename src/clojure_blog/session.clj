;; session.clj (clojure-firefly)
;; Copyright (c) 2014 Austin Zheng
;; Released under the terms of the MIT License

(ns clojure-blog.session  
  (:require 
    [ring.util.response :as response]))

(defn redirect [session flash-msg url] 
  (let [
    c-url (if url url "/")
    base-response (response/redirect c-url)
    with-session (if session (assoc base-response :session session) base-response)
    with-flash (if flash-msg (assoc with-session :flash flash-msg) with-session)]
    with-flash)) 

(defn session-with-return-url [session url]
  "Set a URL to return to e.g. after login"
  (let [c-session (if session session {})]
    (assoc c-session :return-url url)))

(defn session-without-return-url [session url]
  (if session (dissoc session :return-url) {}))

(defn session-added-admin-privileges [session username]
  (let [
    c-session (if session session {})
    with-admin (assoc c-session :admin-session "session-active")
    with-username (assoc with-admin :username username)]
    with-username))

(defn session-removed-admin-privileges [session]
  (if session (dissoc session :admin-session) {}))
