;; routes.clj (clojure-firefly)
;; Copyright (c) 2014 Austin Zheng
;; Released under the terms of the MIT License

(ns clojure-blog.routes
   (:require
    [clojure-blog.settings :as settings]))

;; Shared routes
(def main-route "/")
(def home-route (apply str ["/blog/0/" settings/posts-per-page]))
(def about-route "/about")

;; Blog routes
(defn blog-route [start number] (apply str ["/blog/" start "/" number]))
(defn blog-post-route [post-id] (apply str ["/post/" post-id]))
(defn blog-posts-for-tag-route [tag] (apply str ["/posts/tag/" tag]))
(def blog-archive-route "/blog/archive")

;; Link routes
(def links-route "/links")

;; Admin routes
(def login-route "/admin/login")
(def logout-route "/admin/logout")
(def new-post-route "/admin/new/post")
(defn edit-post-route [post-id] (apply str ["/admin/edit/post/" post-id]))
(defn delete-post-route [post-id] (apply str ["/admin/delete/post/" post-id]))
(def new-link-route "/admin/new/link")
