(ns clojure-blog.handler
  (:use compojure.core)
  (:require 
	[clojure-blog.util :as cbutil]
	[clojure-blog.database :as cbdb]
	[net.cgrand.enlive-html :as html]
  	[compojure.handler :as handler]
    [compojure.route :as route]
    [ring.middleware.session :as session]
    [ring.adapter.jetty :as jetty]
    [clj-time.format :as time-format]
    [clj-time.coerce :as time-coerce]))

;; Config
(def posts-per-page 10)

(defn not-found [] "404: not found")

; TEMP --------
(defn session-handler [req] (assoc req :session "MAI SESSION :3"))

; Return a function that itself returns a pre-determined random number
(defn get-randn-fn []
	(let [rand-number (rand 1)]
		(fn [] (str rand-number))))

; /TEMP -------

(html/defsnippet post-snippet "post-snippet.html" 
	[:div.post]
	[post-dict]
	[:title] (html/content [(:title post-dict) " - Blog Post"])
	[:h1] (html/content (:title post-dict))
	[:span.date] (html/content (:date post-dict))
	[:div.content] (html/html-content (:content post-dict)))

(html/deftemplate post-page "post.html"
	[post-dict]
	[:div.post] (html/html-content (reduce str (html/emit* (post-snippet post-dict)))))

(html/deftemplate blog-page "blog.html"
	[post-dicts]
	[:div.posts] (html/html-content (reduce str (map (fn [post-dict] (reduce str (html/emit* (post-snippet post-dict)))) post-dicts))))

(html/deftemplate post-compose "compose.html"
	[post-dict]
	[:input.post-title] (if (contains? post-dict :post-title) (html/set-attr :value (:post-title post-dict)) (html/set-attr :unused "unused"))
	[:input.post-id] (if (contains? post-dict :post-id) (html/set-attr :value (:post-id post-dict)) nil)
	[:textarea.post-content] (if (contains? post-dict :post-content) (html/content (:post-content post-dict)) (html/set-attr :unused "unused"))
	[:button.action-submit] (html/set-attr :name (if (contains? post-dict :post-id) "edit-post" "add-post"))
	[:span.delete-button] (if (:should-show-delete post-dict) (html/html-content "<button name=\"delete\" type=\"submit\">Delete</button>") nil))

(defn action-create-post! [session params]
	"Create a post and save it to the database"
	(let [post-id (cbdb/add-post! (:post-title params) (:post-content params))]
		(if post-id (apply str ["Post created. ID: " post-id]) "Couldn't create post!")))

(defn action-edit-post! [session params]
	"Edit an existing post"
	(let [
		new-title (:post-title params)
		new-content (:post-content params)
		post-id (:post-id params)]
		(if (cbdb/edit-post! post-id new-title new-content) "Post edited." "Couldn't edit post!")))

(defn action-create-preview [session params]
	; Stuff to check the session
	(if params (apply str (concat ["Params: "] params)) "No params, for some reason"))

(defn action-delete-post! [session post-id]
	; Stuff to check the session
	(if (cbdb/delete-post! post-id) "Post deleted" "Error deleting post"))

(defn handle-delete [session params]
	(action-delete-post! session (:id params)))

(defn handle-npsubmit [session params]
	"Handle the user pressing the 'preview', 'delete', or 'submit' buttons on the compose page"
	(cond 
		(contains? params :add-post) (action-create-post! session params)
		(contains? params :edit-post) (action-edit-post! session params)
		(contains? params :preview) (action-create-preview session params)
		(contains? params :delete) (action-delete-post! session (:post-id params)) 
		:else (not-found)))

(defn generate-edit-post-composer [post-id title content]
	(reduce str (post-compose {:should-show-delete true, :post-id post-id, :post-title title, :post-content content})))

(defn generate-new-post-composer []
	(reduce str (post-compose {:should-show-delete false})))

(defn generate-post [title date content]
	"Given raw data from the database, generate the HTML for a single post"
	(let [
		date-object (let [date-long (cbutil/parse-integer date)] (if date-long (time-coerce/from-long date-long) nil))
		date-formatter (time-format/formatter "yyyy-MM-dd HH:mm")
		post-map {
			:title title, 
			:date (if date-object (time-format/unparse date-formatter date-object) "(unknown)"), 
			:content content}]
		(reduce str (post-page post-map))))

(defn generate-blog [post-maps]
	"Given raw data from the database, generate the HTML for a page of posts"
	(let [
		map-transform-fn (defn maptfn [raw-map]
			(let [
				date-object (let [date-long (cbutil/parse-integer (:post-date raw-map))] (if date-long (time-coerce/from-long date-long) nil))
				date-formatter (time-format/formatter "yyyy-MM-dd HH:mm")
				post-title (:post-title raw-map)
				post-content (:post-content raw-map)] 
				{
					:title post-title, 
					:date (if date-object (time-format/unparse date-formatter date-object) "(unknown)"),
					:content post-content}))
		] 
		(reduce str (blog-page (map map-transform-fn post-maps)))))

(defn get-post-composer [session post-id]
	(if post-id 
		(let [post-data (cbdb/get-post post-id)]
			(if post-data
				(generate-edit-post-composer post-id (:post-title post-data) (:post-content post-data))
				"Bad post ID"))
		(generate-new-post-composer)))

(defn get-post [post-id] 
	"Given a raw ID, retrieve a post from the database"
	(let [post-data (cbdb/get-post post-id)]
		(if post-data
			(generate-post (:post-title post-data) (:post-date post-data) (:post-content post-data))
			"Couldn't find the specified post")))

(defn get-posts [raw-start raw-post-count]
	"Given a start index and a number of posts, return as many posts as possible"
	(let [
		start (cbutil/parse-integer raw-start)
		post-count (cbutil/parse-integer raw-post-count)
		posts (if (and start post-count) (cbdb/get-posts start post-count) nil)]
		(if posts 
			(do (generate-blog posts))
			"Unable to retrieve any posts")))

; App routes
(defroutes app-routes

  (GET "/" [] 
  	(get-posts 0 posts-per-page))

  (GET ["/test2/:id" :id #"[0-9]+"] 
  	{session :session 
  	 params :params} 
  	{:body (apply str [(:test session) " - session, id:" (:id params)]) 
  	 :session {:test "hello"}})

  (GET "/test3" {session :session, params :params}
    {:body (if (contains? session :next) (apply (:next session) []) (apply str ["No session yet"]))
     :session {:next (apply get-randn-fn [])}}
    )

  ; Blog
  (GET "/blog"
  	{session :session, params :params}
  	(get-posts 0 posts-per-page))

  (GET "/blog/:start/:count" 
  	{session :session, params :params}
  	(get-posts (:start params) (:count params)))

  (GET "/post/:id"
    [id]
  	(get-post id))

  ; Admin panel
  (GET "/admin/edit/post/:id" 
  	{session :session, params :params} 
  	(get-post-composer session (:id params)))

  (GET "/admin/new/post" 
  	{session :session, params :params}
  	(get-post-composer session nil))

  (GET "/admin/delete/post/:id" 
  	{session :session, params :params} 
  	(handle-delete session params))

  (POST "/admin/submit/post"
  	{session :session, params :params}
  	(handle-npsubmit session params))

  (route/resources "/")
  (route/not-found "Not found!"))

(def app
	; Note: since we wrap in handler/site we shouldn't need to manually add session middleware
  (handler/site app-routes))
