# TeleZen

Android app (Kotlin, Jetpack Compose, Room, WorkManager) that blocks short-form feeds inside apps via an AccessibilityService. Android only. The `offline` flavor (the normal build) has no network access by design: never add the INTERNET permission to `src/main` or `src/offline`, and keep network code out of them. Only the `sync` flavor (`app/src/sync`) has it, to push stats to the user's own laptop; it is a separate APK so the normal one stays internet-free.

## Commands

- Unit tests: `./gradlew testOfflineDebugUnitTest testSyncDebugUnitTest`
- Debug APKs (both flavors): `./gradlew assembleDebug`
- Release APK (R8 minified, debug-signed): `./gradlew assembleOfflineRelease` (or `assembleSyncRelease`). Check release builds after touching serialized models: kotlinx.serialization + R8 keep rules are in `app/proguard-rules.pro`.

## Architecture

- `service/TeleZenAccessibilityService.kt`: tracks the foreground app (window-state events), runs throttled screen checks for short-form targets, a 5s tick for sessions/limits/schedules, and the strict-mode Settings guard.
- `service/NodeMatcher.kt`: evaluates rules against the node tree. Compose screens don't support platform text search, so `findText` falls back to a throttled tree walk.
- `core/Policy.kt`: pure decision logic (`Policy.onEntry`, `ScheduleLogic`, `StrictPolicy`). Keep it free of Android APIs and cover changes in `app/src/test`.
- `data/SettingsStore.kt`: every settings change goes through `update {}`, which rejects loosening changes while strict mode is on.
- `assets/rules.json`: detection rules. Bump `version` when changing them; `RulesParser` validates on load.
- `ui/intervention/`: the pause/block/limit/schedule/session-end screen, started by the service in its own task.
- Flavors: `core/LaptopSync.kt` and `ui/screens/LaptopSyncSection.kt` exist in both `src/offline` (empty stubs) and `src/sync` (real code: pairing code in its own SharedPreferences, HTTP push of `StatsExport`, hourly WorkManager job). Main code only calls `LaptopSync.schedule` and `LaptopSync.available`. The laptop side is the WatchZen helper (`watchzen/helper/server.js`).

## Gotchas

- Home screens and the default dialer are "protected packages": never pause or block them (`Apps.protectedPackages`).
- Settings/permission intents are started from an Activity without `FLAG_ACTIVITY_NEW_TASK`, so Back returns to TeleZen.
- Instagram's `reel_*` view IDs are Stories; Reels are `clips_*`.
