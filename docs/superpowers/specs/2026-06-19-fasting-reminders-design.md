# 斷食提醒 / Fasting reminders — design

**Date:** 2026-06-19
**Status:** Approved design, pending implementation

## Context

SportRecorder is a fasting-rooted food-diary app framed as **Capture → Reflect →
Insight → Re-engage**. The *Capture* engine (time/photo/note/location per meal) and,
since the Insights tab, the *Reflect / Insight* surface are both strong. What is
entirely missing is the **real-time / prospective** half: the app never tells the user
anything *while* a fast or eating window is in progress. There is no `WorkManager`,
`AlarmManager`, `NotificationChannel`, or `BroadcastReceiver` anywhere in the codebase.

This feature adds **scheduled local notifications** so the app nudges the user at the
two moments that matter for intermittent fasting, plus a first **Settings** screen to
control them.

## Scope (MVP)

Two reminders, both derived from the existing `DietWindow` calculation:

1. **進食視窗即將關閉** — fires at `windowEnd − leadMinutes` (default 30, configurable).
   Only meaningful while in the EATING phase. "Last call" to finish the final meal.
2. **斷食達標 🎉** — fires at `fastTargetAt` (`lastEat + fastingHours`). The high-value,
   celebratory one.

A new **Settings** screen, reached from a floating gear `IconButton` on the Diet home
(top-end of the existing `Box` — **not** a `TopAppBar`, to match the app's no-top-bar
style), containing:

- Master/ per-reminder on-off toggles (one per reminder).
- **Lead time** for "window closing" — adjustable length (minutes).
- **Quiet hours** — a toggle plus an adjustable start/end time range; while active the
  斷食達標 notification is suppressed (see below).
- If exact-alarm permission is not granted, an inline "開啟精準提醒" action that deep-links
  to the system exact-alarm settings.

Out of scope: a third "log your meal" nudge, per-reminder custom sounds, snooze,
notification history, calorie/macro reminders, configurable quiet-hours behaviour beyond
suppress.

## Behaviour details

### Quiet hours
A user-set window (e.g. 22:00–08:00, toggleable). Because fasts commonly complete in the
small hours, when **斷食達標**'s trigger time falls inside quiet hours the notification is
**suppressed** (not deferred) for MVP — we do not post it at all for that fast. The
"window closing" reminder is not quiet-hours-gated (it only fires during the eating
window, i.e. daytime by definition). Quiet hours may span midnight (start > end).

### Scheduling
- Each reminder type owns one `AlarmManager` slot (distinct PendingIntent request code).
- We schedule **only future, not-yet-passed** events. 斷食達標 is skipped if the fast is
  already complete (`now ≥ fastTargetAt`) or suppressed by quiet hours; 視窗即將關閉 is
  skipped outside the EATING phase or if `windowEnd − lead` is already past.
- `fastTargetAt` shifts later every time a new meal is logged, so we **recompute and
  reschedule on every data change** rather than relying on a long-running observer.

### Reschedule triggers
Meal saved / edited / deleted · fasting type/settings changed · reminder prefs changed ·
device boot (`BOOT_COMPLETED`) · after an alarm fires (schedule the next occurrence).

## Architecture

Mirrors the clean-architecture + Hilt + "pure domain calculator" pattern used by
`DietWindow` and `InsightsAggregator`.

- **`ReminderPlanner` (pure, Android-free, unit-tested)** — input: records, `DietSettings`,
  `ReminderPrefs`, `now`; output: `List<ScheduledReminder(type, triggerAtMillis)>`. Holds
  *all* the timing/quiet-hours/phase logic so it is fully testable.
- **`ReminderPrefs` + `ReminderPreferencesRepository`** — persisted via DataStore, mirroring
  the existing `DietSettings` DataStore module (enabled flags, lead minutes, quiet-hours
  enabled + start/end minutes-since-midnight).
- **`ReminderScheduler` (Android)** — turns `ReminderPlanner` output into exact alarms;
  uses `setExactAndAllowWhileIdle` when `canScheduleExactAlarms()`, else falls back to
  `setAndAllowWhileIdle` (inexact).
- **`ReminderReceiver` (BroadcastReceiver)** — on alarm: post the notification on its
  channel, then re-run scheduling for the next occurrence.
- **`BootReceiver`** — on `BOOT_COMPLETED`: reschedule from current DB state.
- **`RescheduleRemindersUseCase`** — reads current records+settings+prefs and drives
  `ReminderScheduler`; invoked from the mutation points listed above.
- **UI** — `Route.Settings`, `SettingsScreen` + `SettingsViewModel`; gear entry on
  `DietScreen`. Two `NotificationChannel`s (one per reminder) so users can tune each in
  system settings.

## Permissions

- `POST_NOTIFICATIONS` (Android 13+) — requested at runtime the first time the user enables
  any reminder.
- `SCHEDULE_EXACT_ALARM` (Android 12+) — user-grantable; we check `canScheduleExactAlarms()`
  and degrade to inexact if absent. **We deliberately do NOT use `USE_EXACT_ALARM`** (it is
  reserved for alarm-clock/timer apps and invites Play-policy review).
- `RECEIVE_BOOT_COMPLETED` — to reschedule after reboot.

## ⚠️ Release / compliance checklist (action for a later agent)

This feature introduces **new Android permissions**, which has store-listing and privacy
implications that must be handled at release time — **outside this repo**:

- [ ] **Privacy policy** lives in a *separate repository* (not SportRecorder). It must be
  updated to disclose the new permissions and that the app schedules **local** notifications
  (no data leaves the device). → *Hand this to an agent in that repo when cutting the release.*
- [ ] **Play Console**: review the *Permissions declaration* / *Sensitive permissions* forms.
  `SCHEDULE_EXACT_ALARM` may require a declaration; confirm `USE_EXACT_ALARM` is **absent**.
- [ ] Confirm the data-safety form still reflects "no data shared/collected" (reminders are
  purely on-device).
- [ ] Bump `versionCode` / `versionName` as usual (see the `release` skill).

*(The implementation will also leave a `TODO(release)` note near the manifest permission
declarations pointing back to this checklist so it is not forgotten.)*

## Testing

- `ReminderPlannerTest` (JVM, pinned TZ/locale like `DietViewModelTest`): window-closing only
  in EATING; 達標 time = lastEat + fastingHours; past events dropped; quiet-hours suppression
  (including a midnight-spanning range); disabled toggles produce nothing; lead-time applied.
- The Android scheduler/receiver/notification + permission flows are **not** unit-testable
  here and must be verified on a device (this dev environment cannot compile the project).
