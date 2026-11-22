(ns user
  (:require [datomic.client.api :as d]
            [shadow.cljs.devtools.api :as shadow]
            [shadow.cljs.devtools.server :as shadow-server]
            [silmaril.clj.load :as l]
            [silmaril.clj.model :as model]
            [silmaril.clj.server :as silmaril-server]))

(defn go
  "Starts shadow-cljs, watches :app, and starts the Ring server on :8888."
  []
  (shadow-server/start!)
  (shadow/watch :app)
  (silmaril-server/start!))

(defn halt []
  (silmaril-server/stop!)
  (shadow-server/stop!))

(comment
  ;; Start / Stop shadow-cljs app & silmaril server
  (go)
  (halt))

(defn delete-db!
  "Deletes the silmarillion database. `c` is stale until `reinstate-db!` runs."
  []
  (d/delete-database l/client {:db-name "silmarillion"}))

(defn reinstate-db!
  "Creates the database, loads schema + all data, and rebinds `c` to the new connection."
  []
  (let [conn (l/conn)]
    (l/load-all! conn)
    (alter-var-root #'model/c (constantly conn))))

(comment
  ;; Datomic reset
  (delete-db!)
  (reinstate-db!))
