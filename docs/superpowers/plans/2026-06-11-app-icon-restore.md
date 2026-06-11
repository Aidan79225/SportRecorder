# App Icon Restore Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Restore the original branded launcher icon (green "S" on dark `#2B2B2B`) that commit `d04fa9f` overwrote with the default Android template, and add green-"S" raster fallbacks for API < 26.

**Architecture:** `git checkout a9306d3 --` restores the adaptive-icon XMLs, the green-"S" foreground vector, and the 512px Play Store PNG; the default green-grid drawable and default raster `.webp` mipmaps are removed; PNG raster mipmaps are regenerated from the restored 512px PNG with PowerShell `System.Drawing`.

**Tech Stack:** Android adaptive icons (`mipmap-anydpi-v26`), vector drawables, PowerShell `System.Drawing` (image downscale/circular-mask).

**Verification model:** `.\gradlew.bat assembleDebug` green (no duplicate-resource errors), then install + screenshot the launcher/app-drawer showing the green "S" (not the green robot).

**Environment (project memory):** `java` not on PATH — before every Gradle command: `$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"` then `.\gradlew.bat <tasks>` from `C:\Users\Aidan\SportRecorder`. Emulator `Pixel_10_Pro` (API 37); adb at `C:\Users\Aidan\AppData\Local\Android\Sdk\platform-tools\adb.exe`; screenshots to `%TEMP%` (screencap + adb pull, never `> file`). **Commit hygiene:** stage only named paths (never `git add -A`); commit trailer `Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>`.

**Branch:** `feat/restore-app-icon` (spec already committed here).

**Key facts (from git archaeology):**
- Last good custom icon = commit `a9306d3`: adaptive icon with `<background android:drawable="@color/ic_launcher_background"/>` (`#2B2B2B`) + `<foreground android:drawable="@drawable/ic_launcher_foreground"/>` (green "S" `#38D69F`); 512px `ic_launcher-playstore.png` (green S); **no raster `.webp` mipmaps existed** (adaptive-only).
- `d04fa9f` broke it: foreground → Android robot; adaptive background → new `@drawable/ic_launcher_background` (green grid); added default `mipmap-*/ic_launcher.webp` + `_round.webp`; default Play Store PNG.

---

## Task 1: Restore icon resources from `a9306d3`; remove the default extras

**Files:**
- Restore (from `a9306d3`): `app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml`, `app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml`, `app/src/main/res/drawable/ic_launcher_foreground.xml`, `app/src/main/ic_launcher-playstore.png`
- Delete: `app/src/main/res/drawable/ic_launcher_background.xml` (green grid, now unused)
- Delete: the 10 default raster mipmaps `app/src/main/res/mipmap-{mdpi,hdpi,xhdpi,xxhdpi,xxxhdpi}/ic_launcher.webp` and `ic_launcher_round.webp`

- [ ] **Step 1: Restore the four custom-icon files from `a9306d3`**

```
git checkout a9306d3 -- app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml app/src/main/res/drawable/ic_launcher_foreground.xml app/src/main/ic_launcher-playstore.png
```
After this, `mipmap-anydpi-v26/ic_launcher.xml` should reference `@color/ic_launcher_background` for its background and `@drawable/ic_launcher_foreground` for its foreground (verify by reading it).

- [ ] **Step 2: Delete the now-unused green-grid drawable and the default raster mipmaps**

```
git rm app/src/main/res/drawable/ic_launcher_background.xml
git rm app/src/main/res/mipmap-mdpi/ic_launcher.webp app/src/main/res/mipmap-mdpi/ic_launcher_round.webp app/src/main/res/mipmap-hdpi/ic_launcher.webp app/src/main/res/mipmap-hdpi/ic_launcher_round.webp app/src/main/res/mipmap-xhdpi/ic_launcher.webp app/src/main/res/mipmap-xhdpi/ic_launcher_round.webp app/src/main/res/mipmap-xxhdpi/ic_launcher.webp app/src/main/res/mipmap-xxhdpi/ic_launcher_round.webp app/src/main/res/mipmap-xxxhdpi/ic_launcher.webp app/src/main/res/mipmap-xxxhdpi/ic_launcher_round.webp
```

