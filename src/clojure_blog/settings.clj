(ns clojure-blog.settings)

;; The name of the blog
(def blog-title "AUSTIN'S AWESOME BLOG")

;; A subtitle for the blog
(def blog-subtitle "This is a very cool blog, and I really like it")

;; How many posts each blog page should show
(def posts-per-page 10)

;; Don't change these if there isn't a pressing need
(def main-route "/")
(def login-route "/admin/login")
(def logout-route "/admin/logout")
(def new-post-route "/admin/new/post")
(def new-link-route "/admin/new/link")