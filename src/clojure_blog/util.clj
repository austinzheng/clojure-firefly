(ns clojure-blog.util)

(defn parse-integer [raw]
  (if (integer? raw) raw 
    (try (Long/parseLong raw)
      (catch Exception e nil))))

(defn map-function-on-map-keys [m func]
  "Take a map and return another map, with the keys transformed by some function"
  (reduce (fn [new-map [k v]] (assoc new-map (apply func [k]) v)) {} m))

(defn nonempty? [string]
  (and 
    (not (= string nil)) 
    (> (count string) 0)))
