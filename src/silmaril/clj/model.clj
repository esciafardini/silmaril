(ns silmaril.clj.model
  (:require [silmaril.clj.load :as l]
            [datomic.client.api :as d]))

(def c (l/conn))

(defn query
  "to: the destination, can be pre-populated with data (defaults to empty vector)
   xform: optional transducer supplied (applies to every incoming datum)
   *Note: Placing context maps at index 0 may prove useful..."
  [query & {:keys [args to xform process-fn]
            :or {xform identity
                 process-fn identity
                 to []}}]
  (process-fn (into to xform (apply d/q query (d/db c) args))))

(def chapter-pattern
  [:chapter/position
   :chapter/name
   :chapter/start-page
   :chapter/last-page])

;; xform fn
(defn pages-in-chapter
  "Assoc the set of pages in chapter (for later comparison)"
  [{:chapter/keys [start-page last-page] :as chapter}]
  (assoc chapter :chapter/pages (vec (range start-page (inc last-page)))))

;; chapters in order
(defn get-all-chapters []
  (query '{:find [(pull ?c pattern)]
           :in [$ pattern]
           :where [[?c :chapter/position]]}
         {:args [chapter-pattern]
          :xform (map first)}))

(defn get-chapter [chap-num]
  (query '{:find [(pull ?c pattern)]
           :in [$ pattern ?chap-num]
           :where [[?c :chapter/position ?chap-num]]}
         {:args [chapter-pattern chap-num]
          :xform (comp (map first)
                       (map pages-in-chapter))
          :to {}}))

(def rules
  '[[(name-and-description ?e ?name ?description)
     [?e :entry/name ?name]
     [?e :entry/description ?description]]
    ;; ?e is the entry whose main name or alias is ?lookup
    [(entry-by-name ?e ?lookup)
     [?e :entry/name ?lookup]]
    [(entry-by-name ?e ?lookup)
     [?a :alias/name ?lookup]
     [?a :alias/of ?e]]
    ;; for sorting on chapter view (show in order of appearance)
    [(lowest-page-in-chapter ?e ?c ?p)
     [?e :entry/page ?p]
     [?c :chapter/start-page ?start]
     [?c :chapter/last-page ?end]
     [(<= ?start ?p)]
     [(<= ?p ?end)]]
    ;; get the "type" from pre-defined entry types
    [(entry-type ?e ?type)
     [?e :entry/type ?type-id]
     [?type-id :db/ident ?type]]
    ;; aliases for main entry
    [(aliases ?name ?alias)
     [?e :entry/name ?name]
     [?a :alias/of ?e]
     [?a :alias/name ?alias]]
    ;; aliases of aliases
    [(aliases ?name ?alias)
     [?b :alias/name ?name]
     [?b :alias/of ?e]
     [?a :alias/of ?e]
     [?a :alias/name ?alias]
     [(not= ?alias ?name)]]
    ;; include main entry for aliases of alias
    [(aliases ?name ?alias)
     [?b :alias/name ?name]
     [?b :alias/of ?e]
     [?e :entry/name ?alias]]])

(defn find-other-names
  "Find all aliases for an entry - works for aliases as well - hence `other names`"
  [entry-name]
  (query '{:find [?alias]
           :in [$ % ?name]
           :where [(aliases ?name ?alias)]}
         {:args [rules entry-name]
          :xform (map first)
          :to #{}}))

;;xform fn
(defn build-entry-map [[entry-name desc entry-type & [page]]]
  (let [aliases (find-other-names entry-name)]
    (cond-> {:name entry-name
             :description desc
             :entry-type (name entry-type)}
      (seq aliases)
      (assoc :aliases aliases)
      page
      (assoc :lowest-page page))))

(defn find-entries-for-chapter [chapter-number]
  (query '{:find [?name ?description ?entry-type (min ?page)]
           :in [$ % ?chap-num]
           :where [(entry-type ?e ?entry-type)
                   (name-and-description ?e ?name ?description)
                   (lowest-page-in-chapter ?e ?c ?page)
                   [?e :entry/appears-in ?c]
                   [?c :chapter/position ?chap-num]]}
         {:args [rules chapter-number]
          :to [{:context? true
                :chapter chapter-number}]
          :xform (map build-entry-map)
          :process-fn #(sort-by :lowest-page %)}))

(defn find-entries-for-page [page-number]
  (query '{:find [?name ?description ?entry-type]
           :in [$ % ?page-num]
           :where [(entry-type ?e ?entry-type)
                   (name-and-description ?e ?name ?description)
                   [?e :entry/page ?page-num]]}
         {:args [rules page-number]
          :to [{:context? true
                :page page-number}]
          :xform (comp (map build-entry-map))
          :process-fn #(sort-by :name %)}))

;; find a single entry from name
(defn find-entry [entry-name]
  (query '{:find [?name ?description ?entry-type]
           :in [$ % ?lookup]
           :where [(entry-by-name ?e ?lookup)
                   (entry-type ?e ?entry-type)
                   (name-and-description ?e ?name ?description)]}
         {:args [rules entry-name]
          :to {}
          :xform (comp (map build-entry-map))}))

;; Testing Testing
(comment
  (find-other-names "Melkor")
  (find-other-names "Morgoth")
  (find-entry "Morgoth")
  (find-entry "Tirion")
  (find-entries-for-chapter 1)
  (find-entries-for-page 4)
  (get-chapter 9)
  (get-all-chapters))
