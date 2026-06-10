# 進食紀錄改為照片 + GPS（移除食物 model）設計文件

- 日期：2026-06-10
- 範圍：移除 `FoodRecord`（食物/巨量營養素）這層，改成一筆進食紀錄可掛多張照片並記錄一個
  GPS 位置；照片轉 webp 存在 app 專屬資料夾，儲存結構為日後 Google Drive 備份預留。
- 不在本案：實際 Google Drive 登入 / 上傳 / 同步、反向地理編碼（只存原始座標）、
  既有紀錄的照片編輯。

## 背景與目的

目前進食紀錄 `EatTime(id, time)` 底下掛多筆 `FoodRecord(name, carbohydrate, protein, fat)`。
使用者要：(1) 拿掉食物這層 model；(2) 讓進食紀錄可拍照並記錄 GPS；(3) 照片轉 webp 放在
app 資料夾；(4) 儲存結構方便日後用 Google Drive 備份。

成果：一筆 eat record = 時間 + 一個 GPS 位置（可空）+ 多張 webp 照片（檔案存在 app 專屬
外部資料夾，DB 只存相對檔名）。整個「DB 檔 + photos 資料夾」即為自足、可攜的備份單位。

## 1. 資料模型（Room v3 → v4）

- **移除**：`FoodRecord` entity、`food_record` table、`FoodRecordDao`、巨量營養素 / 熱量計算。
- **`EatTime`** 新增兩個可空欄位：
  ```kotlin
  @Entity(tableName = "eat_time")
  data class EatTime(
      @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "id") val id: Int = 0,
      @ColumnInfo(name = "time") val time: Long,
      @ColumnInfo(name = "lat") val lat: Double? = null,
      @ColumnInfo(name = "lng") val lng: Double? = null,
  )
  ```
- **新增 `Photo`**（一對多，`eatTimeId`）+ `PhotoDao`：
  ```kotlin
  @Entity(tableName = "photo")
  data class Photo(
      @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "id") val id: Int = 0,
      @ColumnInfo(name = "eat_time_id") val eatTimeId: Int,
      @ColumnInfo(name = "file_name") val fileName: String,   // 相對檔名，如 <uuid>.webp
      @ColumnInfo(name = "created_at") val createdAt: Long,
  )
  ```
- `AppDatabase` 版本 3→4，entities 改為 `[EatTime, FastingType, Photo]`（移除 FoodRecord）。
- **Migration 3→4**：
  ```sql
  DROP TABLE IF EXISTS food_record;
  ALTER TABLE eat_time ADD COLUMN lat REAL;
  ALTER TABLE eat_time ADD COLUMN lng REAL;
  CREATE TABLE IF NOT EXISTS photo (
    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
    eat_time_id INTEGER NOT NULL,
    file_name TEXT NOT NULL,
    created_at INTEGER NOT NULL DEFAULT 0
  );
  ```
  既有 eat_time 列保留時間、lat/lng 為 null、無照片。**既有 food_record 資料直接丟棄**（已確認）。

## 2. 儲存布局（為 Drive 備份預留）

- 照片存於 `context.getExternalFilesDir("photos")`（如 `…/Android/data/<pkg>/files/photos/`），
  單一扁平資料夾，免儲存權限。
- DB 只存**相對檔名**（`<uuid>.webp`），不存絕對路徑 → 資料夾可攜、還原不綁路徑。
- 日後備份單位 = **Room DB 檔 + `photos/` 資料夾**，自足且可重現；Drive 專案屆時只需上傳此兩者。

## 3. 影像處理管線

- 拍照：`ACTION_IMAGE_CAPTURE`，以 `EXTRA_OUTPUT` 透過 **`FileProvider`** 寫到暫存檔
  （新增 `<provider>` 與 `res/xml/file_paths.xml`）。
- 轉檔：decode（必要時 `inSampleSize` 控制記憶體）→ 縮放到長邊上限 **1280px** →
  以 `Bitmap.CompressFormat.WEBP_LOSSY` **quality 80** 重新編碼 → 寫到 `photos/<uuid>.webp`
  → 刪暫存檔。1280 / 80 為定案參數。
- 轉檔在背景（`Dispatchers.IO`）執行，完成後把相對檔名加入 UiState 的待存清單。

