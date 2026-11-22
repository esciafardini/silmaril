# Glossary import

## Files

| File | Contents |
|---|---|
| `resources/schema.edn` | Attribute definitions. |
| `resources/data/chapters.edn` | One entity per chapter. |
| `resources/data/batch-NN.edn` | Glossary entries (from index pages 304–345) in order, split across 12 files. |
| `resources/data/aliases.edn` | Glossary aliases: "See X" or "Nickname for Y". |
| `resources/data/ages.edn` | Ages that have no glossary entry (Days Before Days, Second Age, Third Age). |
| `src/silmaril/load.clj` | Loads `resources` data into local Datomic db: `clojure -M -m silmaril.load` |

## Load order

`load.clj` transacts the files in this order:
1. `schema.edn`
2. `chapters.edn`: everything else points at chapters.
3. `batch-01.edn` … `batch-12.edn`
4. `aliases.edn`: an alias can point at a main entry from any batch.
5. `ages.edn`: points at the First Age entry, from a batch file.

Re-running is safe: every entity has a unique identity attribute, so existing
entities are updated, not duplicated.

## Schema

Three kinds of entity: chapters, entries and aliases.

- **Chapter**: a chapter of the book.
- **Entry**: a glossary headword. Its kind is set by `:entry/type`.
- **Alias**: another name for an entry.

Entry types (`:entry/type`, a ref to one of these idents):

| `:entry/type` | Used for |
|---|---|
| `:entry.type/character` | Individual beings |
| `:entry.type/place` | Lands, rivers, mountains, cities, realms |
| `:entry.type/age` | Ages (Days Before Days, First Age, Second Age, Third Age, ...) |
| `:entry.type/event` | Battles, named happenings |
| `:entry.type/thing` | Objects, weapons, jewels, ships, stars |
| `:entry.type/animal` | Horses, hounds, eagles |
| `:entry.type/race` | Peoples and kindreds |
| `:entry.type/house` | Houses and lineages |
| `:entry.type/language` | Spoken languages |

Chapter attributes:

- `:chapter/name` (Unique String): Chapter name.
- `:chapter/position` (Long): In reading order, starting at 1 with Ainulindalë.
- `:chapter/start-page` (Long): Helps map glossary page numbers to chapters.
- `:chapter/during` (Ref, Cardinality Many): Points to the age entries the chapter
  takes place in. Ainulindalë and Valaquenta have none - #TODO (maybe they should?)

Entry attributes:

- `:entry/name` (Unique String): Glossary headword, or a fuller name for
  numbered headwords ("Celeborn (tree)").
- `:entry/type` (Ref): One of the `:entry.type/*` idents above.
- `:entry/glossary-label` (Unique String): The glossary's numbered label, e.g.
  "Míriel (2)". Only on numbered headwords.
- `:entry/description` (String): Glossary text, with alias info moved to `alias`
  entities.
- `:entry/page` (Long, Cardinality Many): Holds the glossary's page refs.
- `:entry/appears-in` (Ref, Cardinality Many): Points to chapters.
- `:entry/mentions` (Ref, Cardinality Many): Entries this description mentions.
  Mentions will be added by hand as matching names automatically was too
  unreliable.

Type-specific attributes (they sit on entries, but only make sense for one type):

- `:house/founder` (Ref): Character entry a house descends from.
- `:event/during` (Ref, Cardinality Many): Age entries - there is no data for
  this yet as it isn't specified in the glossary (11-16-2026).

Alias attributes:

- `:alias/key` (Unique String): to track aliases in format `"Anar → Sun"`.
- `:alias/name` (String): alias as the glossary prints it.
- `:alias/of` (Ref): to the main entry.
- `:alias/description`, `:alias/page` and `:alias/appears-in` hold the
  alias's own glossary text, pages and chapters.

## Data shape

Chapter:

```clojure
{:chapter/name       "Of the Darkening of Valinor"
 :chapter/position   10
 :chapter/start-page 63}
```

Entry:

```clojure
{:entry/name        "Ungoliant"
 :entry/type        :entry.type/character
 :entry/description "The great Spider, ..."
 :entry/page        [63 64 66 67 ...]
 :entry/appears-in  [[:chapter/name "Of the Darkening of Valinor"]
                     [:chapter/name "Of the Flight of the Noldor"]]}
```

Alias:

```clojure
{:alias/key         "Anar → Sun"
 :alias/name        "Anar"
 :alias/of          [:entry/name "Sun"]
 :alias/description "Quenya name of the Sun."
 :alias/page        [89 90 91]
 :alias/appears-in  [[:chapter/name "Of the Sun and Moon and the Hiding of Valinor"]]}
```

## Queries

Every entry in a chapter:

```clojure
[:find ?name ?type
 :where [?ch :chapter/name "Of the Darkening of Valinor"]
        [?e :entry/appears-in ?ch]
        [?e :entry/name ?name]
        [?e :entry/type ?t]
        [?t :db/ident ?type]]
```

Every entry on a page: replace the chapter clauses with `[?e :entry/page 92]`.
