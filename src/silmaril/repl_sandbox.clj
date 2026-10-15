(ns silmaril.repl-sandbox
  (:require [silmaril.load :as l]
            [datomic.client.api :as d]))

(def c (l/conn))

(defn query [query & [processing-fn]]
  (let [process (or processing-fn identity)]
    (process (d/q query (d/db c)))))

;; chapters in order
(query
 '[:find ?pos ?chapter ?page
   :where
   [?ch :chapter/position ?pos]
   [?ch :chapter/name ?chapter]
   [?ch :chapter/start-page ?page]]
 (comp vec #(sort-by first %)))

(->> [:character/name "Thingol"]
     (d/pull (d/db (l/conn)) '[* {:character/appears-in [:chapter/name]}])
     :character/description)

(query '[:find ?type ?main
         :where [?a :alias/name "Mirkwood"]
         [?a :alias/of ?e]
         [?e ?attr ?main]
         [?attr :db/ident ?id]
         [(name ?id) ?n] [(= ?n "name")]
         [(namespace ?id) ?type]])

;; all chapter names
(query
 '[:find ?name
   :where [_ :chapter/name ?name]]
 set)

;; figure out which chapter a page is in:
(defn page->chapter [page-number]
  (query
   '[:find ?position ?name ?page-start
     :where
     [?ch :chapter/position ?position]
     [?ch :chapter/name ?name]
     [?ch :chapter/start-page ?page-start]]
   (fn [results]
     (->> results
          (sort-by last)
          (remove (fn [[_ _ start-page]] (> start-page page-number)))
          last))))

(page->chapter 92)
