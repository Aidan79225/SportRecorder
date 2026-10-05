# 店家 · Venue on a record design

- **Date:** 2026-10-05
- **Status:** Implemented on `claude/venue-83`. Issue [#83](https://github.com/Aidan79225/SportRecorder/issues/83).
- **Related:** [#84](https://github.com/Aidan79225/SportRecorder/issues/84) (paste a Google Maps URL
  → fill the venue and its coordinates) builds on this. [#85](https://github.com/Aidan79225/SportRecorder/issues/85)
  (food tags) is independent — see *Schema versioning* for why the two no longer need to ship together.

## Goal

A record stores `time / location(lat,lng) / note / photos`. 「在哪裡吃的」only fits as **coordinates**:
the Insights map can draw "you have been to these points" but cannot say **which place**. A venue name
today can only go in the free-text `note`, so it is retyped every time and can never be counted,
grouped or corrected.

What this design buys is not "a venue can be recorded" — it is **never retyping one** and
**being able to look back by place**.

## The decision that shapes everything: a venue is an entity

A venue is its own row with its own coordinates, not a string on the record.

The cheaper option (a `venue` string column, picker from `SELECT DISTINCT`) delivers the picker and
the name on the map, and was recommended. The owner chose the entity because of what only an entity
gives:

- **Renaming propagates.** Fix a typo once and every past record follows.
- **A venue owns a position**, which can be corrected later — and correcting it fixes the whole
  history at once (see *Coordinates*).
- `#84` needs somewhere to put the coordinates parsed out of a Google Maps URL.

The cost is paid in full now: a new table, a migration, a DAO, repository surface, and backup shape.

## Two different facts, not two copies of one

This is the heart of the design, and the thing most likely to be undone by someone later:

| | means | captured | can be fixed later |
|---|---|---|---|
| `EatRecord.location` (`lat`/`lng`) | **where you were** | only at the moment of the meal | no — it is gone if not captured |
| `Venue.lat`/`lng` | **where the food came from** | any time | yes, and retroactively |

Takeaway eaten at home is "at home + that restaurant". Both are true; neither overwrites the other.

**`EatRecord.location` keeps exactly today's meaning.** An earlier draft had the venue's coordinates
replace the record's when a venue was chosen; that was rejected because it silently turns "where I
was" into "where the food is from" — the record stops being a record of the moment.

## Data model

New entity (Room `version = 7` → `8`, `exportSchema` JSON committed as always):

```
venue
  id          INTEGER PK autoincrement   -- local only; never travels (see Backup)
  name        TEXT NOT NULL UNIQUE       -- normalized: trimmed, runs of whitespace collapsed,
                                         -- uniqueness compared case-insensitively
  lat         REAL NULL                  -- a venue need not know where it is
  lng         REAL NULL
  lastUsedAt  INTEGER NOT NULL           -- drives picker order
```

`eat_time` gains `venueId INTEGER NULL` referencing `venue(id)`. Domain: `EatRecord.venue: Venue?`.

**In the database** the record points at the venue by id, never by a copied name — that is what makes
a rename propagate, and nothing about a venue is denormalized onto `eat_time`. The backup document is
the one place that travels by name instead, for the reason given under *Backup*.

## Coordinates

- Creating a venue while the record has a GPS fix: the venue **adopts that fix** as its initial
  position. Otherwise it starts with none, and `#84` or a later visit fills it in.
- A venue's position can be corrected, and because records read through `venueId`, **the correction
  applies to every past record at once**. This is the entity's main pay-off.
- Choosing a venue never changes the record's own `lat`/`lng`.

## Map

`InsightsAggregator` currently groups records by rounded coordinates (`LOCATION_ROUNDING`). It gains
one rule, applied per record:

1. The record has a venue **with** coordinates → a named marker for that venue.
2. Otherwise → today's behaviour, an anonymous point at the record's own coordinates.

**No record disappears from the map.** Dropping venue-less records would quietly tell anyone who
cooks at home that their meals do not count.

This is pure logic and is unit-tested in `commonTest` alongside the existing aggregator tests.

## Rename is also merge; there is no delete

Renaming happens from the picker. If the new name is one that **already exists**, the two venues
**merge**: the records of the renamed venue re-point to the surviving one, and the emptied venue is
removed.

That makes merge the cleanup tool, so v1 ships **no delete** — unused venues simply sink in a
recency-ordered picker.

Merging moves records that the user is not looking at, so the confirmation **says how many records
will move**. Silent history edits are the one thing this feature must not do.

## Picker

Ordered by `lastUsedAt`, most recent first: eating has strong recency, so the few places someone is
going through a phase with float to the top on their own. No location permission, no guessing.

**One suggestion on top of that order:** when editing a record whose `note` contains the name of a
known venue (plain substring match, after normalization), that venue is offered first.

This is deliberately *not* an inference. It was considered and rejected to have an LLM read old notes
and guess venue names: in-app it would mean sending a personal food diary to a third party, which
contradicts the app's posture of talking to nothing but the user's own Drive; and any guess that
writes itself into a record is rewriting the user's own memory. A substring match against names the
user themself created guesses nothing, needs no network and no dependency, and improves as venues
accumulate.

**No automatic migration of existing notes.** Old records keep their text exactly as written; filling
in a venue is the user's to do, one tap at a time, helped by the suggestion above.

## Backup

`BackupDocument` gains a `venues` array (name + coordinates), and each `BackupMeal` carries its
**venue name** — not its id.

Ids are not preserved by a restore: `replaceAll` deletes everything and re-inserts, letting Room
assign new ids and re-parenting photos to them. A name is the only key stable across installs, so
restore creates the venues first and attaches meals by name — no id-mapping table, and a meal still
finds its venue even if the `venues` array is missing.

### Schema versioning: `SCHEMA_VERSION` is **not** bumped

`BackupJson` is configured `ignoreUnknownKeys = true`, and `restore` rejects a snapshot only when
`doc.schemaVersion > SCHEMA_VERSION` — i.e. only newer-into-older.

| | leave at 1 | bump to 2 |
|---|---|---|
| old app ← new snapshot | ignores `venues`/venue name, **restores everything else** | **rejects the whole snapshot** |
| new app ← old snapshot | missing fields take defaults | fine |

Bumping would be strictly worse for users: an old install that merely cannot understand a venue name
would refuse to restore a year of meals. A bump is for changes that are genuinely incompatible — a
field changing meaning, or old data restoring *wrongly*. Adding an optional field is neither.

(This corrects issues #83 and #85, which both assumed a bump was required and therefore that the two
features should ship together to pay for it once. They are independent.)

## Testing

Pure logic in `commonMain`, tested in `commonTest` (so it runs on JVM **and** iOS):

- name normalization: trimmed, whitespace runs collapsed; uniqueness is **case-insensitive**
  (`Starbucks` and `starbucks` are one venue) while the **display keeps what the user typed**
- picker ordering by recency, and the note-substring suggestion taking precedence
- rename→merge: records re-pointed, emptied venue gone, affected count correct
- the map rule: venue-with-coordinates → named marker; everything else → its own point

Plus, following the existing local-only `androidTest` precedent: the Room 7→8 migration, and a backup
round-trip including **an old (venue-less) snapshot restoring into the new app**.

## 初衷對照 / North-Star check

- ✅ **記錄要快、要輕鬆** — picking beats typing, and gets faster with use. A venue is always optional;
  the one-tap quick record (#75) is untouched.
- ✅ **服務覺察** — 「我常在哪裡吃」is a plain fact about yourself, which is exactly what the Insights
  page exists to reflect.
- ⚠️ **Drift risk — collection mechanics.** A venue list is one small step from check-ins, badges and
  「你去過 N 家店」. There is no count of places visited anywhere in this design, and there should not be:
  it is part of a record, not a game.
- ⚠️ **Drift risk — silent history edits.** Rename-as-merge moves records the user is not looking at.
  The confirmation must say how many. A feature that quietly rewrites someone's diary has stopped
  being a diary.
- ⚠️ **Drift risk — inference.** The rejected LLM-guessing option would have had the app decide what
  the user meant. Suggestion, never substitution.

## Out of scope

Google Maps URL parsing (#84), food tags (#85), an export feature, and any backfill of existing notes.