- [ ] **Step 3: Confirm `@color/ic_launcher_background` still exists** — read `app/src/main/res/values/ic_launcher_background.xml`; it must contain `<color name="ic_launcher_background">#2B2B2B</color>` (it is unchanged since `a9306d3`; do not edit).

- [ ] **Step 4: Build (adaptive-only at this point)**

Run:
```
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat assembleDebug
```
Expected: `BUILD SUCCESSFUL`, no duplicate-resource error and no "resource ... not found" for `@color/ic_launcher_background` or `@drawable/ic_launcher_foreground`. (The original shipped adaptive-only, so a green build here is expected.)

- [ ] **Step 5: Commit**

```
git add app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml app/src/main/res/drawable/ic_launcher_foreground.xml app/src/main/ic_launcher-playstore.png
git commit -m "[Fix] Restore original green-S app icon; drop default-template extras"
```
(The `git rm`'d files are already staged for deletion and included in this commit.)

---

## Task 2: Regenerate API <26 raster fallbacks from the restored 512px PNG

**Files:**
- Create: `app/src/main/res/mipmap-{mdpi,hdpi,xhdpi,xxhdpi,xxxhdpi}/ic_launcher.png` and `ic_launcher_round.png`

- [ ] **Step 1: Generate PNG mipmaps (square + circular) from the restored Play Store PNG**

Run this PowerShell (uses `System.Drawing`; the source is the green-S 512px PNG restored in Task 1):
```powershell
Add-Type -AssemblyName System.Drawing
$root = "C:\Users\Aidan\SportRecorder\app\src\main\res"
$src  = "C:\Users\Aidan\SportRecorder\app\src\main\ic_launcher-playstore.png"
$img  = [System.Drawing.Image]::FromFile($src)
$sizes = [ordered]@{ "mdpi"=48; "hdpi"=72; "xhdpi"=96; "xxhdpi"=144; "xxxhdpi"=192 }
foreach ($d in $sizes.Keys) {
    $px = [int]$sizes[$d]
    $dir = Join-Path $root "mipmap-$d"
    if (-not (Test-Path $dir)) { New-Item -ItemType Directory -Path $dir | Out-Null }
    # square ic_launcher.png
    $bmp = New-Object System.Drawing.Bitmap $px, $px
    $g = [System.Drawing.Graphics]::FromImage($bmp)
    $g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
    $g.DrawImage($img, 0, 0, $px, $px)
    $bmp.Save((Join-Path $dir "ic_launcher.png"), [System.Drawing.Imaging.ImageFormat]::Png)
    $g.Dispose(); $bmp.Dispose()
    # circular ic_launcher_round.png
    $bmp2 = New-Object System.Drawing.Bitmap $px, $px
    $g2 = [System.Drawing.Graphics]::FromImage($bmp2)
    $g2.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
    $g2.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
    $path = New-Object System.Drawing.Drawing2D.GraphicsPath
    $path.AddEllipse(0, 0, $px, $px)
    $g2.SetClip($path)
    $g2.DrawImage($img, 0, 0, $px, $px)
    $bmp2.Save((Join-Path $dir "ic_launcher_round.png"), [System.Drawing.Imaging.ImageFormat]::Png)
    $g2.Dispose(); $bmp2.Dispose()
}
$img.Dispose()
Write-Output "done"
```
Expected: 10 PNG files created (5 densities × {square, round}). There must be NO `.webp` of the same name left in those dirs (Task 1 deleted them) — otherwise it's a duplicate-resource error.

- [ ] **Step 2: Build**

Run:
```
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat assembleDebug
```
Expected: `BUILD SUCCESSFUL`, no duplicate-resource error (`mipmap/ic_launcher`), no aapt PNG errors.

- [ ] **Step 3: Install + screenshot the launcher icon**

```
.\gradlew.bat installDebug
$adb = "C:\Users\Aidan\AppData\Local\Android\Sdk\platform-tools\adb.exe"
& $adb shell input keyevent KEYCODE_HOME
& $adb shell monkey -p com.crazystudio.sportrecorder -c android.intent.category.LAUNCHER 0   # ensures launcher knows the app; optional
# Open the app drawer / launcher and screenshot:
& $adb shell input keyevent KEYCODE_HOME
& $adb shell screencap -p /sdcard/sr_icon.png
& $adb pull /sdcard/sr_icon.png C:\Users\Aidan\AppData\Local\Temp\sr_icon.png
& $adb shell rm /sdcard/sr_icon.png
```
Expected: the SportRecorder icon is a **green "S" on a dark background** (NOT the green Android robot). If the launcher still shows the old icon from cache, uninstall + reinstall: `& $adb uninstall com.crazystudio.sportrecorder` then `.\gradlew.bat installDebug`, and re-check. (The coordinator/reviewer should view `%TEMP%\sr_icon.png` to confirm.)

- [ ] **Step 4: Commit**

```
git add app/src/main/res/mipmap-mdpi/ic_launcher.png app/src/main/res/mipmap-mdpi/ic_launcher_round.png app/src/main/res/mipmap-hdpi/ic_launcher.png app/src/main/res/mipmap-hdpi/ic_launcher_round.png app/src/main/res/mipmap-xhdpi/ic_launcher.png app/src/main/res/mipmap-xhdpi/ic_launcher_round.png app/src/main/res/mipmap-xxhdpi/ic_launcher.png app/src/main/res/mipmap-xxhdpi/ic_launcher_round.png app/src/main/res/mipmap-xxxhdpi/ic_launcher.png app/src/main/res/mipmap-xxxhdpi/ic_launcher_round.png
git commit -m "[Fix] Add green-S raster icon fallback for API <26"
```

> Fallback (only if Step 1 cannot run / `System.Drawing` unavailable): skip Task 2 entirely and ship adaptive-only exactly like the original `a9306d3` — the icon is correct on API 26+ (compileSdk/target 36; the overwhelming majority of devices). Record this choice in the commit/report.

---

## Task 3: Final verification

**Files:** none.

- [ ] **Step 1: Release build** — `$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat assembleRelease` → `BUILD SUCCESSFUL` (confirms icons are valid in release packaging too).
- [ ] **Step 2: Confirm the Play Store asset** — `app/src/main/ic_launcher-playstore.png` is the green "S" (512px) for future Play Console use.
- [ ] **Step 3:** No commit (verification only).

---

## Self-Review (author check vs. spec)

- **Spec coverage:** restore adaptive xmls + foreground + 512 PNG → Task 1 Step 1; delete green-grid drawable → Task 1 Step 2; keep `#2B2B2B` color → Task 1 Step 3; regenerate API <26 raster fallback from the 512 PNG → Task 2 (with the spec's adaptive-only fallback noted); manifest unchanged (not touched); verify build + launcher screenshot + 512 asset → Task 2 Step 3 / Task 3. All spec sections map to tasks.
- **Placeholder scan:** concrete `git checkout`/`git rm` commands, a complete runnable `System.Drawing` script, exact density px (48/72/96/144/192), and explicit verification. No vague steps. The raster mechanism is fully specified (no "decide later").
- **Consistency:** densities and file names match between Task 2 Step 1 (generation) and Step 4 (commit). The deleted `.webp` set (Task 1) and the created `.png` set (Task 2) cover the same 5 densities × 2 variants, so no duplicate-resource collision. `@color/ic_launcher_background` referenced by the restored adaptive xml matches the kept values file.
- **Residual note:** launcher icon caching can mask the change at runtime (Task 2 Step 3 covers uninstall/reinstall to bust it).
