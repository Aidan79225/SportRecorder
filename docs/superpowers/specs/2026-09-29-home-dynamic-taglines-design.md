# Home 動態標語 · Dynamic home taglines

**Status:** implementing (`claude/busy-mccarthy-t7xloj`)

## Problem

The Home headline above the fasting-type chip was one fixed string per phase
(`準備好了嗎？` / `進食中，好好享受！` / `斷食中，加油！` / `你做到了！`). Opening the app always
read the same, so the line faded into the background.

## Design

- **Pure picker** — `domain/diet/HomeTagline.kt` (`HomeTaglinePicker`, commonMain, unit-tested)
  maps the `DietWindowState` + local hour to a `TaglineMood`:

  | Phase | Mood | Rule |
  |---|---|---|
  | IDLE | `IDLE_MORNING` / `AFTERNOON` / `EVENING` / `NIGHT` | 05–11 / 11–17 / 17–22 / 22–05 |
  | EATING | `EATING_WINDING_DOWN` | ≤ 1h left in the window |
  | EATING | `EATING` | otherwise |
  | FASTING | `FASTING_JUST_STARTED` | ring < 15% |
  | FASTING | `FASTING_ALMOST_THERE` | ring ≥ 85% |
  | FASTING | `FASTING` | otherwise |
  | SUCCESS | `SUCCESS` | — |

- **Pools** — one `string-array` per mood (`diet_tagline_*`, en + zh-TW, 3–5 lines each). The
  original four lines are kept inside their pools.
- **Variety without flicker** — `DietViewModel` draws a new seed each time its `uiState` upstream
  starts (every Home visit, via `WhileSubscribed`), so the line differs between visits but stays
  put while the per-second ticker runs. The mood can still change mid-visit as context changes
  (e.g. the fast reaches 85%).
- The UI resolves `HomeTagline(mood, variant)` → `pool[variant % pool.size]`, so locales may have
  different pool sizes.

## 初衷對照 / North-Star check

- **只記錄、不評價** — no line grades the day. `SUCCESS` keeps `你做到了！` but mixes in neutral
  reflections (`到達你設定的時間了`, `聽聽身體想吃什麼`) so reaching the target reads as a fact,
  not a verdict; there is no "failed / broke the fast" pool at all.
- **陪伴, not coach-barking** — lines are companionable or observational (`斷食中，陪著你`,
  `身體正在休息`, `感受此刻的自己`); suggestions are phrased as questions (`要不要喝點水？`)
  rather than commands. `斷食中，加油！` stays as one gentle cheer among several.
- **No capture friction** — display-only; nothing new to tap or fill in. Some eating lines
  (`拍下美味的瞬間`) softly invite capture, which serves Capture → Reflect.
- **Drift to watch** — `EATING_WINDING_DOWN` could read as pressure to stop eating; its lines stay
  descriptive (`進食時間快結束囉`, `最後幾口，慢慢享受`). Future additions should avoid
  urgency, countdown panic or streak framing.
