;; auth_database.clj (clojure-firefly)
;; Copyright (c) 2014 Austin Zheng
;; Released under the terms of the MIT License

(ns clojure-blog.auth-database
  (:use clojure-blog.database)
  (:require 
    [taoensso.carmine :as car :refer (wcar)]))

(declare hash-for-username)

;; API (security)
(defn blog-has-accounts? []
  "Returns false if there are no accounts configured for the blog. Returns true if there are, or if there was a
  database error (this is to fail-safe certain operations that can only happen if the blog hasn't been configured
  yet)."
  (let [
    acct-count (op-get-or-nil* (wcar* (car/hlen :account-map)))]
    (or (nil? acct-count) (> acct-count 0))))

(defn username-exists? [username]
  "Check for the existence of a username"
  (not (nil? (hash-for-username username))))

(defn add-account [username pwhash]
  "Add an account to the database. Returns true if the add succeded, false otherwise"
  (if (not (username-exists? username))
    (op-set-or-false* (wcar* (car/hset :account-map username pwhash) (car/bgsave)))
    false))

(defn hash-for-username [username]
  "Get the scrypt hash for a given username, or nil if the username is invalid"
  (op-get-or-nil* (wcar* (car/hget :account-map username))))
