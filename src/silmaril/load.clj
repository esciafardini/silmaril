(ns silmaril.load
  "Loads schema, chapters, and glossary batches into the local Datomic db.
  Run from the project root:  clojure -M -m silmaril.load"
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [datomic.client.api :as d]))

;; Storage lives in this repo (.datomic/ is gitignored), not in ~/.datomic/local.edn.
(def client (d/client {:server-type :datomic-local
                       :system      "silmarillion"
                       :storage-dir (.getAbsolutePath (io/file ".datomic/local-storage"))}))

(defn conn []
  (d/create-database client {:db-name "silmarillion"})
  (d/connect client {:db-name "silmarillion"}))

(defn read-edn [path]
  (edn/read-string (slurp path)))

(defn batch-files
  "resources/data/batch-*.edn, in name order."
  []
  (->> (.listFiles (io/file "resources/data"))
       (map #(.getPath %))
       (filter #(re-matches #".*/batch-\d+\.edn" %))
       sort))

(defn load-all!
  "Transacts schema, then chapters, then every batch file, then aliases, then ages.
  Safe to re-run: names are unique, so existing entities are updated."
  [conn]
  (doseq [path (concat ["resources/schema.edn"
                        "resources/data/chapters.edn"]
                       (batch-files)
                       ;; last: aliases point at entities from any batch
                       ["resources/data/aliases.edn"
                        ;; hand-written: extra ages and :chapter/during
                        "resources/data/ages.edn"])]
    (println "transacting" path)
    (d/transact conn {:tx-data (read-edn path)})))

(defn -main [& _]
  (let [c (conn)]
    (load-all! c)
    (println "chapters: " (ffirst (d/q '[:find (count ?c) :where [?c :chapter/name]] (d/db c)))))
  (shutdown-agents))
