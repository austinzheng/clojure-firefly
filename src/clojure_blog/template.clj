(ns clojure-blog.template
   (:require
    [clojure-blog.tags :as cbtags]
    [clojure-blog.util :as cbutil]
    [clojure-blog.routes :as cbroutes]
    [clojure-blog.settings :as cbsettings]
    [clojure-blog.tags :as cbtags]
    [net.cgrand.enlive-html :as html]
    [clj-time.core :as time-core]
    [clj-time.local :as time-local]
    [clj-time.format :as time-format]
    [clj-time.coerce :as time-coerce]))

(declare header-snippet)
(declare footer-snippet)
(declare post-snippet)
(declare archive-snippet)

(declare post-compose)

(defn build-composer [session-info-map flash-msg post-id post-map]
  "Given a post ID and a post map, build the composer view"
  (let [
    show-delete (not (nil? post-id))
    c-post-map (if post-map post-map {})
    title (:post-title c-post-map "")
    content (:post-content c-post-map "")
    tags-list (:post-tags c-post-map nil)
    tags (if tags-list (cbtags/join-tags tags-list) "")]
    (post-compose session-info-map flash-msg post-id show-delete title content tags tags)))

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
    tags (:post-tags post-map nil)
    formatted-tags (cbtags/tags-html-for-tags tags)
    snippet (post-snippet 
      is-single 
      (:logged-in session-info-map false)
      post-id
      title
      formatted-date
      content 
      is-edited
      formatted-edit-date
      formatted-tags)]
    (reduce str (html/emit* snippet))))

(defn- invoke-archive-snippet [session-info-map metadata-map]
  "Generate the HTML for a single archive item"
  (let [
    {post-id :post-id title :post-title date :post-date} metadata-map
    formatted-date (format-date date)
    url (when post-id (cbroutes/blog-post-route post-id))
    logged-in (:logged-in session-info-map false)
    edit-post-route (when logged-in (cbroutes/edit-post-route post-id))
    snippet (archive-snippet logged-in edit-post-route title formatted-date url)]
    (reduce str (html/emit* snippet))))

(defn- invoke-header-snippet [session-info-map]
  "Generate the HTML for the blog header"
  (reduce str 
    (html/emit*
     (header-snippet 
      cbsettings/blog-title
       cbsettings/blog-subtitle
        cbroutes/main-route))))

