# silmaril

Silmarillion glossary in a local Datomic db (`.datomic/local-storage`). See
`resources/IMPORT.md` for the data model.

## Wipe and re-import the db

If you edited anything in `resources/source/`, regenerate first:

```sh
clojure -M -m silmaril.generate
```

From the REPL (use this while an nREPL is running; it owns the storage):

```clojure
(require '[silmaril.load :as l] '[datomic.client.api :as d])
(d/delete-database l/client {:db-name "silmarillion"})
(l/load-all! (l/conn))
```

Then re-create any connection you hold, e.g. `(def c (l/conn))` in `repl_sandbox`.

From the shell (stop the REPL first):

```sh
rm -rf .datomic/local-storage/silmarillion
clojure -M -m silmaril.load
```
