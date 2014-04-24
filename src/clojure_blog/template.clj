(ns clojure-blog.template
   (:require 
    [net.cgrand.enlive-html :as html]))

(declare post-snippet)
(declare nav-snippet)

(defn- invoke-post-snippet [post-map]
  (post-snippet 
    (:title post-map "") 
    (:date post-map "")
    (:content post-map "") 
    (:edited post-map false)
    (:edit-date post-map "")))

(defn- invoke-nav-snippet [nav-map]
  (nav-snippet
    (:logged-in nav-map false)
    (:username nav-map)
    (:login-route nav-map)
    (:logout-route nav-map)))

;; Snippet for a single post entry
(html/defsnippet post-snippet "post-snippet.html" 
  [:div.post]
  [title date content edited edit-date]
  [:h1] (html/content title)
  [:span.date] (html/content date)
  [:div.content] (html/html-content content))

;; Snippet for the navigation bar
(html/defsnippet nav-snippet "nav-snippet.html"
  [:div.navigation]
  [logged-in username login-route logout-route]
  ; Hide login if necessary, and set it up otherwise
  [:div.login] (if logged-in nil (html/set-attr :unused ""))
  [:form.login-form] (html/set-attr :action login-route)
  ; Hide logout link if necessary, and set it up otherwise
  [:a.logout] (if logged-in (html/set-attr :href logout-route) nil)
  [:span.credentials] (html/content (if logged-in ["Welcome, " username ". "] "")))

;; Template for the single-post page
(html/deftemplate post-page "post.html"
  [post-map nav-map]
  [:title] (html/content [(:title post-map) " - My Blog"])
  [:div.nav] (html/html-content (reduce str (html/emit* (invoke-nav-snippet nav-map))))
  [:div.post] (html/html-content (reduce str (html/emit* (invoke-post-snippet post-map)))))

;; Template for the multiple-post blog page
(html/deftemplate blog-page "blog.html"
  [post-maps nav-map]
  [:div.nav] (html/html-content (reduce str (html/emit* (invoke-nav-snippet nav-map))))
  [:div.posts] (html/html-content (reduce str (map #(reduce str (html/emit* (invoke-post-snippet %))) post-maps))))

;; Template for the blog post composer page
(html/deftemplate post-compose "compose.html"
  [post-map nav-map]
  [:div.nav] (html/html-content (reduce str (html/emit* (invoke-nav-snippet nav-map))))
  [:input.post-title] (if (contains? post-map :post-title) (html/set-attr :value (:post-title post-map)) (html/set-attr :unused ""))
  [:input.post-id] (if (contains? post-map :post-id) (html/set-attr :value (:post-id post-map)) nil)
  [:textarea.post-content] (if (contains? post-map :post-content) (html/content (:post-content post-map)) (html/set-attr :unused ""))
  [:button.action-submit] (html/set-attr :name (if (contains? post-map :post-id) "edit-post" "add-post"))
  [:span.delete-button] (if (:should-show-delete post-map) (html/html-content "<button name=\"delete\" type=\"submit\">Delete</button>") nil))