(defn- invoke-footer-snippet [session-info-map]
  "Generate the HTML for the blog footer"
  (reduce str 
    (html/emit* 
      (footer-snippet
        (:logged-in session-info-map false)
        (:username session-info-map)
        cbroutes/login-route
        cbroutes/logout-route
        cbroutes/new-post-route))))


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
  [:a#sect-home] (html/set-attr :href cbroutes/main-route)
  [:a#sect-archive] (html/set-attr :href cbroutes/blog-archive-route)
  ; [:a#sect-links] (html/set-attr :href cbroutes/links-route)
  ; [:a#sect-about] (html/set-attr :href cbroutes/about-route)
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

;; Template for the admin page
(html/deftemplate admin-page "admin.html"
  [session-info-map flash-msg is-first-time]
  [:title] (html/content "Control Panel")
  [:div.nav] (html/html-content (invoke-header-snippet session-info-map))
  [:div.flash] (when flash-msg (html/html-content (reduce str (html/emit* (flash-snippet flash-msg)))))
  [:div#first-time] (only-if is-first-time)
  [:div.footer] (html/html-content (invoke-footer-snippet session-info-map)))

;; Template for the error page
(html/deftemplate error-page "error.html"
  [session-info-map flash-msg error-msg back-link back-msg]
  [:title] (html/content "Error")
  [:div.nav] (html/html-content (invoke-header-snippet session-info-map))
  [:div.flash] (when flash-msg (html/html-content (reduce str (html/emit* (flash-snippet flash-msg)))))
  [:span.message] (html/content (if error-msg error-msg "Sorry, an error seems to have occurred."))
  [:a#goback] (when back-link (html/set-attr :href back-link))
  [:a#goback] (when back-link (html/content (if back-msg back-msg "Back")))
  [:div.footer] (html/html-content (invoke-footer-snippet session-info-map)))


;;; BLOG-SPECIFIC

;; Snippet for a single post entry
(html/defsnippet post-snippet "post-snippet.html" 
  [:div.post]
  [is-single is-logged-in post-id title date content is-edited edit-date html-tag-data]
  [:h2#post-title] (when is-single (html/content title))
  [:h2#link-post-title] (only-if (not is-single))
  [:a#title-link] (when-not is-single (html/content title))
  [:a#title-link] (when-not is-single (html/set-attr :href (cbroutes/blog-post-route post-id)))
  [:span.if-logged-in] (only-if is-logged-in)
  [:a#edit-post] (when is-logged-in (html/set-attr :href (cbroutes/edit-post-route post-id)))
  [:span#edit-note] (when is-edited (html/content (if edit-date ["Last edited: " edit-date] "Edited")))
  [:span.if-edited] (only-if is-edited)
  [:span#date] (html/content (apply str ["Date: " date]))
  [:span#tags-list] (html/html-content html-tag-data)
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
  [:span#edit-note] nil
  [:span.if-edited] nil
  [:span#date] (html/content "(Preview Mode)")
  [:span#tags-list] nil
  [:div.content] (html/html-content content))

;; Snippet for an archive entry
(html/defsnippet archive-snippet "archive-snippet.html"
  [:div.archive-item]
  [is-logged-in edit-post-route title date url]
  [:a#item-url] (html/content title)
  [:a#item-url] (html/set-attr :href url)
  [:span#item-date] (html/content date)
  [:span.if-logged-in] (only-if is-logged-in)
  [:a#edit-post] (when edit-post-route (html/set-attr :href edit-post-route)))

;; Template for the single-post page
(html/deftemplate post-page "post.html"
  [session-info-map flash-msg post-id post-map]
  [:title] (html/content [(:post-title post-map "Post") " - " cbsettings/blog-title])
  [:div.nav] (html/html-content (invoke-header-snippet session-info-map))
  [:div.flash] (when flash-msg (html/html-content (reduce str (html/emit* (flash-snippet flash-msg)))))
  [:div.post] (html/html-content (invoke-post-snippet session-info-map true post-id post-map))
  [:div.footer] (html/html-content (invoke-footer-snippet session-info-map)))

;; Template for the multiple-post blog page
(html/deftemplate blog-page "blog.html"
  [session-info-map flash-msg post-id-list post-map-list no-posts-msg prev-url prev-description current-nav-description next-url next-description]
  [:title] (html/content cbsettings/blog-title)
  [:div.nav] (html/html-content (invoke-header-snippet session-info-map))
  [:div.flash] (when flash-msg (html/html-content (reduce str (html/emit* (flash-snippet flash-msg)))))
  [:div.posts] (html/html-content (reduce str (map #(invoke-post-snippet session-info-map false %1 %2) post-id-list post-map-list)))
  [:div.posts] (if (empty? post-map-list) (html/content no-posts-msg) (no-change))
  [:a#blog-prev-link] (html/set-attr :href prev-url)
  [:a#blog-prev-link] (html/content prev-description)
  [:span.has-prev] (only-if prev-url)
  [:a#blog-next-link] (html/set-attr :href next-url)
  [:a#blog-next-link] (html/content next-description)
  [:span.has-next] (only-if next-url)
  [:span#blog-current] (when current-nav-description (html/content current-nav-description))
  [:div.blog-nav] (only-if (seq post-map-list))
  [:br#blog-nav-separator] (only-if (seq post-map-list))
  [:div.footer] (html/html-content (invoke-footer-snippet session-info-map)))

;; Template for the archive page
(html/deftemplate archive-page "archive.html"
  [session-info-map flash-msg metadata-list no-posts-msg]
  [:title] (html/content (reduce str ["Archives - " cbsettings/blog-title]))
  [:div#no-posts] (when (empty? metadata-list) (html/content no-posts-msg))
  [:div.nav] (html/html-content (invoke-header-snippet session-info-map))
  [:div.flash] (when flash-msg (html/html-content (reduce str (html/emit* (flash-snippet flash-msg)))))
  [:ul#archive-posts-list] (only-if (seq metadata-list))
  [:div.archive] (when (seq metadata-list) (html/html-content (reduce str (map #(invoke-archive-snippet session-info-map %) metadata-list))))
  [:div.footer] (html/html-content (invoke-footer-snippet session-info-map)))

;; Template for the blog post composer page
(html/deftemplate post-compose "post-compose.html"
  [session-info-map flash-msg post-id show-delete title content raw-tags raw-old-tags]
  [:title] (html/content ["Composer - " cbsettings/blog-title])
  [:div.nav] (html/html-content (invoke-header-snippet session-info-map))
  [:div.flash] (when flash-msg (html/html-content (reduce str (html/emit* (flash-snippet flash-msg)))))
  [:input#post-title] (html/set-attr :value title)
  [:input#post-id] (when post-id (html/set-attr :value post-id))
  [:input#post-old-tags] (if (> (count raw-tags) 0) (html/set-attr :value raw-old-tags) nil)
  [:input#post-tags] (html/set-attr :value raw-tags)
  [:textarea#post-content] (html/content content)
  [:button#action-submit] (html/set-attr :name (if post-id "edit-post" "add-post"))
  [:span#delete-button] (when show-delete (html/html-content "<button name=\"delete\" type=\"submit\">Delete</button>"))
  [:div.footer] (html/html-content (invoke-footer-snippet session-info-map)))

;; Template for the blog post preview page
(html/deftemplate post-preview "post-preview.html"
  [session-info-map flash-msg can-submit post-id title content tags old-tags]
  [:div.nav] (html/html-content (invoke-header-snippet session-info-map))
  [:div.flash] (when flash-msg (html/html-content (reduce str (html/emit* (flash-snippet flash-msg)))))
  [:div.post] (html/html-content (reduce str (html/emit* (post-preview-snippet title content))))
  [:input#post-id] (when post-id (html/set-attr :value post-id))
  [:input#post-title] (html/set-attr :value title)
  [:input#post-content] (html/set-attr :value content)
  [:input#post-tags] (html/set-attr :value tags)
  [:input#post-old-tags] (html/set-attr :value old-tags)
  [:button#action-submit] (when can-submit (html/set-attr :name (if post-id "edit-post" "add-post"))))
