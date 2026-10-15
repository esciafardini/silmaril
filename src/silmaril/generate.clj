(ns silmaril.generate
  "Builds the transactable data files from the hand-edited glossary source.

  Reads   resources/source/{entries,aliases,cross-references,manual-aliases}.edn
          resources/data/chapters.edn
  Writes  resources/data/batch-NN.edn  (main entries, split into n-files)
          resources/data/aliases.edn   (alias entities)

  Run from the project root:  clojure -M -m silmaril.generate"
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :as str])
  (:import (java.text Normalizer Normalizer$Form)))

(def n-files 12)

(defn read-edn [path] (edn/read-string (slurp path)))

;; ---------------------------------------------------------------- pages → chapters

(def chapters
  "[[start-page name] ...] sorted by start page."
  (->> (read-edn "resources/data/chapters.edn")
       (map (juxt :chapter/start-page :chapter/name))
       (sort-by first)
       vec))

(defn- expand
  "\"209-15\" → 209..215. A shortened end borrows the start's leading digits."
  [ref]
  (let [ref (str/trim (str/replace ref "ff" ""))]
    (if-not (str/includes? ref "-")
      [(parse-long ref)]
      (let [[a b] (str/split ref #"-")
            b     (if (< (count b) (count a)) (str (subs a 0 (- (count a) (count b))) b) b)]
        (range (parse-long a) (inc (parse-long b)))))))

(defn page-list
  "Page refs as printed (\"206, 209-15\") → sorted distinct pages. Pages outside the
  chapters (front matter, Tables) are an error: keep them out of :pages."
  [pages]
  (let [ps (->> (str/split (or pages "") #",")
                (map str/trim)
                (remove str/blank?)
                (mapcat expand)
                distinct
                sort)]
    (doseq [p ps]
      (when (or (< p 3) (>= p 293))
        (throw (ex-info (str "page " p " outside chapters") {:pages pages}))))
    ps))

(defn chapters-for
  "Chapters the pages fall in, in reading order."
  [pages]
  (let [chapter-of (fn [p] (second (last (filter #(<= (first %) p) chapters))))
        order      (into {} (map-indexed (fn [i [_ n]] [n i]) chapters))]
    (sort-by order (distinct (map chapter-of (page-list pages))))))

;; ---------------------------------------------------------------- rendering

(def edn-str pr-str)

(defn- join-pages [& ps] (str/join ", " (remove str/blank? ps)))

(defn- pad-right [s n] (str s (apply str (repeat (- n (count s)) " "))))

(defn- render-entry [{:keys [type name label desc pages founder todo]}]
  (let [t      (clojure.core/name type)
        width  (+ (count t) (count (if label "/glossary-label" "/description")) 2)
        k      #(pad-right (str ":" t "/" %) width)
        pl     (page-list pages)
        chs    (chapters-for pages)
        fields (cond-> [[(k "name") (edn-str name)]]
                 label   (conj [(k "glossary-label") (edn-str label)])
                 desc    (conj [(k "description") (edn-str desc)])
                 ;; nested map, not a lookup ref: the founder may be in this same transaction
                 founder (conj [(k "founder") (str "{:character/name " (edn-str founder) "}")])
                 (seq pl) (conj [(k "page") (str "[" (str/join " " pl) "]")])
                 (seq chs) (conj [(k "appears-in")
                                  (let [pad (apply str (repeat (+ 3 width) " "))]
                                    (str "[" (str/join (str "\n" pad) (map #(str "[:chapter/name " (edn-str %) "]") chs)) "]"))]))]
    (str/join "\n" (cond-> []
                     todo                 (conj (str " ;; TODO " todo))
                     (str/blank? pages)   (conj " ;; no page refs in the glossary")
                     true                 (conj (str " {" (str/join "\n  " (map (partial apply str) fields)) "}"))))))

(defn- render-batch [title entries]
  (str ";; " title "\n[" (subs (str/join "\n\n" (map render-entry entries)) 1) "]\n"))

(defn- render-alias [{:keys [name type to text pages note]}]
  (let [pl     (page-list pages)
        chs    (chapters-for pages)
        fields (cond-> [[":alias/key" (edn-str (str name " → " to))]
                        [":alias/name" (edn-str name)]
                        [":alias/of" (str "[:" (clojure.core/name type) "/name " (edn-str to) "]")]]
                 (seq text) (conj [":alias/description" (edn-str text)])
                 (seq pl)   (conj [":alias/page" (str "[" (str/join " " pl) "]")])
                 (seq chs)  (conj [":alias/appears-in"
                                   (str "[" (str/join (str "\n" (apply str (repeat 20 " ")))
                                                      (map #(str "[:chapter/name " (edn-str %) "]") chs)) "]")]))
        width  (inc (apply max (map (comp count first) fields)))]
    (str (when note (str " ;; " note "\n"))
         " {" (str/join "\n  " (map (fn [[k v]] (str (pad-right k width) v)) fields)) "}")))

(defn- render-aliases [aliases]
  (str ";; Aliases: other names for a main entry, with their own glossary text and page refs.\n"
       ";; Transact after every batch file.\n\n["
       (subs (str/join "\n\n" (map render-alias aliases)) 1) "]\n"))

;; ---------------------------------------------------------------- building

(defn- sort-key
  "Glossary order: accents and case ignored, a leading ‘The’ ignored."
  [s]
  (-> (str/replace s #"^The " "")
      (Normalizer/normalize Normalizer$Form/NFD)
      (str/replace #"[^\x00-\x7F]" "")
      str/lower-case))

(def stub-sort-as
  "Houses file under their founder, as the glossary does (\"House of Bëor\" after Bëor)."
  {"House of Fëanor" "Fëanor "})

(defn- insert-stub
  "Insert after the last entry under the same letter that sorts before it. Entries split
  off a sub-heading (\"Lords of Andúnië\" under A) are out of alphabetical order, so only
  entries filed under the same letter are compared."
  [entries stub]
  (let [k (sort-key (get stub-sort-as (:name stub) (:name stub)))
        i (->> (map-indexed vector entries)
               (filter (fn [[_ e]] (let [ek (sort-key (:name e))]
                                     (and (= (first ek) (first k)) (<= (compare ek k) 0)))))
               last
               first)]
    (into (conj (subvec entries 0 (inc i)) stub) (subvec entries (inc i)))))

(defn- alias-row [{[type to] :of :as a}] (assoc a :type type :to to))

(defn fold-aliases
  "Merge each alias's pages into its main entry. The alias's text stays on the alias
  entity only. A main entry with no headword of its own is built from its aliases."
  [entries aliases]
  (let [index (into {} (map-indexed (fn [i e] [[(:type e) (:name e)] i]) entries))
        merge-in (fn [e {:keys [pages]}] (update e :pages join-pages pages))
        [entries stubs]
        (reduce (fn [[es stubs] {:keys [type to name] :as a}]
                  (if-let [i (index [type to])]
                    [(update es i merge-in a) stubs]
                    [es (-> stubs
                            (update [type to] #(or % {:type type :name to :aliases []}))
                            (update-in [[type to] :aliases] #(if (some #{name} %) % (conj % name)))
                            (update [type to] merge-in a))]))
                [entries (array-map)]
                aliases)
        stubs (for [[_ st] stubs]
                (-> st
                    (assoc :todo (str "no headword in the glossary; pages and text from alias "
                                      (str/join ", " (:aliases st))))
                    (dissoc :aliases)))]
    (doseq [st stubs] (println "main entry with no headword (built from aliases):" (:name st)))
    (reduce insert-stub entries stubs)))

(defn- resolve-target
  "Main entry [type name] for a cross-reference target: an entity name, a glossary label,
  or an alias of one."
  [entries aliases target]
  (let [hits (concat (for [e entries :when (= (:name e) target)] [(:type e) (:name e)])
                     (for [e entries :when (= (:label e) target)] [(:type e) (:name e)]))
        via-alias (last (for [a aliases :when (= (:name a) target)] [(:type a) (:to a)]))]
    (cond
      (= 1 (count hits)) (first hits)
      (seq hits)         (throw (ex-info (str "ambiguous cross-reference target " target) {:hits hits}))
      via-alias          via-alias
      :else              (throw (ex-info (str "unresolved cross-reference target " target) {})))))

(defn- split-evenly [n coll]
  (let [v (vec coll) [q r] [(quot (count v) n) (rem (count v) n)]
        bounds (map #(+ (* % q) (min % r)) (range (inc n)))]
    (map #(subvec v %1 %2) bounds (rest bounds))))

(defn generate! []
  (let [aliases  (map alias-row (read-edn "resources/source/aliases.edn"))
        entries  (fold-aliases (vec (read-edn "resources/source/entries.edn")) aliases)
        _        (doseq [[k n] (frequencies (map (juxt :type :name) entries)) :when (> n 1)]
                   (throw (ex-info (str "duplicate entry " k) {})))
        xrefs    (for [{:keys [name see target]} (read-edn "resources/source/cross-references.edn")
                       :let [[type to] (resolve-target entries aliases (or target see))]]
                   {:name name :type type :to to})
        manual   (for [a (read-edn "resources/source/manual-aliases.edn")]
                   (assoc (alias-row a) :note "added by hand; not a glossary headword"))
        all      (concat aliases xrefs manual)
        data-dir (io/file "resources/data")]
    (doseq [{:keys [name to]} all :when (= name to)]
      (throw (ex-info (str "alias names its own main entry: " name) {})))
    (doseq [[k n] (frequencies (map (juxt :name :to) all)) :when (> n 1)]
      (throw (ex-info (str "duplicate alias " k) {})))
    (doseq [f (.listFiles data-dir) :when (re-matches #"batch-\d+\.edn" (.getName f))]
      (io/delete-file f))
    (let [chunks (split-evenly n-files entries)]
      (doseq [[i chunk] (map-indexed vector chunks)]
        (spit (io/file data-dir (format "batch-%02d.edn" (inc i)))
              (render-batch (format "Glossary batch %02d of %d (%s – %s)"
                                    (inc i) (count chunks) (:name (first chunk)) (:name (last chunk)))
                            chunk)))
      (println (count entries) "entries in" (count chunks) "batch files:" (mapv count chunks)))
    (spit (io/file data-dir "aliases.edn") (render-aliases all))
    (println (count aliases) "aliases +" (count xrefs) "cross-references +" (count manual) "by hand")))

(defn -main [& _] (generate!))
