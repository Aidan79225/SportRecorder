# Google Drive Backup — Design

- **Date:** 2026-06-23
- **Status:** Approved (design); pending implementation plan
- **Scope:** Android v1 (the app currently ships Android only; iOS host app does not exist yet)

## Goal

Let a user sign in with Google and **back up their data to their own Google Drive**, then
**restore it** after reinstalling or switching phones. This is recovery, not multi-device sync.

Serves the North Star — *"陪你留住每一個美好的當下"* (preserve the moments): the user never
loses their records.

## Non-goals (v1)

- No continuous / multi-device live sync, no merge or conflict resolution.
- No automatic / scheduled background backup — manual only.
- No backup-reminder nagging (see 初衷對照).
- No iOS implementation yet (kept reachable behind interfaces; not built).

## Decisions

| Decision | Choice |
|---|---|
| Model | **Recovery snapshot** (restore overwrites local data) |
| Trigger | **Manual** — "Back up now" / "Restore" buttons |
| Photos | **Included**, uploaded **incrementally** (immutable webp, dedup by filename) |
| Cloud location | Google Drive **appDataFolder** (hidden, app-private) |
| Scope | `https://www.googleapis.com/auth/drive.appdata` (narrowest) |
| Snapshot format | **Structured JSON export** (`BackupDocument`) + photo files — *not* a raw DB-file copy |
| Retention | **Keep the last 3 snapshots** so an accidental empty backup can't destroy the only good one |

## Architecture & KMP boundary

Backup *logic* lives in `commonMain` (reuses the repositories, like the rest of the migrated
stack); only the Google-specific pieces are platform `actual`s.

**commonMain**
- `BackupDocument` — versioned `@Serializable` snapshot of the DB-backed data: `schemaVersion`,
  `createdAt`, `appVersionName`, `meals[]` (each referencing its photo **filenames**, not bytes),
  `fastingTypes[]`, `dietSettings`, `reminderPrefs`. Photos are **not** embedded in the JSON.
- `BackupService` — orchestrator. Reads/writes the domain via the existing repositories,
  (de)serializes `BackupDocument`, and drives a `BackupStore`. Pure Kotlin + coroutines.
- `BackupStore` (interface) — cloud boundary: `listSnapshots()`, `uploadSnapshot(json, photoFileNames)`,
  `downloadSnapshot(id)`, `prune(keepLast)`. No Google specifics.
- `BackupAuth` (interface) — sign-in state (`Flow<Account?>`) + supplies the Drive access token.

**:app (Android `actual`s)**
- `GoogleBackupAuth` — Google sign-in + `drive.appdata` authorization → access token.
- `GoogleDriveBackupStore` — Drive v3 REST against appDataFolder; reads/writes local photo files
  via the existing `PhotoStorage`.

