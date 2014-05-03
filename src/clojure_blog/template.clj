(ns clojure-blog.template
  (:require
    [clojure-blog.routes :as routes]
    [clojure-blog.settings :as settings]
    [net.cgrand.enlive-html :as html]))

(declare header-snippet)
(declare footer-snippet)

;; ENLIVE UTILITY
(defn no-change []
  "No-op for enlive"
  (fn [arg] arg))

(defn only-if [pred]
  "Pass through an element unchanged if the predicate is true, otherwise remove it"
  (if pred (no-change) nil))


;; HELPERS
(defn invoke-header-snippet [session-info-map]
  "Generate the HTML for the blog header"
  (reduce str 
    (html/emit*
     (header-snippet settings/blog-title settings/blog-subtitle routes/main-route))))

(defn invoke-footer-snippet [session-info-map]
  "Generate the HTML for the blog footer"
  (reduce str 
    (html/emit* 
      (footer-snippet
        (:logged-in session-info-map false)
        (:username session-info-map)
        routes/login-route
        routes/logout-route
        routes/new-post-route))))


;; GENERAL TEMPLATES
; Snippet for the navigation bar
(html/defsnippet header-snippet "header-snippet.html"
  [:div.page-header]
  [title subtitle main-route]
  [:a#title] (html/content title)
  [:a#title] (html/set-attr :href main-route)
  [:a#sect-home] (html/set-attr :href routes/main-route)
  [:a#sect-archive] (html/set-attr :href routes/blog-archive-route)
  ; [:a#sect-links] (html/set-attr :href routes/links-route)
  ; [:a#sect-about] (html/set-attr :href routes/about-route)
  [:p.subtitle] (html/content subtitle))

; Snippet for the footer
(html/defsnippet footer-snippet "footer-snippet.html"
  [:div.page-footer]
  [is-logged-in username login-route logout-route new-post-route]
  ; Hide login if necessary, and set it up otherwise
  [:form#login-form] (when-not is-logged-in (html/set-attr :action login-route))
  ; Hide logout link if necessary, and set it up otherwise
  [:span.if-logged-in] (only-if is-logged-in)
  [:a#logout] (when is-logged-in (html/set-attr :href logout-route))
  [:a#new-post] (when is-logged-in (html/set-attr :href new-post-route))
  [:span#credentials] (html/content (if is-logged-in ["Logged in as " username ". "] "")))

; Snippet for 'flash' messages
(html/defsnippet flash-snippet "flash-snippet.html"
  [:div.flash]
  [flash-msg]
  [:span#flash-message] (html/content (if flash-msg flash-msg "(no message)")))

; Template for the admin page
(html/deftemplate admin-page "admin.html"
  [session-info-map flash-msg is-first-time]
  [:title] (html/content "Control Panel")
  [:div.nav] (html/html-content (invoke-header-snippet session-info-map))
  [:div.flash] (when flash-msg (html/html-content (reduce str (html/emit* (flash-snippet flash-msg)))))
  [:div#first-time] (only-if is-first-time)
  [:div.footer] (html/html-content (invoke-footer-snippet session-info-map)))

; Template for the error page
(html/deftemplate error-page "error.html"
  [session-info-map flash-msg error-msg back-link back-msg]
  [:title] (html/content "Error")
  [:div.nav] (html/html-content (invoke-header-snippet session-info-map))
  [:div.flash] (when flash-msg (html/html-content (reduce str (html/emit* (flash-snippet flash-msg)))))
  [:span.message] (html/content (if error-msg error-msg "Sorry, an error seems to have occurred."))
  [:a#goback] (when back-link (html/set-attr :href back-link))
  [:a#goback] (when back-link (html/content (if back-msg back-msg "Back")))
  [:div.footer] (html/html-content (invoke-footer-snippet session-info-map)))
