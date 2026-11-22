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
  (-> (d/q '{:find  [?chapter]
             :in    [$ ?pos]
             :where [[?chapter :chapter/position ?pos]]}
           (d/db c) chap-num)
      ffirst))

(defn get-chapter-entity [chap-num]
  (d/pull (d/db c) '[*] [:chapter/position chap-num]))

;; Find all entries for a chapter

;; get min page within chapter that entity is mentioned
;; i.e. Thingol is mentioned 10 times in chapter 4 - return the min page number (for sorting)

;; Find all entries for a page

;; Attach all aliases to an entity

;; Testing Testing
(comment
  (get-chapter-id 3)
  (get-chapter-entity 3)
  (get-all-chapters))
