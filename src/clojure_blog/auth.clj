(ns clojure-blog.auth)

;; TODO: Actually implement this properly
(defn credentials-valid? [params]
  (and (= (:username params nil) "admin") (:password params nil) "12345"))

(defn admin? [session]
  (= (:admin-session session nil) "session-active"))

(defn add-admin-session [session]
  (let [c-session (if session session {})]
    (assoc c-session :admin-session "session-active")))
