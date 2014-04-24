(ns clojure-blog.auth)

(declare admin?)

(defn make-session-info [session]
  "Build the 'session-info' map, which encapsulates session-specific info useful when rendering a response body"
  (if session 
    (let [] 
      {:logged-in (admin? session)
        :username "Admin"})   ; Fix this
    {:logged-in false
      :username nil}))
 

;; TODO: Actually implement this properly
(defn credentials-valid? [params]
  (and
   (= (:username params nil) "admin") 
   (= (:password params nil) "12345")))

(defn admin? [session]
  (= (:admin-session session nil) "session-active"))

(defn add-admin-session [session]
  (let [c-session (if session session {})]
    (assoc c-session :admin-session "session-active")))
