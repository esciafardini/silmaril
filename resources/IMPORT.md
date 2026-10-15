# Glossary import

## Files

| File | Contents |
|---|---|
| `resources/schema.edn` | Attribute definitions. |
| `resources/data/chapters.edn` | One entity per chapter. Hand-written. |
| `resources/data/batch-NN.edn` | Glossary entries (index pages 304–345) in index order, split evenly across 12 files. Generated from `resources/source/`. |
| `resources/data/aliases.edn` | Other names for main entries: glossary aliases, "see X" cross-references, and aliases added by hand. Generated. |
| `resources/data/ages.edn` | Ages that have no glossary headword (Days Before Days, Second Age, Third Age), and which age each chapter takes place in. Hand-written. |
| `resources/source/entries.edn` | Main glossary entries, in index order. Hand-edited source. |
| `resources/source/aliases.edn` | Aliases with their own glossary text and pages. Hand-edited source. |
| `resources/source/cross-references.edn` | "See X" headwords. Hand-edited source. |
| `resources/source/manual-aliases.edn` | Aliases that are not glossary headwords. Hand-edited source. |
| `src/silmaril/generate.clj` | Builds the batch files and `aliases.edn` from `resources/source/`. Edit the source files, not the generated ones, then run `clojure -M -m silmaril.generate`. |
| `src/silmaril/load.clj` | Loads everything into the local Datomic db: `clojure -M -m silmaril.load` |

## Load order

`load.clj` transacts the files in this order. Each step needs the ones before it.

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
| `chapter` | Chapters of the book |
| `character` | Individual beings: Elves, Men, Valar, Maiar, Dwarves, monsters |
| `place` | Lands, rivers, mountains, cities, realms |
| `age` | Ages (Days Before Days, First Age, Second Age, Third Age). First Age overlaps with Years of Trees/Sun |
| `event` | Battles, named happenings |
| `thing` | Objects, weapons, jewels, ships, stars |
| `animal` | Horses, hounds, eagles |
| `race` | Peoples and kindreds: Noldor, Edain, Teleri |
| `language` | Quenya, Sindarin, Adûnaic, ... |
| `house` | Houses and lineages: House of Bëor, House of Hador |
| `alias` | Another name for a main entry: Anar for the Sun |

Chapter attributes:

- `:chapter/name` is a string and unique. You can look a chapter up by it.
- `:chapter/position` is a long. It is reading order, starting at 1 with Ainulindalë.
- `:chapter/start-page` is a long. It maps glossary page numbers to chapters.
- `:chapter/during` is a ref with cardinality many. It points to the age(s)
  the chapter takes place in. Ainulindalë and Valaquenta have none.

Every glossary type (`character` through `house`) has these attributes:

- `:<type>/name` is a string and unique. It is the glossary headword.
- `:<type>/description` is a string. It is the glossary text, with every
  other name for the entry removed ("also called _X_", "see _X_" pointing
  at an alias, quoted names like ‘Rivendell’). Those names are alias
  entities instead.
  Words the glossary prints in italics are wrapped in underscores
  (`see _Isengard_`): cross-reference names, titles, and words cited as
  words.
- `:<type>/page` is a long with cardinality many. It holds the glossary's
  page refs, with ranges expanded.
- `:<type>/appears-in` is a ref with cardinality many. It points to the
  chapters those pages fall in. It means "the glossary cites this name in
  that chapter", not "this takes place in that chapter".

Other attributes:

- `:character/glossary-label` is a string and unique, only on numbered
  headwords ("Míriel (2)").
- `:house/founder` is a ref to the character a house descends from.
- `:event/during` is a ref to ages. It has no data yet.
- `:entry/mentions` is a ref with cardinality many, usable on any type. It
  points to entities the description mentions. Mentions are added by hand;
  matching names automatically was too unreliable, because the glossary
  rarely italicises names of other entries.

Alias attributes:

