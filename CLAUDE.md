# SportRecorder — project guide

## North Star · 初衷

> 這是一支從斷食出發的飲食紀錄 app。
> 我們只記錄、不評價,陪你留住每一個美好的當下。
> 在自我覺察的路上,和你一起慢慢長大。

Everything we build serves **awareness through easy recording** — the habit loop
**Capture → Reflect → Insight → Re-engage**. See `README.md` for the full framing.

## North-Star check (do this during every brainstorm / spec)

When brainstorming a feature or writing/reviewing a spec, **before finalizing the design**,
add a short **「初衷對照 / North-Star check」** section that answers: *does this serve the
mission, and where might it drift?* Call out drift explicitly and propose a mission-aligned
alternative — don't just wave it through.

Treat these as **red flags** (a feature hitting one needs justification or a redesign):

- **評價使用者 (judging the user)** — scoring/grading days, good/bad food labels, guilt,
  shame, "you failed" framing. The mission is *只記錄、不評價*.
- **記錄變成負擔 (friction in capture)** — extra required steps, nags that make logging feel
  like a chore. Capturing a moment should stay quick and pleasant.
- **外在壓力取代覺察 (external pressure over awareness)** — leaderboards, competitive streaks,
  punishment, manipulative gamification. Features exist to help users *see themselves*, not
  to push them.
- **教練式命令的語氣 (coach-barking tone)** — copy/notifications should be gentle and
  companionable (*陪伴*), never coercive.

Borderline by design: any metric that can read as a verdict (adherence ON/OFF, streaks).
If a feature leans that way, prefer **neutral reflection** ("here's your pattern") over
**judgment** ("you missed your goal"), and flag the tension in the spec.

## Build & verify

- `JAVA_HOME` must point to the Android Studio JBR (Java isn't on PATH here).
- Full local gate (matches CI): `./gradlew assembleDebug testDebugUnitTest :app:detekt :app:lintDebug`.
- Domain calculators (`DietWindow`, `InsightsAggregator`, `ReminderPlanner`) are pure and
  unit-tested — keep new logic there, Android-free.
- Specs/plans live in `docs/superpowers/`. Release flow: `.claude/skills/release`.
