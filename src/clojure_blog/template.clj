(ns clojure-blog.template
   (:require 
    [clojure-blog.settings :as cbsettings]
    [net.cgrand.enlive-html :as html]))

(declare post-snippet)
(declare header-snippet)
(declare footer-snippet)

(defn- invoke-post-snippet [post-map]
  (post-snippet 
    (:title post-map "") 
    (:date post-map "")
    (:content post-map "") 
    (:edited post-map false)
    (:edit-date post-map "")))

(defn- invoke-header-snippet [session-info-map]
  (header-snippet
    cbsettings/blog-title
    cbsettings/blog-subtitle
    cbsettings/main-route))

(defn- invoke-footer-snippet [session-info-map]
  (footer-snippet
    (:logged-in session-info-map false)
    (:username session-info-map)
    cbsettings/login-route
    cbsettings/logout-route
    cbsettings/new-post-route))

;; Snippet for a single post entry
(html/defsnippet post-snippet "post-snippet.html" 
  [:div.post]
  [title date content edited edit-date]
  [:h2] (html/content title)
  [:span.date] (html/content date)
  [:div.content] (html/html-content content))

;; Snippet for the navigation bar
(html/defsnippet header-snippet "header-snippet.html"
  [:div.page-header]
  [title subtitle main-route]
  [:a.title] (html/content title)
  [:a.title] (html/set-attr :href main-route)
  [:p.subtitle] (html/content subtitle))

;; Snippet for the footer
(html/defsnippet footer-snippet "footer-snippet.html"
  [:div.page-footer]
  [is-logged-in username login-route logout-route new-post-route]
  ; Hide login if necessary, and set it up otherwise
  [:form.login-form] (if is-logged-in nil (html/set-attr :action login-route))
  ; Hide logout link if necessary, and set it up otherwise
  [:a.logout] (if is-logged-in (html/set-attr :href logout-route) nil)
  [:span.if-logged-in] (if is-logged-in (html/set-attr :unused "") nil)
  [:a.new-post] (html/set-attr :href new-post-route)
  [:span.credentials] (html/content (if is-logged-in ["Logged in as " username ". "] "")))

;; Template for the error page
(html/deftemplate error-page "error.html"
  [session-info-map error-msg back-link back-msg]
  [:title] (html/content "Error")
  [:div.nav] (html/html-content (reduce str (html/emit* (invoke-header-snippet session-info-map))))
  [:span.message] (html/content (if error-msg error-msg "Sorry, an error seems to have occurred."))
  [:a.goback] (if back-link (html/set-attr :href back-link) nil)
  [:a.goback] (if back-link (html/content (if back-msg back-msg "Back")) nil)
  [:div.footer] (html/html-content (reduce str (html/emit* (invoke-footer-snippet session-info-map)))))

;; Template for the single-post page
(html/deftemplate post-page "post.html"
  [session-info-map post-map]
  [:title] (html/content [(:title post-map) " - " cbsettings/blog-title])
  [:div.nav] (html/html-content (reduce str (html/emit* (invoke-header-snippet session-info-map))))
  [:div.post] (html/html-content (reduce str (html/emit* (invoke-post-snippet post-map))))
  [:div.footer] (html/html-content (reduce str (html/emit* (invoke-footer-snippet session-info-map)))))

;; Template for the multiple-post blog page
(html/deftemplate blog-page "blog.html"
  [session-info-map post-maps]
  [:title] (html/content cbsettings/blog-title)
  [:div.nav] (html/html-content (reduce str (html/emit* (invoke-header-snippet session-info-map))))
  [:div.posts] (html/html-content (reduce str (map #(reduce str (html/emit* (invoke-post-snippet %))) post-maps)))
  [:div.footer] (html/html-content (reduce str (html/emit* (invoke-footer-snippet session-info-map)))))

;; Template for the blog post composer page
(html/deftemplate post-compose "compose.html"
  [session-info-map post-map]
  [:title] (html/content ["Composer - " cbsettings/blog-title])
  [:div.nav] (html/html-content (reduce str (html/emit* (invoke-header-snippet session-info-map))))
  [:input.post-title] (if (contains? post-map :post-title) (html/set-attr :value (:post-title post-map)) (html/set-attr :unused ""))
  [:input.post-id] (if (contains? post-map :post-id) (html/set-attr :value (:post-id post-map)) nil)
  [:textarea.post-content] (if (contains? post-map :post-content) (html/content (:post-content post-map)) (html/set-attr :unused ""))
  [:button.action-submit] (html/set-attr :name (if (contains? post-map :post-id) "edit-post" "add-post"))
  [:span.delete-button] (if (:should-show-delete post-map) (html/html-content "<button name=\"delete\" type=\"submit\">Delete</button>") nil)
  [:div.footer] (html/html-content (reduce str (html/emit* (invoke-footer-snippet session-info-map)))))