## 4. 位置擷取

- 依賴 `com.google.android.gms:play-services-location` 的 `FusedLocationProviderClient`。
- 開啟「建立進食紀錄」sheet 時，runtime 請求 `ACCESS_FINE_LOCATION` / `ACCESS_COARSE_LOCATION`，
  取一次目前定位作為該筆紀錄的 GPS。拒絕 / 取不到 → lat/lng 為 null，紀錄仍可儲存。
- 只存原始座標（不反查地址）。

## 5. UI 變更

- **CreateEatTime sheet**：保留日期 / 時間列；**移除食物區**，改為：
  - 一行定位狀態（定位中 / 顯示座標 / 無位置）。
  - 「新增照片」按鈕（叫起系統相機）+ 待存照片縮圖列（每張可移除）。
  - Create：寫入 eat_time（含 lat/lng）+ 對應 Photo 列。
- **移除 `CreateFoodRecordSheet` + 其 ViewModel + `Route.CreateFoodRecord`**（拍照用相機 intent，
  不再需要表單 sheet）。
- **Record 畫面**：每列顯示日期 / 時間 + 水平照片縮圖列 + 📍 位置標示（座標）；點縮圖 →
  全螢幕檢視（Compose `Dialog` / 全螢幕 composable）。長按刪除同時刪除照片檔與 Photo 列。
- **Diet 首頁**：不受影響（只用 eat_time 時間戳）。
- 縮圖載入：用 Coil（`io.coil-kt:coil-compose`）讀本地檔案；新增此依賴。

## 6. 暫存生命週期（比舊 food 流程更乾淨）

待存照片放在 ViewModel 的 UiState（拍完即寫檔到 photos 資料夾、路徑進 state 清單），
**只有按 Create 才寫入 Photo 列**。sheet 未建立就關閉 → 刪除已暫存的檔案，避免孤兒檔
（修正舊 food draft-bucket 會留孤兒列的問題）。

## 7. 權限 / Manifest

- 新增 `ACCESS_FINE_LOCATION`、`ACCESS_COARSE_LOCATION`。
- 新增 `FileProvider`（`<provider>` + `file_paths.xml`，授權暫存拍照輸出）。
- **不需** CAMERA 權限（intent 方式）、**不需**儲存權限（app 專屬資料夾）。
- 位置權限以 Compose `rememberLauncherForActivityResult(RequestMultiplePermissions)` 請求。

## 8. 明確排除（YAGNI）

- Google Drive 登入 / 上傳 / 同步（下一個專案）。
- 反向地理編碼（只存座標）。
- 編輯既有紀錄的照片、巨量營養素 / 熱量。

## 9. 風險與注意

- **相機 intent + FileProvider**：authority 命名、`file_paths.xml` 路徑、`FLAG_GRANT_WRITE_URI_PERMISSION`
  要正確，否則拍照回傳失敗。
- **記憶體**：大圖 decode 需 `inSampleSize` / 縮放，避免 OOM。
- **位置時序**：定位是非同步，sheet 開啟後可能尚未取得；UI 顯示「定位中」，Create 時用當下已取得值。
- **模擬器驗證**：模擬器相機會回傳測試影像；位置用 Android Studio Extended Controls 灌入模擬座標。
- **刪除一致性**：刪 eat record 時要連同其 Photo 檔與列一併刪除（交易內處理）。

## 10. 驗證

- 環境見專案記憶（JAVA_HOME 指向 Android Studio JBR、`gradlew.bat`、`Pixel_10_Pro` AVD/API 37）。
- `assembleDebug` 綠燈；Room migration 3→4 不崩（既有資料庫升級成功）。
- 模擬器流程：FAB → 建立紀錄 → 授權位置（灌模擬座標）→ 拍 1–2 張照片（模擬器測試影像）→
  Create；確認：`photos/` 出現 `<uuid>.webp`、Photo 列寫入、eat_time 的 lat/lng 已存。
- Record 畫面顯示該紀錄的縮圖列與座標；點縮圖可全螢幕檢視；長按刪除同時清掉檔案與列。
- App 重啟後資料與照片仍在（持久化 + 相對檔名還原正確）。
- webp 檔案大小 / 畫質符合 1280/80 預期（檔案明顯小於原圖）。