**UI / DI** — a Backup section in Settings (account state · Back up now · Restore · "last backed
up" · progress). Koin wires the Android impls behind the interfaces.

> Forward-looking (not v1): the Drive REST calls could later move into `commonMain` (Ktor +
> a platform-supplied token) so iOS reuses them via an iOS `BackupAuth` actual.

## Data surface backed up

- Room DB `database-name` (meals = `EatTime` + `Photo` rows, fasting types) — exported as JSON,
  **not** as a raw file copy.
- DataStore `diet_preference` (diet settings, reminder prefs) — exported into the same document.
- Photo files: `getExternalFilesDir/photos/*.webp` — uploaded as files alongside the manifest.

## Backup flow (manual)

1. Settings → **Back up now**. If not signed in → Google sign-in (`drive.appdata`) first.
2. `BackupService.backup()`:
   - Build `BackupDocument` from the repos → JSON.
   - Upload referenced **photos first**, skipping any already present in appDataFolder
     (incremental; photos are immutable, dedup by filename).
   - Upload **`manifest.json` last** — the *commit marker*. A snapshot whose manifest is
     missing/incomplete is invalid and cleaned up.
   - **Prune** to the last N snapshots; drop orphan photos no kept snapshot references.
   - Record "last backed up at". Progress UI + cancel.

## Restore flow

1. Settings → **Restore** → sign in → list snapshots (date · size · app version) → pick →
   **confirm**: "This replaces the data on this device."
2. `BackupService.restore(snapshot)`:
   - Download `manifest.json` → parse → **validate `schemaVersion`** (refuse if newer than the app
     understands → "update the app to restore this backup").
   - Download all referenced photos to a **staging area**.
   - **Only after everything is downloaded & valid**: in one transaction, clear local data + insert
     from the manifest, then move staged photos into the photos dir.
   - Re-arm reminders via `RescheduleRemindersUseCase` (meal times changed).

## Safety principles

- **Manifest-last commit** → no half-written snapshot is ever considered valid.
- **Download-all-then-swap** → a failed/cancelled restore leaves current local data untouched.
- **Keep last N** → an accidental empty/cleared backup can't destroy the only good one.
- Restore is always explicit and confirmed.

## Auth (Android v1)

- Sign-in via Google Identity Services; **authorization** for Drive is separate — request only
  `drive.appdata` → access token for Drive REST. The app sees only its own hidden folder.
- Fetch the access token on demand per session, refresh on expiry, **don't persist tokens**;
  persist only the chosen account (to show "signed in as …" and silently re-authorize). Sign-out clears it.
- New deps (the app's first network stack): `play-services-auth` (authorization client) + a thin
  Drive v3 REST client over OkHttp (`files.list/create/get/delete` on appDataFolder).
- **Ops prerequisite:** configure + **verify the OAuth consent screen**. `drive.appdata` is a
  *sensitive* scope → needs Google verification (uses the existing privacy-policy URL) but **not**
  the CASA assessment that *restricted* Drive scopes require. Budget lead time before production.
- Add the `INTERNET` permission (currently absent).

## Error handling (all → friendly message, never a half-state)

- No network → message, no changes.
- Not signed in / token expired / access revoked → re-prompt sign-in.
- Drive quota full → "Your Google Drive is full."
- Upload cancelled/interrupted → snapshot uncommitted (manifest-last), ignored & cleaned next run.
- Restore download fails → abort; local data untouched.
- `schemaVersion` newer than app → refuse with an update prompt.
- Corrupt/incomplete manifest → snapshot shown as invalid, skipped.
- User denies Drive authorization → explain, stay local-only.

## Testing

- `commonMain` (`BackupService` with a fake `BackupStore` + existing fake repos):
  export builds a correct `BackupDocument`; **export → restore round-trip** is identical;
  incremental photo skip; prune-to-N (keeps newest, drops orphan photos); `schemaVersion` guard;
  corrupt-manifest handling; simulated mid-restore failure leaves local data intact.
- `BackupDocument` JSON round-trip + a **golden fixture** to catch schema drift.
- The Google `actual`s stay thin → manual/instrumented testing against a real Drive account
  (Google SDKs don't unit-test well); most logic lives in the tested `commonMain` service.

## 初衷對照 / North-Star check (required by CLAUDE.md)

This is the app's first data egress, so it matters. Data (incl. photos) goes to the **user's own
Google Drive**, app-private hidden folder, narrowest scope, **opt-in + manual** — the developer
collects/receives **nothing** (no app servers). The app stays fully usable local-only; sign-in is
never required.

- Serves *"留住每一個美好的當下"* → strongly mission-aligned.
- Red flags: 記錄變負擔? No (optional, doesn't touch capture). 評價使用者? No. 外在壓力 / 排行 /
  streak? No. 語氣? Gentle.
- **Drift to avoid:** no guilt-y "you haven't backed up in N days!" nagging — if a backup reminder
  is ever added, keep it gentle/optional and **not in v1**.

## Docs to update (not code)

- Privacy policy (lives in the separate blog repo, **not** this repo) — disclose the optional
  Google Drive backup, the `drive.appdata` scope, what's stored, and that it's user-controlled.
- Play Console data-safety form — still "no data shared with the developer"; disclose the optional
  backup to the user's own Drive.

## Future / out of scope

- iOS implementation (`BackupAuth`/`BackupStore` actuals; possibly a shared Ktor Drive client).
- Automatic / scheduled backup (gentle, optional) — only after v1.
