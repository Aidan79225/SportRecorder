---
name: release
description: Use when cutting a new release of the SportRecorder Android app — shipping merged changes to Google Play, bumping the version, tagging the release, or producing a signed app bundle (.aab).
---

# Release SportRecorder

## Overview

Cut a Play release: **bump version → build & verify the signed AAB → commit to `master` → annotated tag → push → hand over the AAB + release notes.** Build the bundle *before* committing so a broken build never gets a release tag.

## Automated upload (GitHub Action)

Pushing the version tag also **builds and uploads to Google Play** via `.github/workflows/release.yml` (trigger: tags matching `[0-9]+.[0-9]+.[0-9]+`, or manual `workflow_dispatch` to pick the track/status). It runs `bundleRelease` then `r0adkll/upload-google-play` (the Gradle Play Publisher plugin is **not** usable here — it requires `BaseAppModuleExtension`, which AGP 9 removed).

So the per-release manual work is just: **bump the version → edit `distribution/whatsnew/whatsnew-en-US` + `whatsnew-zh-TW` → commit → tag → push the tag.** The Action does the build + upload.

- **Default track:** `internal`, `status=completed`. For production, run the workflow manually with `track=production` and **`status=draft`** so nothing auto-goes-live without a click in the Console.
- **One-time setup (required before it works):** add repo secret **`PLAY_SERVICE_ACCOUNT_JSON`** — the full JSON key of a Google Cloud service account granted access in Play Console → Users & permissions. Signing needs no secret (the keystore + passwords are in the repo).
- **Still manual in the Console (no API):** the `SCHEDULE_EXACT_ALARM` use-case declaration and the data-safety form.
- Keep `whatsnew-*` files ≤ 500 chars, user-facing, both locales.

The steps below remain the **local fallback** (and the source of truth for version-bump rules) when you want to build/inspect the AAB by hand or the Action isn't available.

## When to use

- Shipping merged changes to Google Play / preparing a store submission.
- You need a signed `.aab` (Android App Bundle) to upload.
- Bumping the app version + tagging a release.

## Steps

1. **Pick the version.** Read the current values in `app/build.gradle.kts` (`versionCode = N`, `versionName = "X.Y.Z"`).
   - `versionCode` → **always +1** (Play rejects a re-used code).
   - `versionName` → minor bump (`0.X.0`) for user-facing features, patch (`0.0.Z`) for fixes only. If unsure which, ask the user.

2. **Edit `app/build.gradle.kts`** — set the new `versionCode` and `versionName`.

3. **Build the signed AAB** (this proves it compiles AND signs before anything is committed). From repo root in **PowerShell**:
   ```powershell
   $env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
   .\gradlew.bat bundleRelease
   ```
   Output: `app/build/outputs/bundle/release/app-release.aab` (signed by the `release` signingConfig; `release.keystore` is committed, so no extra setup). Use a generous timeout (~600000 ms).

4. **Verify the embedded version** matches what you set (a wrong/duplicate `versionCode` is the #1 Play-rejection cause):
   ```bash
   grep -oE 'versionCode="[0-9]+"|versionName="[^"]+"' \
     app/build/intermediates/bundle_manifest/release/*/AndroidManifest.xml | head -2
   ```

5. **Commit directly to `master`** (matches the prior releases `0.1.0`/`0.2.0`, which are first-parent commits on `master`, not PRs):
   ```bash
   git add app/build.gradle.kts
   git commit -m "[Release] X.Y.Z (versionCode N)"
   ```
   End the message with the trailer `Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>`.

6. **Annotated tag:**
   ```bash
   git tag -a X.Y.Z -m "SportRecorder X.Y.Z (versionCode N)"
   ```

7. **Push master + the tag:**
   ```bash
   git push origin master
   git push origin X.Y.Z
   ```

8. **Hand over the artifact + notes.** Send the AAB to the user with SendUserFile, and draft **user-facing** release notes (zh-rTW primary + en-US default) — describe what users see, not internal refactors. Data safety form is "no collection" (local-only, no network).

## Quick reference

| What | Value |
|---|---|
| Version file | `app/build.gradle.kts` → `versionCode` / `versionName` |
| Build AAB | `.\gradlew.bat bundleRelease` (set `JAVA_HOME` to Android Studio JBR first) |
| AAB path | `app/build/outputs/bundle/release/app-release.aab` |
| Commit | `[Release] X.Y.Z (versionCode N)` — direct to `master` |
| Tag | annotated `X.Y.Z` |
| Signing | `release` signingConfig (keystore committed) — no setup needed |

## Common mistakes

- **Forgetting `JAVA_HOME`** → gradle can't find a JDK. Set it before *every* `gradlew.bat` call (java is not on PATH on this machine).
- **`assembleRelease` (APK) instead of `bundleRelease` (AAB)** → Play wants the AAB.
- **Not bumping `versionCode`** → Play rejects the upload as a duplicate.
- **Bumping the version but forgetting to commit `app/build.gradle.kts`** before tagging → the tag points at the old version.
- **Putting internal refactors in the store "what's new"** → those notes are for users; keep them user-facing.
- Release builds currently have **R8/minify OFF** (`isMinifyEnabled = false`), so the AAB is unminified — that's expected unless that's changed separately.
