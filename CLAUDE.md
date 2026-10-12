# TeleZen

Android app (Kotlin, Jetpack Compose, Room, WorkManager) that blocks short-form feeds inside apps via an AccessibilityService. Android only; no network access by design. Never add the INTERNET permission.

## Commands

- Unit tests: `./gradlew testDebugUnitTest`
- Debug APK: `./gradlew assembleDebug`
- Release APK (R8 minified, debug-signed): `./gradlew assembleRelease`. Check release builds after touching serialized models: kotlinx.serialization + R8 keep rules are in `app/proguard-rules.pro`.

## Architecture

- `service/TeleZenAccessibilityService.kt`: tracks the foreground app (window-state events), runs throttled screen checks for short-form targets, a 5s tick for sessions/limits/schedules, and the strict-mode Settings guard.
- `service/NodeMatcher.kt`: evaluates rules against the node tree. Compose screens don't support platform text search, so `findText` falls back to a throttled tree walk.
- `core/Policy.kt`: pure decision logic (`Policy.onEntry`, `ScheduleLogic`, `StrictPolicy`). Keep it free of Android APIs and cover changes in `app/src/test`.
- `data/SettingsStore.kt`: every settings change goes through `update {}`, which rejects loosening changes while strict mode is on.
- `assets/rules.json`: detection rules. Bump `version` when changing them; `RulesParser` validates on load.
- `ui/intervention/`: the pause/block/limit/schedule/session-end screen, started by the service in its own task.

## Gotchas

- Home screens and the default dialer are "protected packages": never pause or block them (`Apps.protectedPackages`).
- Settings/permission intents are started from an Activity without `FLAG_ACTIVITY_NEW_TASK`, so Back returns to TeleZen.
- Instagram's `reel_*` view IDs are Stories; Reels are `clips_*`.
