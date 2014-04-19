(ns clojure-blog.util)

(defn parse-int [raw]
	(try (Integer/parseInt raw)
		(catch Exception e nil)))

(defn map-function-on-map-keys [m func]
	(reduce (fn [new-map [k v]] (assoc new-map (apply func [k]) v)) {} m))
