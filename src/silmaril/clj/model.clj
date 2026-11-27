(ns silmaril.clj.model
  (:require [silmaril.clj.load :as l]
            [datomic.client.api :as d]))

(def c (l/conn))

(defn query
  "to: the destination, can be pre-populated with data (defaults to empty vector)
   xform: optional transducer supplied (applies to every incoming datum)
   *Note: Placing context maps at index 0 may prove useful..."
  [query & {:keys [args to xform]
            :or {xform identity
                 to []}}]
  (into to xform (apply d/q query (d/db c) args)))

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
    ;; Get lowest page mention in chapter (for sorting)
    [(lowest-page ?e ?type)
     [?e :entry/type ?type-id]
     [?type-id :db/ident ?type]]
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
(defn build-entry-map [[entry-name desc entry-type]]
  (let [aliases (find-other-names entry-name)]
    (cond-> {:name entry-name
             :description desc
             :entry-type (name entry-type)}
      (seq aliases)
      (assoc :aliases aliases))))

(defn find-entries-for-chapter [chapter-number]
  (query '{:find [?name ?description ?entry-type]
           :in [$ % ?chap-num]
           :where [(entry-type ?e ?entry-type)
                   (name-and-description ?e ?name ?description)
                   [?e :entry/appears-in ?c]
                   [?c :chapter/position ?chap-num]]}
         {:args [rules chapter-number]
          :to [{:context? true
                :chapter chapter-number}]
          :xform (map build-entry-map)}))

(defn find-entries-for-page [page-number]
  (->> (query '{:find [?name ?description ?entry-type]
                :in [$ % ?page-num]
                :where [(entry-type ?e ?entry-type)
                        (name-and-description ?e ?name ?description)
                        [?e :entry/page ?page-num]]}
              {:args [rules page-number]
               :to [{:context? true
                     :page page-number}]
               :xform (comp (map build-entry-map))})
       (sort-by :name)))

;; Testing Testing
(comment
  (find-other-names "Melkor")
  (find-other-names "Morgoth")
  (find-entries-for-chapter 1)
  (find-entries-for-page 4)
  (get-chapter 9)
  (get-all-chapters))
