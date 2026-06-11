# GitHub Actions CI Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a GitHub Actions CI workflow that runs build + unit tests + detekt + Android Lint on every PR to `master` (and push to `master`), and make the Linux `gradlew` executable so the runner can invoke it.

**Architecture:** One `.github/workflows/ci.yml`, single `ubuntu-latest` job: JDK 21 → Gradle cache → Android SDK (platform 36 / build-tools 36) → `./gradlew assembleDebug testDebugUnitTest :app:detekt :app:lintDebug`, uploading reports on failure. The tracked `gradlew` git mode is fixed to executable.

**Tech Stack:** GitHub Actions, Gradle 9.4.1 (KTS + version catalog), AGP 9.2, JDK 21, detekt (no baseline) + Android Lint (baseline).

**Verification model:** The gate IS the live CI run — open the PR and confirm the `CI` workflow goes **green**. A canary (deliberate failure) confirms it actually blocks.

**Environment:** Local commands run from `C:\Users\Aidan\SportRecorder` (PowerShell or the Bash tool for git). `gh` is authenticated as `Aidan79225`. The CI itself runs on GitHub's Linux runners (not locally).

**Branch:** `feat/ci-github-actions` (spec already committed here).

---

## Task 1: Add the CI workflow + fix `gradlew` mode

**Files:**
- Create: `.github/workflows/ci.yml`
- Mode change: `gradlew` (git mode `100644` → `100755`)

- [ ] **Step 1: Create `.github/workflows/ci.yml`**

```yaml
name: CI

on:
  pull_request:
    branches: [master]
  push:
    branches: [master]
  workflow_dispatch:

concurrency:
  group: ci-${{ github.ref }}
  cancel-in-progress: true

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - name: Checkout
        uses: actions/checkout@v4

      - name: Set up JDK 21
        uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '21'

      - name: Set up Gradle
        uses: gradle/actions/setup-gradle@v4

      - name: Set up Android SDK
        uses: android-actions/setup-android@v3

      - name: Install required SDK packages
        run: sdkmanager "platforms;android-36" "build-tools;36.0.0"

      - name: Make gradlew executable
        run: chmod +x ./gradlew

      - name: Build, unit tests, detekt, lint
        run: ./gradlew assembleDebug testDebugUnitTest :app:detekt :app:lintDebug --stacktrace

      - name: Upload reports on failure
        if: failure()
        uses: actions/upload-artifact@v4
        with:
          name: reports
          path: |
            app/build/reports/
            app/build/test-results/
          if-no-files-found: ignore
```

- [ ] **Step 2: Fix the tracked `gradlew` executable bit** (so any Linux runner can execute it, independent of the workflow's `chmod`):

```
git update-index --chmod=+x gradlew
```
Verify: `git ls-files -s gradlew` → mode should now read `100755`.

- [ ] **Step 3: Commit** (the workflow + the mode change):

```
git add .github/workflows/ci.yml
git commit -m "[CI] Add GitHub Actions: build + unit tests + detekt + lint on PR"
```
(The `update-index --chmod` change is already staged, so it rides in this commit. Use the trailer `Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>`.)

- [ ] **Step 4: Push the branch and open the PR** (the `pull_request` trigger starts the workflow):

```
git push -u origin feat/ci-github-actions
gh pr create --base master --head feat/ci-github-actions --title "Add GitHub Actions CI (build + tests + detekt + lint)" --body "Adds .github/workflows/ci.yml gating every PR with assembleDebug + testDebugUnitTest + :app:detekt + :app:lintDebug, and fixes the gradlew executable bit for Linux runners."
```

- [ ] **Step 5: Watch the run go green**

```
gh run list --branch feat/ci-github-actions --limit 1
gh run watch <run-id>     # or: gh run watch (picks the latest)
```
Expected: the `CI` workflow's `build` job succeeds (assembleDebug + testDebugUnitTest + :app:detekt + :app:lintDebug all pass — matching local). If it fails:
  - **SDK license error** → add a step before "Install required SDK packages": `run: yes | sdkmanager --licenses || true`.
  - **`gradlew: Permission denied`** → confirm Step 2 applied (mode `100755`); the workflow's `chmod` is a backstop.
  - **Missing platform/build-tools** → confirm the `sdkmanager "platforms;android-36" "build-tools;36.0.0"` step ran; bump versions if AGP requires different ones (read the error).
  - **detekt/lint failure** → that means a real finding; it should match local `:app:detekt`/`:app:lintDebug` (both currently green), so investigate the diff. Fix, commit, push; the workflow re-runs.
  Iterate (edit `.github/workflows/ci.yml`, commit, push) until the run is green. `git status` clean at the end.

---

## Task 2: Prove the gate blocks (canary), then revert

**Files:** none committed (temporary canary only).

- [ ] **Step 1: Push a deliberate failure on a throwaway branch** to confirm CI turns the check red. From `feat/ci-github-actions`, add a canary detekt violation to any Kotlin file, e.g. append to `app/src/main/java/com/crazystudio/sportrecorder/util/StringUtils.kt`:

```kotlin
fun ciCanary(): Int { val x=12345 ; return x }
```
Commit + push:
```
git add app/src/main/java/com/crazystudio/sportrecorder/util/StringUtils.kt
git commit -m "[tmp] CI canary - expect red"
git push
```

- [ ] **Step 2: Confirm the CI run FAILS** — `gh run watch` → the `CI` workflow fails at the "Build, unit tests, detekt, lint" step (detekt `MagicNumber`/formatting). This proves a new issue blocks the PR.

- [ ] **Step 3: Revert the canary** so the PR is clean and green again:

```
git revert --no-edit HEAD
git push
```
(Or `git reset --hard HEAD~1` + force-push if you prefer no revert commit; revert is cleaner on a shared PR.) Confirm the next `CI` run is **green**. `git status` clean.

---

## Task 3: Post-merge reminder (repo setting — not a file change)

- [ ] **Step 1:** After the PR merges, in **GitHub → Settings → Branches → Branch protection rules → `master`**, add a rule requiring the **`build` status check** (the `CI` workflow's job) to pass before merging. This makes red PRs un-mergeable. (This is a one-time repo setting done by the maintainer; nothing to commit. Surface it in the final summary / PR description.)

---

## Self-Review (author check vs. spec)

- **Spec coverage:** workflow file + triggers + concurrency → Task 1 Step 1; JDK 21 / setup-gradle / setup-android / SDK 36 / chmod / the four gate tasks / upload-on-failure → Task 1 Step 1; `gradlew` mode fix → Task 1 Step 2; green-run verification → Task 1 Step 5; reverse-check (canary blocks) → Task 2; required-status-check follow-up → Task 3. All spec sections map to tasks.
- **Placeholder scan:** complete `ci.yml`, exact `git`/`gh`/`gradlew` commands, and concrete failure-mode remedies. No vague steps.
- **Consistency:** the gate command `./gradlew assembleDebug testDebugUnitTest :app:detekt :app:lintDebug` is identical between the workflow (Step 1) and the local gates we've been running; the job name `build` referenced in Task 3 matches the workflow's `jobs.build`.
- **Note:** Task 2's canary is on the same feature branch (the open PR), so it exercises the real `pull_request` gate; it's reverted before merge so it never lands on `master`.