- `:alias/key` is a string and unique: `"<alias name> → <main entry name>"`,
  e.g. `"Anar → Sun"`. It exists so re-running the load updates aliases
  instead of duplicating them; a name alone is not unique ("The Dark Lord"
  has one alias for Melkor and one for Sauron).
- `:alias/name` is the alias as the glossary prints it.
- `:alias/of` is a ref to the main entry.
- `:alias/description`, `:alias/page` and `:alias/appears-in` hold the
  alias's own glossary text, pages and chapters. Cross-references and hand
  aliases have none.

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

`[:chapter/name "..."]` is a lookup ref. It means "the entity whose
`:chapter/name` is this string". It only works for entities transacted
earlier, not ones created in the same transaction; those are written as
nested maps instead (`{:age/name "Second Age"}`, `:house/founder`).

## Page → chapter mapping

A page `p` belongs to the chapter with the largest `start-page` that is
still `<= p`. Several page refs in one chapter collapse into one link.

Refs below page 3 (front matter) or at/after page 293 (Tables) map to no
chapter; they are dropped, and the entry's TODO notes them.

## Rules for the glossary pass

- Each headword is stored once, as a single name string.
- If a headword repeats a name that already exists in the same type, it is
  not created.
- If an entry is hard to classify, I pick a type and add a `TODO` comment
  in the batch file. TODOs also record where page refs came from, and mark
  the few descriptions that are not glossary text (Arminas).
- A sub-heading inside an entry ("Sons of Bór 183", "The Siege of Angband
  105–6, ...") that is another name for the parent, or part of it, has its
  page refs merged into the parent ("Edain" → Atani, "Bay of Belfalas" →
  Belfalas). A sub-heading that names a different kind of thing becomes its
  own entity with those page refs, and the parent keeps only its own refs:
  "House of Bëor" and "Lords of Andúnië" are `house`, "Siege of Angband" is
  `event`. A house links to its founder with `:house/founder` when the
  glossary names one. Split entities have no description, since the
  glossary gives none.
- An alias (another name for a main entry, with its own text and page
  refs) becomes an `alias` entity pointing at the main entry. Its page refs
  are also merged into the main entry. Its text is stored only on the alias
  (`:alias/description`), not copied into the main entry's description.

  When one being has several names, one of them is chosen as the true
  name (the main entry) and all the others are aliases of it, including
  aliases of aliases. If no headword exists for the main entry (First Age,
  House of Fëanor, Moon, Sun), the main entry is built from its aliases'
  pages, has no description, and carries a TODO saying so.
- A cross-reference ("Mirkwood: see Greenwood the Great") becomes an
  `alias` entity with no text or pages.
  If the target is itself an alias or a numbered label, the alias points
  at the main entry it resolves to ("Mount Doom: see Amon Amarth" →
  Orodruin). When the glossary names more than one target, one is chosen
  by hand with `:target` in `cross-references.edn`. A cross-reference that is not another name for
  its target (Arminas → Gelmir (2): a different Elf) is handled case by
  case.
- Aliases that are not glossary headwords ("Isle of Númenor") are added by
  hand in `manual-aliases.edn`. Most are names that were removed from a
  main entry's description; epithets become "<Name> the <Epithet>"
  ("Elendil the Tall"). They are marked with a comment in
  `aliases.edn`.
- Numbered headwords ("Míriel (2)") are separate entities sharing a name.
  The name gets a fuller name where one exists ("Tar-Míriel"), otherwise
  the name plus a short descriptive word in place of the number
  ("Celeborn (tree)", "Celeborn (elf)", "Eärendur (king of Arnor)").
  `:character/glossary-label` stores the glossary's label exactly, so
  "See Míriel (2)" resolves via `[:character/glossary-label "Míriel (2)"]`.
  Other types have no label attribute; their label goes in a TODO comment.
  The name can be changed later if a better one turns up.
