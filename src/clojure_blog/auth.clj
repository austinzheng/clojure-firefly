(ns clojure-blog.auth 
  (:require
    [clojure-blog.session :as ss]
    [clojure-blog.database :as db]
    [clojurewerkz.scrypt.core :as scrypt]))

(declare admin?)
(declare username)

;; SESSION-RELATED
(defn make-session-info [session]
  "Build the 'session-info' map, which encapsulates session-specific info useful when rendering a response body"
  {
    :logged-in (admin? session)
    :username (:username session "(error)")
  })

(defn admin? [session]
  (:admin-session session nil))


;; SECURITY
(defn first-time? []
  ; Here in case we want to do something a bit more robust in the future...
  (not (db/blog-has-accounts?)))

(defn- acceptable-username? [username]
  "Returns true if a username meets criteria for being a valid username (e.g. length), false otherwise"
  (and (string? username) (> (count username) 4)))

(defn- acceptable-password? [password]
  "Returns true if a password meets criteria for being a valid password, false otherwise"
  (and (string? password) (> (count password) 7)))

(defn- hash-password [password]
  "Takes a password and returns the scrypt hashed version"
  (scrypt/encrypt password 16384 8 1))

(defn- validate-password [password pw-hash]
  "Returns true if a password is equivalent to a scrypt hash, false otherwise or upon error"
  (try (scrypt/verify password pw-hash) (catch IllegalArgumentException e false)))

(defn credentials-valid? [params]
  "Takes in unsanitized params and validates the credentials within, if there are any. All error conditions cause this
  function to return false."
  (let [
    username (:username params nil)
    password (:password params nil)
    well-formed (and (acceptable-username? username) (acceptable-password? password))
    retrieved-hash (when well-formed (db/hash-for-username username))
    ]
    (if (and well-formed (seq retrieved-hash)) 
      (validate-password password retrieved-hash)
      false)))

(defn- add-account [username password]
  "Given a *well-formed* username and password, try to add a new account. Returns true if successful, false otherwise"
  (let [pw-hash (hash-password password)]
    (if (seq pw-hash)
      (db/add-account username pw-hash)
      false)))


;; AUTH RESPONSE HANDLERS
(defn post-login [session params]
  (let [
    is-valid (credentials-valid? params)
    return-url (:return-url session "/")
    username (:username params nil)
    session (if is-valid (ss/session-added-admin-privileges session username) (ss/session-removed-admin-privileges session))
    flash-msg (if is-valid "Logged in!" "Invalid credentials.")]
    (ss/redirect session flash-msg return-url)))

(defn post-logout [session params]
  (let [
    flash-msg (if (admin? session) "Logged out." "Already logged out!")
    session (ss/session-removed-admin-privileges session)
    return-url "/"]
    (ss/redirect session flash-msg return-url)))

(defn post-first-time [session params]
  (let [
    actually-first-time (not (db/blog-has-accounts?))
    username (:new-username params nil)
    password (:new-password params nil)
    well-formed (and (acceptable-username? username) (acceptable-password? password))
    account-added (if (and actually-first-time well-formed) (add-account username password) false)
    flash-msg (cond
      (not actually-first-time) "Error: you've already done first-time setup!"
      (not well-formed) "Error: the username and/or password you provided were invalid."
      (not account-added) "Error: couldn't add the new account to the database."
      :else "Success! Added your new administrator account.")
    ]
    (ss/redirect session flash-msg (:return-url session "/"))))
