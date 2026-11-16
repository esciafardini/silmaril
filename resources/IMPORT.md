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
5. `ages.edn`: points at First Age, from a batch file.

Re-running is safe: every entity has a unique identity attribute, so existing
entities are updated, not duplicated.

## Schema

Eleven entity types. Each one is identified by which namespace its attributes use.

| Type | Used for |
|---|---|
| `alias` | Another name for a main entry |
| `chapter` | Chapters of the book |
| `character` | Individual beings |
| `place` | Lands, rivers, mountains, cities, realms |
| `age` | Ages (Days Before Days, First Age, Second Age, Third Age) |
| `event` | Battles, named happenings |
| `thing` | Objects, weapons, jewels, ships, stars |
| `animal` | Horses, hounds, eagles |
| `race` | Peoples and kindreds |
| `language` | Spoken languages |
| `house` | Houses and lineages |

Chapter attributes:

- `:chapter/name` (Unique String): Chapter name.
- `:chapter/position` (Long): In reading order, starting at 1 with Ainulindalë.
- `:chapter/start-page` (Long): Helps map glossary page numbers to chapters.
- `:chapter/during` (Ref, Cardinality Many) Points to the age(s) the chapter takes 
  place in. Ainulindalë and Valaquenta have none - #TODO (maybe they should?)

Every glossary type (except `alias` & `chapter`) comprised of:

- `:<type>/name` (Unique String): Glossary headword.
- `:<type>/description` (String): Removes all alias info in favor of `alias` entities. 
- `:<type>/page` (Long, Cardinality Many): Holds the glossary's page refs.
- `:<type>/appears-in` (Ref, Cardinality Many): Points to chapters.

Alias attributes:

- `:alias/key` (Unique String): to track aliases in format `"Anar → Sun"`. 
- `:alias/name` (String): alias as the glossary prints it.
- `:alias/of` (Ref): to the main entry.
- `:alias/description`, `:alias/page` and `:alias/appears-in` hold the
  alias's own glossary text, pages and chapters.

Special attributes:

- `:character/glossary-label` (Unique String): For numbered headwords like
  "Míriel (2)".
- `:house/founder` (Ref): Character a house descends from.
- `:event/during` (Ref): Ages - there is no data for this yet as it isn't 
  specified in the glossary (11-16-2026).
- `:entry/mentions` (Ref, Cardinality Many): Mentions will be added by hand as
  matching names automatically was too unreliable.

## Data shape

Chapter:

```clojure
{:chapter/name       "Of the Darkening of Valinor"
 :chapter/position   10
 :chapter/start-page 63}
```

Glossary entry:

```clojure
{:character/name        "Ungoliant"
 :character/description "The great Spider, ..."
 :character/page        [63 64 66 67 ...]
 :character/appears-in  [[:chapter/name "Of the Darkening of Valinor"]
                         [:chapter/name "Of the Flight of the Noldor"]]}
```

Alias:

```clojure
{:alias/key         "Anar → Sun"
 :alias/name        "Anar"
 :alias/of          [:thing/name "Sun"]
 :alias/description "Quenya name of the Sun."
 :alias/page        [89 90 91]
 :alias/appears-in  [[:chapter/name "Of the Sun and Moon and the Hiding of Valinor"]]}
```
