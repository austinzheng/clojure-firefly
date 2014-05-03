(ns clojure-blog.blog-templates
  (:use clojure-blog.template)
  (:require
    [clojure-blog.tags :as tags]
    [clojure-blog.routes :as r]
    [clojure-blog.util :as util]
    [clojure-blog.settings :as settings]
    [net.cgrand.enlive-html :as html]))

(declare post-compose)
(declare post-snippet)
(declare archive-snippet)

;; HELPERS
(defn build-composer [session-info-map flash-msg post-id post-map]
  "Given a post ID and a post map, build the composer view"
  (let [
    show-delete (not (nil? post-id))
    c-post-map (if post-map post-map {})
    title (:post-title c-post-map "")
    content (:post-content c-post-map "")
    tags-list (:post-tags c-post-map nil)
    tags (if tags-list (tags/join-tags tags-list) "")]
    (post-compose session-info-map flash-msg post-id show-delete title content tags tags)))

(defn- invoke-post-snippet [session-info-map is-single post-id post-map]
  "Generate the HTML for a single post"
  (let [
    {title :post-title date :post-date content :post-content is-edited :post-edited edit-date :post-edit-date} post-map
    formatted-date (util/format-date date)
    formatted-edit-date (util/format-date edit-date)
    tags (:post-tags post-map nil)
    formatted-tags (tags/tags-html-for-tags tags)
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
    formatted-date (util/format-date date)
    url (when post-id (r/blog-post-route post-id))
    logged-in (:logged-in session-info-map false)
    edit-post-route (when logged-in (r/edit-post-route post-id))
    snippet (archive-snippet logged-in edit-post-route title formatted-date url)]
    (reduce str (html/emit* snippet))))


;; TEMPLATES
; Snippet for a single post entry
(html/defsnippet post-snippet "post-snippet.html" 
  [:div.post]
  [is-single is-logged-in post-id title date content is-edited edit-date html-tag-data]
  [:h2#post-title] (when is-single (html/content title))
  [:h2#link-post-title] (only-if (not is-single))
  [:a#title-link] (when-not is-single (html/content title))
  [:a#title-link] (when-not is-single (html/set-attr :href (r/blog-post-route post-id)))
  [:span.if-logged-in] (only-if is-logged-in)
  [:a#edit-post] (when is-logged-in (html/set-attr :href (r/edit-post-route post-id)))
  [:span#edit-note] (when is-edited (html/content (if edit-date ["Last edited: " edit-date] "Edited")))
  [:span.if-edited] (only-if is-edited)
  [:span#date] (html/content (apply str ["Date: " date]))
  [:span#tags-list] (html/html-content html-tag-data)
  [:div.content] (html/html-content content))

; Snippet for a post preview
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

; Snippet for an archive entry
(html/defsnippet archive-snippet "archive-snippet.html"
  [:div.archive-item]
  [is-logged-in edit-post-route title date url]
  [:a#item-url] (html/content title)
  [:a#item-url] (html/set-attr :href url)
  [:span#item-date] (html/content date)
  [:span.if-logged-in] (only-if is-logged-in)
  [:a#edit-post] (when edit-post-route (html/set-attr :href edit-post-route)))

; Template for the single-post page
(html/deftemplate post-page "post.html"
  [session-info-map flash-msg post-id post-map]
  [:title] (html/content [(:post-title post-map "Post") " - " settings/blog-title])
  [:div.nav] (html/html-content (invoke-header-snippet session-info-map))
  [:div.flash] (when flash-msg (html/html-content (reduce str (html/emit* (flash-snippet flash-msg)))))
  [:div.post] (html/html-content (invoke-post-snippet session-info-map true post-id post-map))
  [:div.footer] (html/html-content (invoke-footer-snippet session-info-map)))

; Template for the multiple-post blog page
(html/deftemplate blog-page "blog.html"
  [session-info-map flash-msg post-id-list post-map-list no-posts-msg prev-url prev-description current-nav-description next-url next-description]
  [:title] (html/content settings/blog-title)
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

; Template for the archive page
(html/deftemplate archive-page "archive.html"
  [session-info-map flash-msg metadata-list no-posts-msg]
  [:title] (html/content (reduce str ["Archives - " settings/blog-title]))
  [:div#no-posts] (when (empty? metadata-list) (html/content no-posts-msg))
  [:div.nav] (html/html-content (invoke-header-snippet session-info-map))
  [:div.flash] (when flash-msg (html/html-content (reduce str (html/emit* (flash-snippet flash-msg)))))
  [:ul#archive-posts-list] (only-if (seq metadata-list))
  [:div.archive] (when (seq metadata-list) (html/html-content (reduce str (map #(invoke-archive-snippet session-info-map %) metadata-list))))
  [:div.footer] (html/html-content (invoke-footer-snippet session-info-map)))

; Template for the blog post composer page
(html/deftemplate post-compose "post-compose.html"
  [session-info-map flash-msg post-id show-delete title content raw-tags raw-old-tags]
  [:title] (html/content ["Composer - " settings/blog-title])
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

; Template for the blog post preview page
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
