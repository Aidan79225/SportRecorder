---
name: release
description: Use when cutting a new release of the SportRecorder Android app — shipping merged changes to Google Play, bumping the version, tagging the release, or producing a signed app bundle (.aab).
---

# Release SportRecorder

## Overview

Cut a Play release: **bump version → build & verify the signed AAB → commit to `master` → annotated tag → push → hand over the AAB + release notes.** Build the bundle *before* committing so a broken build never gets a release tag.

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
