# 還原 App Icon（綠色 S）設計文件

- 日期：2026-06-11
- 範圍：把被覆蓋成預設 Android 範本的 launcher icon 還原為原本的品牌 icon（深底 `#2B2B2B`
  上的綠色「S」），並補上原本缺少的 API <26 raster fallback。
- 不在本案：重新設計 icon、Play Console 圖示更新流程（僅產出 512 圖檔)。

## 背景與根因

`git` 歷史顯示 launcher icon 在 commit `d04fa9f`（「Update ic_launcher-playstore.png … and
20 more files…」）被 Image Asset Studio 重新產生，覆蓋成**預設 Android 範本**（綠色機器人 +
綠色格線背景 `#3DDC84`）。最後一個正確的自訂 icon 在 commit `a9306d3`：

- `mipmap-anydpi-v26/ic_launcher.xml`（與 `_round`）:`<background android:drawable="@color/ic_launcher_background"/>`（`#2B2B2B` 深色）+ `<foreground android:drawable="@drawable/ic_launcher_foreground"/>`
- `drawable/ic_launcher_foreground.xml`:綠色「S」向量（`#38D69F`）
- `ic_launcher-playstore.png`:512px 綠色 S（深底）
- `values/ic_launcher_background.xml`:`<color name="ic_launcher_background">#2B2B2B</color>`
- **當時沒有任何 raster `.webp` mipmap**(只有 anydpi-v26 的 adaptive 定義)

`d04fa9f` 的破壞:foreground 換成機器人;adaptive 背景由 `@color/...` 改指向新的
`@drawable/ic_launcher_background`(74 行綠色格線);新增 5 個密度的預設 raster webp
(`mipmap-*/ic_launcher.webp` 與 `_round`);playstore png 換成預設綠色 Android。

## 還原方案

1. **還原 adaptive icon 與前景/512 圖**(從 `a9306d3` 取回):
   - `app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml`
   - `app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml`
   - `app/src/main/res/drawable/ic_launcher_foreground.xml`
   - `app/src/main/ic_launcher-playstore.png`
   作法:`git checkout a9306d3 -- <上述四個路徑>`。還原後 adaptive 背景回到
   `@color/ic_launcher_background`(`#2B2B2B`)、前景為綠色 S。
   `values/ic_launcher_background.xml`(`#2B2B2B`)自 `a9306d3` 起未變,維持即可。

2. **刪除** `app/src/main/res/drawable/ic_launcher_background.xml`(`d04fa9f` 新增的綠色格線
   向量,還原後 adaptive icon 已改用 `@color/...`,此檔不再被引用)。

3. **重生 API <26 raster fallback**(補原本就缺的部分,使 API 24–25 也顯示綠色 S):
   以還原後的 512px `ic_launcher-playstore.png`(深底綠 S)為來源,縮放產生 5 個密度的
   `ic_launcher`(方形)與 `ic_launcher_round`(圓形遮罩)點陣圖,取代 `d04fa9f` 留下的預設
   綠色 Android webp:
   - mdpi 48、hdpi 72、xhdpi 96、xxhdpi 144、xxxhdpi 192(px)
   - 格式:PNG 或 webp 皆可(Android 接受 PNG mipmap);產生工具於 plan 階段決定
     (優先用本機可用的影像工具縮放/圓形遮罩;若無則改用 Android Studio Image Asset 或
     等比例縮放腳本)。
   - 若工具不可得,退而求其次:沿用 `a9306d3` 的「adaptive-only、無 raster」原狀(刪掉
     `d04fa9f` 的預設 webp),維持與原版完全一致(API <26 仍是原本的缺口,但與上架版本相同)。

4. Manifest 不需更動(`android:icon="@mipmap/ic_launcher"`、`roundIcon="@mipmap/ic_launcher_round"`
   皆已存在且正確)。

## 風險與注意

- **資源同名**:`@color/ic_launcher_background`(values)與曾經的 `@drawable/ic_launcher_background`
  同名但不同型別;刪除 drawable 後,adaptive 的 `@color/...` 引用即無歧義。
- **raster 來源**:用 512 playstore png 縮放可確保各密度與品牌一致;圓形版需套圓形遮罩。
- **裝置快取**:launcher 會快取舊 icon,驗證時若沒更新,重裝或清 launcher 快取再看。

## 驗證

- 環境見專案記憶(JAVA_HOME 指向 Android Studio JBR、`gradlew.bat`、`Pixel_10_Pro` AVD/API 37)。
- `.\gradlew.bat assembleDebug` 綠燈(資源編譯通過、無重複資源錯誤)。
- `.\gradlew.bat installDebug`;到 launcher / app drawer 查看圖示為**深底綠色 S**(非綠色機器人)。
  必要時重裝以清 launcher icon 快取。截圖佐證。
- 確認 `ic_launcher-playstore.png` 為綠色 S(供日後 Play Console 使用)。

## 明確排除（YAGNI）

- 不重新設計 icon、不加 monochrome（themed icon)圖層。
- 不在本案處理 Play Console 上傳;僅確保 512 圖檔正確。
