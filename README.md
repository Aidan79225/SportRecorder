# SportRecorder

> 這是一支從斷食出發的飲食紀錄 app。
>
> 我們只記錄、不評價,陪你留住每一個美好的當下。
>
> 在自我覺察的路上,和你一起慢慢長大。

*A fasting-rooted food diary. We record, never judge — here to help you hold on to
each good moment, and to grow with you, slowly, on the path to self-awareness.*

## 初衷 · Why this exists

斷食不該是焦慮的數字遊戲。SportRecorder 從斷食出發,但真正想做的是:用**輕鬆的記錄**,
讓你**意識到自己到底吃了什麼**,進而**更關注健康與生活**。我們相信覺察先於改變,而覺察來自
溫柔地看見自己,不是被打分數。

## 習慣循環 · The habit loop

**Capture → Reflect → Insight → Re-engage**

- **Capture 擷取** — 一餐一個時間點,可加照片、備註、地點。記錄要快、要輕鬆。
- **Reflect 回顧** — 把吃過的回放給你看:是樣貌,不只是清單。
- **Insight 洞察** — 從已記錄的資料看出模式,連起斷食與飲食兩半。
- **Re-engage 再參與** — 在對的時刻溫柔提醒(進食視窗、斷食達標),把你帶回循環。

## 設計原則 · What guides every decision

這些原則直接來自初衷,任何新功能都該通過它們:

- **只記錄,不評價** — 不替食物貼好壞標籤、不羞辱、不製造罪惡感。
- **留住當下** — 記錄要無摩擦;捕捉一個片刻應該是愉快的,不是負擔。
- **服務覺察** — 功能是為了幫你看見自己,而不是外在壓力、排名或為遊戲化而遊戲化。
- **慢慢長大** — 溫柔、不強迫;陪伴的語氣,不是教練的命令。

## 開發 · Development

- 設計文件(spec)與實作計畫(plan)放在 `docs/superpowers/`。
- 每份 spec 在定案前都會做一次**初衷對照**(見 `CLAUDE.md` 的 North Star check)。
- 技術:Kotlin · Jetpack Compose · Hilt · Room · DataStore(clean architecture)。
- 建置/驗證:`JAVA_HOME` 指向 Android Studio JBR,然後
  `./gradlew testDebugUnitTest :app:detekt :app:lintDebug`。
- 發布:bump 版號 + 更新 `distribution/whatsnew/*` → 打 tag → GitHub Action 自動上傳到 Play
  (詳見 `.claude/skills/release`)。
