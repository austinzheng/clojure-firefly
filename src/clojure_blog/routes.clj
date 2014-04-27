(ns clojure-blog.routes)

;; Shared routes
(def main-route "/")

;; Blog routes
(defn blog-post-route [post-id] (apply str ["/post/" post-id]))

;; Admin routes
(def login-route "/admin/login")
(def logout-route "/admin/logout")
(def new-post-route "/admin/new/post")
(defn edit-post-route [post-id] (apply str ["/admin/edit/post/" post-id]))
(defn delete-post-route [post-id] (apply str ["/admin/delete/post/" post-id]))
(def new-link-route "/admin/new/link")
