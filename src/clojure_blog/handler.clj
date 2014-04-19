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
    [clj-time.coerce :as time-coerce]
    [clojure.string :as string]))

; (def server1-conn {:pool {} :spec {}})
; (defmacro wcar* [& body] `(car/wcar server1-conn ~@body))

(defn not-found [] "404: not found")

; Definition for post
(html/deftemplate post-page "post.html" 
	[post-dict]
	[:title] (html/content [(:title post-dict) " HOME PAGE"])
	[:h1] (html/content (:title post-dict))
	[:span.date] (html/content (:date post-dict))
	[:div.content] (html/html-content (:content post-dict)))

; Definition for compose post page
(html/deftemplate post-compose "new_post.html"
	[post-dict])


; Get new post
(defn get-composer
	[session params]
	(post-compose {}))

(defn action-create-post
	"Create a post and save it to the database"
	[session params]
	(let [post-id (cbdb/add-post! (:post-title params) (:post-body params))]
		(if post-id (apply str ["Post created. ID: " post-id]) "Couldn't create post!")))

; Preview a post
(defn action-create-preview
	[session params]
	; Stuff to check the session
	(if params (apply str (concat ["Params: "] params)) "No params, for some reason"))

; Handle the user pressing the 'preview' or 'submit' buttons
(defn handle-npsubmit
	[session params]
	(cond 
		(contains? params :submit) (action-create-post session params)
		(contains? params :preview) (action-create-preview session params)
		:else "test"))

; Some test stuff
; Return a function that itself returns a pre-determined random number
(defn get-randn-fn []
	(let [rand-number (rand 1)]
		(fn [] (str rand-number))))

(defn generate-post [title date content]
	"Given raw data from the database, generate the HTML for a single post"
	(let [
		date-object (time-coerce/from-long (Long/parseLong date))
		date-formatter (time-format/formatter "yyyy-MM-dd HH:mm")
		post-map {:title title, :date (time-format/unparse date-formatter date-object), :content content}]
		; (println (apply str ["Content: " content]))
		(reduce str (post-page post-map))))

; Given a raw post ID, get the corresponding post
; (defn get-post [raw_id] 
; 	(let [id (cbutil/parse-int raw_id)] 
; 		(if (= nil id)
; 			{:status 500
; 			 :body "Server has encountered an internal error" }
; 			(string/join ["You requested post " id]))))
(defn get-post [raw_id] 
	"Given a raw ID, retrieve a post from the database"
	(let [post-data (cbdb/get-post raw_id)]
		(if post-data 
			; (do (println post-data) (apply str post-data))
			(generate-post (:post-title post-data) (:post-date post-data) (:post-body post-data))
			"Couldn't find the specified post")))

(defn get-test [] 
	(let [sample-post {:title "Test", 
					   :date "1/2", 
					   :content "Hello world, this is my <a href=\"http://www.google.com\">sample blog entry</a>.<br />
		                        <pre>[LICrosslink sharedInstance].enabled = YES;\nBOOL test = NO;</pre>"}]
		(reduce str (post-page sample-post))))

(defn session-handler [req] (assoc req :session "MAI SESSION :3"))

; Test stuff - DB
; (defn get-from-db [dkey]
; 	(let [dval (wcar* (car/get dkey))]
; 		(if (= dval nil)
; 			"key not found"
; 			(apply str ["value for key " dkey " is " dval]))))

; (defn set-in-db! [dkey dval]
; 	(if (or (not dkey) (not dval)) false
; 		(do (wcar* (car/set dkey dval))
; 			(wcar* (car/bgsave))
; 			true)))

; App routes
(defroutes app-routes
  (GET "/" [] "Hello World")

  (GET ["/test2/:id" :id #"[0-9]+"] 
  	{session :session 
  	 params :params} 
  	{:body (apply str [(:test session) " - session, id:" (:id params)]) 
  	 :session {:test "hello"}})

  ; (GET "/dbadd/:dkey/:dval" 
  ; 	[dkey, dval] 
  ; 	(if (set-in-db! dkey dval) 
  ; 		(apply str ["Set value '" dval "' for key '" dkey "'."]) 
  ; 		"Unable to assoc key with value"))
  ; (GET "/dbget/:dkey" [dkey] (get-from-db dkey))

  (GET "/test3" {session :session, params :params}
    {:body (if (contains? session :next) (apply (:next session) []) (apply str ["No session yet"]))
     :session {:next (apply get-randn-fn [])}}
    )
  (GET "/test" [] (apply get-test []))

  (GET "/post/:id"
    [id]
  	(get-post id))

  ; Admin panel
  (GET "/admin/newpost" 
  	{session :session, params :params}
  	(get-composer session params))

  (POST "/admin/npsubmit"
  	{session :session, params :params}
  	(handle-npsubmit session params))

  (route/resources "/")
  (route/not-found "Not found!"))

(def app
	; Note: since we wrap in handler/site we shouldn't need to manually add session middleware
  (handler/site app-routes))
