(ns clojure-blog.template
   (:require 
    [clojure-blog.util :as cbutil]
    [clojure-blog.routes :as cbroutes]
    [clojure-blog.settings :as cbsettings]
    [net.cgrand.enlive-html :as html]
    [clj-time.core :as time-core]
    [clj-time.local :as time-local]
    [clj-time.format :as time-format]
    [clj-time.coerce :as time-coerce]))

(declare post-snippet)
(declare header-snippet)
(declare footer-snippet)

(declare post-compose)

(defn build-composer [session-info-map flash-msg post-id post-map]
  "Given a post ID and a post map, build the composer view"
  (let [
    show-delete (not (nil? post-id))
    c-post-map (if post-map post-map {})
    title (:post-title c-post-map "")
    content (:post-content c-post-map "")]
    (post-compose session-info-map flash-msg post-id show-delete title content)))

(defn- format-date [raw-date]
  "Given a raw date string (long as string), turn it into a formatted date-time string"
  (let [
    time-zone (time-core/time-zone-for-id cbsettings/time-zone-id)
    f (time-format/with-zone (time-format/formatter "MM/dd/yyyy HH:mm") time-zone)
    as-long (cbutil/parse-integer raw-date)
    as-obj (when as-long (time-coerce/from-long as-long))]
    (if as-obj (time-format/unparse f as-obj) "(Unknown)")))

(defn- invoke-post-snippet [session-info-map is-single post-id post-map]
  "Generate the HTML for a single post"
  (let [
    {title :post-title date :post-date content :post-content is-edited :post-edited edit-date :post-edit-date} post-map
    formatted-date (format-date date)
    formatted-edit-date (format-date edit-date)
    ]
    (post-snippet 
      is-single 
      (:logged-in session-info-map false)
      post-id
      title
      formatted-date
      content 
      is-edited
      formatted-edit-date)))

(defn- invoke-header-snippet [session-info-map]
  "Generate the HTML for the blog header"
  (header-snippet
    cbsettings/blog-title
    cbsettings/blog-subtitle
    cbroutes/main-route))

(defn- invoke-footer-snippet [session-info-map]
  "Generate the HTML for the blog footer"
  (footer-snippet
    (:logged-in session-info-map false)
    (:username session-info-map)
    cbroutes/login-route
    cbroutes/logout-route
    cbroutes/new-post-route))


;; ENLIVE UTILITY

(defn- no-change []
  "No-op for enlive"
  (fn [arg] arg))

(defn- only-if [pred]
  "Pass through an element unchanged if the predicate is true, otherwise remove it"
  (if pred (no-change) nil))


;;; GENERAL TEMPLATES

