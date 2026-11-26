(ns silmaril.clj.model
  (:require [silmaril.clj.load :as l]
            [datomic.client.api :as d]))

(def c (l/conn))

(defn query
  "to: the destination, can be pre-populated with data (defaults to empty vector)
   xform: optional transducer supplied (applies to every incoming datum)
   *Note: I can't think of a use for pre-populated `to` data but I like it as a concept."
  [query & {:keys [args to xform]
            :or {xform (map identity)
                 to []}}]
  (into to xform (apply d/q query (d/db c) args)))

(def chapter-pattern
  [:db/id
   :chapter/position
   :chapter/name
   :chapter/start-page
   {:chapter/during [:entry/name]}])

;; chapters in order
(defn get-all-chapters []
  (query '{:find [(pull ?c pattern)]
           :in [$ pattern]
           :where [[?c :chapter/position]]}
         {:args [chapter-pattern]
          :xform (map first)}))

(defn get-chapter-id [chap-num]
  (-> (d/q '{:find [?chapter]
             :in [$ ?pos]
             :where [[?chapter :chapter/position ?pos]]}
           (d/db c) chap-num)
      ffirst))

(defn get-chapter-entity [chap-num]
  (d/pull (d/db c) '[*] [:chapter/position chap-num]))

(defn find-entries-for-chapter [chapter-number]
  (query '{:find [?name ?description]
           :in [$ ?chap-num]
           :where [[?e :entry/name ?name]
                   [?e :entry/description ?description]
                   [?e :entry/appears-in ?c]
                   [?c :chapter/position ?chap-num]]}
         {:args [chapter-number]
          :to {}}))

;; Find all entries for a page
(defn find-entries-for-page [page-number]
  (query '{:find [?name ?description]
           :in [$ ?page-num]
           :where [[?e :entry/name ?name]
                   [?e :entry/description ?description]
                   [?e :entry/page ?page-num]]}
         {:args [page-number]
          :to {}}))

;; Attach all aliases to an entity


;; Testing Testing
(comment
  (find-entries-for-chapter 3)
  (find-entries-for-page 195)
  (get-chapter-id 1)
  (get-chapter-entity 1)
  (get-all-chapters))
