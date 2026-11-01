# FocusForge Pro ⚡

An offline-first Android study + fitness tracker for CA / B.Com students: a focus timer,
an app "shield" built on the Accessibility API, a syllabus tracker with spaced repetition,
a workout studio with animated exercise demos, and a habit matrix.

Built with Kotlin + plain Android views (no Compose, no third-party runtime libraries).

## Look & feel
- **Aurora backdrop** — three blurred colour wells (indigo / cyan / violet) behind
  everything, so translucent panels read as glass floating in space.
- **Gradient glass surfaces** — every card is a vertical ramp with a bright top
  edge, a hairline border and a soft sheen; accent cards pick up their accent colour.
- **Pill chips** for filters and tags, a floating glass dock for the tab bar,
  a gradient-glow ring for the level / session progress and gradient timer numerals.

## 📥 Install the APK

Download [`dist/FocusForge-Pro-debug.apk`](dist/FocusForge-Pro-debug.apk) and open it on the
phone (enable *Install unknown apps* for your browser/file manager first). Every push to this
branch rebuilds the APK automatically and CI commits the fresh binary to `dist/`.

Requires Android 8.0 (API 26) or newer.

## Tabs

### 1. Focus (dashboard + timer)
- **Hunter dashboard**: greeting + date, live streak badge, four stat tiles
  (focus today, habits, gym last 7 days, distractions blocked), daily-goal bar and
  one-tap quick actions (25m, 50m, log block, shield).
- **Session types** — Study / Revision / Mock Test; the type is stored with every log entry.
- **Timer**: presets, custom H:M, ±5m fine-tune, restart-safe countdown,
  Strict Mode (the shield cannot be weakened mid-session).
- **Keep screen awake** during a live session.
- **Completion notification** (local, no network) when a block finishes.
- **Recent focus blocks** list with XP earned and undo.

### 2. Syllabus
- Courses, subjects, modules and unit status cycles (Todo → Studying → Done).
- **Spaced-revision planner**: completing a unit schedules revisions at
  1 → 3 → 7 → 14 → 30 → 60 days. The planner card shows exam readiness (%),
  the due/overdue queue, upcoming count and one-tap "did it" logging.
- Per-unit "next revision in Nd" hints and revision counter.
- Quick Vault for law sections, AS and formulas.

### 3. Consistency
- Weekly consistency chart and 7-day habit matrix with per-habit streaks.
- **Momentum heatmap** (last 5 weeks) for focus minutes, gym or habits.

### 4. Workout Studio (most detailed tab)
- **Session overview** card: progress ring, sets done, volume moved, elapsed time,
  XP and "next up" hint, with one-tap start/rest controls.
- Training plan per split with set/rep/weight logging, rest timer and stopwatch.
- **Raymarched 3D exercise demos** (22 clips, bodyweight/barbell/dumbbell/cable):
  the athlete is a signed-distance-field body raymarched per pixel, so joints merge
  smoothly, limbs occlude correctly and muscle groups glow red as they work — see
  `tools/motiongen/mannequin3.py`. Plus a tappable anatomy map.
- Generator for a session from muscle/equipment filters.
- **Quick actions per exercise** that stay visible when collapsed: log a set,
  add a set, open the 1RM calculator.
- **Exercise library** with search plus muscle and equipment chip filters.
- **Barbell plate calculator** and a **set-by-set workout summary** (volume,
  average intensity, multiples of bodyweight) when you finish.
- **Report**: weekly calendar, volume totals, weekly gym target, and a
  **personal-record board** with Epley 1RM estimates plus a 1RM/working-weight calculator.

### 5. Shield
- Blocks YouTube Shorts (while allowing lecture videos), Instagram/Reels, social apps,
  the whole YouTube app (nuclear mode) and any custom app.
- **Focus hours schedule** with presets (Morning / Afternoon / Night Owl) — the shield
  only guards inside the window.
- **Snooze** 15/30 minutes and a **whitelist** of apps that are never blocked.
- Backup/restore of all data as a clipboard JSON blob.

## Data
Everything is stored locally in `SharedPreferences` (`FocusForgePrefs`) — daily activity
log, habit history, revision schedule, workout sets and shield settings. No accounts,
no network calls.

## Building
CI (`.github/workflows/build.yml`) assembles the debug APK on every push to
`main`/`arena/**`/`feature/**` and publishes it to `dist/`. To build locally you need
JDK 17 and the Android SDK, then run `gradle assembleDebug`.

## Art pipeline
`tools/motiongen/` regenerates the exercise animation frames
(`app/src/main/assets/motion/<exercise>/f00..f17.webp`) from the reference art.