;; Snippet for the navigation bar
(html/defsnippet header-snippet "header-snippet.html"
  [:div.page-header]
  [title subtitle main-route]
  [:a#title] (html/content title)
  [:a#title] (html/set-attr :href main-route)
  [:p.subtitle] (html/content subtitle))

;; Snippet for the footer
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

;; Snippet for 'flash' messages
(html/defsnippet flash-snippet "flash-snippet.html"
  [:div.flash]
  [flash-msg]
  [:span#flash-message] (html/content (if flash-msg flash-msg "(no message)")))

;; Template for the error page
(html/deftemplate error-page "error.html"
  [session-info-map flash-msg error-msg back-link back-msg]
  [:title] (html/content "Error")
  [:div.nav] (html/html-content (reduce str (html/emit* (invoke-header-snippet session-info-map))))
  [:div.flash] (when flash-msg (html/html-content (reduce str (html/emit* (flash-snippet flash-msg)))))
  [:span.message] (html/content (if error-msg error-msg "Sorry, an error seems to have occurred."))
  [:a#goback] (when back-link (html/set-attr :href back-link))
  [:a#goback] (when back-link (html/content (if back-msg back-msg "Back")))
  [:div.footer] (html/html-content (reduce str (html/emit* (invoke-footer-snippet session-info-map)))))


;;; BLOG-SPECIFIC

;; Snippet for a single post entry
(html/defsnippet post-snippet "post-snippet.html" 
  [:div.post]
  [is-single is-logged-in post-id title date content is-edited edit-date]
  [:h2#post-title] (when is-single (html/content title))
  [:h2#link-post-title] (only-if (not is-single))
  [:a#title-link] (when-not is-single (html/content title))
  [:a#title-link] (when-not is-single (html/set-attr :href (cbroutes/blog-post-route post-id)))
  [:span.if-logged-in] (only-if is-logged-in)
  [:a#edit-post] (when is-logged-in (html/set-attr :href (cbroutes/edit-post-route post-id)))
  [:a#delete-post] (when is-logged-in (html/set-attr :href (cbroutes/delete-post-route post-id)))
  [:span#edit-note] (when is-edited (html/content (if edit-date ["Last edited: " edit-date] "Edited")))
  [:span.if-edited] (only-if is-edited)
  [:span#date] (html/content (apply str ["Date: " date]))
  [:div.content] (html/html-content content))

;; Snippet for a post preview
(html/defsnippet post-preview-snippet "post-snippet.html" 
  [:div.post]
  [title content]
  [:h2#post-title] (html/content title)
  [:h2#link-post-title] nil
  [:a#title-link] nil
  [:span.if-logged-in] nil
  [:a#edit-post] nil
  [:a#delete-post] nil
  [:span#edit-note] nil
  [:span.if-edited] nil
  [:span#date] (html/content "(Preview Mode)")
  [:div.content] (html/html-content content))

;; Template for the single-post page
(html/deftemplate post-page "post.html"
  [session-info-map flash-msg post-id post-map]
  [:title] (html/content [(:post-title post-map "Post") " - " cbsettings/blog-title])
  [:div.nav] (html/html-content (reduce str (html/emit* (invoke-header-snippet session-info-map))))
  [:div.flash] (when flash-msg (html/html-content (reduce str (html/emit* (flash-snippet flash-msg)))))
  [:div.post] (html/html-content (reduce str (html/emit* (invoke-post-snippet session-info-map true post-id post-map))))
  [:div.footer] (html/html-content (reduce str (html/emit* (invoke-footer-snippet session-info-map)))))

;; Template for the multiple-post blog page
(html/deftemplate blog-page "blog.html"
  [session-info-map flash-msg post-id-list post-map-list prev-url prev-description current-nav-description next-url next-description]
  [:title] (html/content cbsettings/blog-title)
  [:div.nav] (html/html-content (reduce str (html/emit* (invoke-header-snippet session-info-map))))
  [:div.flash] (when flash-msg (html/html-content (reduce str (html/emit* (flash-snippet flash-msg)))))
  [:div.posts] (html/html-content (reduce str (map #(reduce str (html/emit* (invoke-post-snippet session-info-map false %1 %2))) post-id-list post-map-list)))
  [:a#blog-prev-link] (html/set-attr :href prev-url)
  [:a#blog-prev-link] (html/content prev-description)
  [:span.has-prev] (only-if prev-url)
  [:a#blog-next-link] (html/set-attr :href next-url)
  [:a#blog-next-link] (html/content next-description)
  [:span.has-next] (only-if next-url)
  [:span#blog-current] (html/content (if current-nav-description current-nav-description "-"))
  [:div.footer] (html/html-content (reduce str (html/emit* (invoke-footer-snippet session-info-map)))))

;; Template for the blog post composer page
(html/deftemplate post-compose "post-compose.html"
  [session-info-map flash-msg post-id show-delete title content]
  [:title] (html/content ["Composer - " cbsettings/blog-title])
  [:div.nav] (html/html-content (reduce str (html/emit* (invoke-header-snippet session-info-map))))
  [:div.flash] (when flash-msg (html/html-content (reduce str (html/emit* (flash-snippet flash-msg)))))
  [:input#post-title] (html/set-attr :value title)
  [:input#post-id] (when post-id (html/set-attr :value post-id))
  [:textarea#post-content] (html/content content)
  [:button#action-submit] (html/set-attr :name (if post-id "edit-post" "add-post"))
  [:span#delete-button] (when show-delete (html/html-content "<button name=\"delete\" type=\"submit\">Delete</button>"))
  [:div.footer] (html/html-content (reduce str (html/emit* (invoke-footer-snippet session-info-map)))))

;; Template for the blog post preview page
(html/deftemplate post-preview "post-preview.html"
  [session-info-map flash-msg can-submit post-id title content]
  [:div.nav] (html/html-content (reduce str (html/emit* (invoke-header-snippet session-info-map))))
  [:div.flash] (when flash-msg (html/html-content (reduce str (html/emit* (flash-snippet flash-msg)))))
  [:div.post] (html/html-content (reduce str (html/emit* (post-preview-snippet title content))))
  [:input#post-id] (when post-id (html/set-attr :value post-id))
  [:input#post-title] (html/set-attr :value title)
  [:input#post-content] (html/set-attr :value content)
  [:button#action-submit] (when can-submit (html/set-attr :name (if post-id "edit-post" "add-post"))))
