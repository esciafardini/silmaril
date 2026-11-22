# Silmaril

Interactive Silmarillion Glossary in a Datomic DB (`.datomic/local-storage`).

Follows the 2022 Tolkien Illustrated Edition (ISBN: 9780063280779)

## Run the app (server + shadow-cljs)

Everything runs in one JVM.

Run in terminal from project root:
```sh
clojure -M:dev        # starts nREPL
```
The `user.clj` namespace has all the tools you need to get the application up and running.

Start the app in REPL:
```clojure
(comment
  ;; Start / Stop shadow-cljs app & silmaril server
  (go)
  (halt))
```

Clear and re-instate the datomic database in REPL:
```clojure
(comment
  ;; Datomic reset
  (delete-db!)
  (reinstate-db!))
```

I will likely keep this as a project that runs locally because I don't feel like paying for another domain name
&& in my opinion, this functions perfectly as a locally running application.

## LLM Usage Disclosure
The aim of this project is to learn Datomic/Datalog & build a personal tool for reading the Silmarillion while keeping LLM usage at a minimum.

I used Claude to scan the glossary pages of the Silmarillion and convert each entry into EDN & to build the initial project boilerplate.

Personally, I love the development experience of Clojure, so writing code by hand and evaluating it in the REPL has been a joyous reunion for me.

I have been using a local copy (the website seems to have been shut down) of `Learn Datalog Today` to get a grip on Datalog syntax.
source: https://github.com/jonase/learndatalogtoday
