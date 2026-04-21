# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

myPlanet Lite is a native Android app (Kotlin, `org.ole.planet.myplanet.lite`, applicationId `org.ole.planet.myplanet.lite`) that talks to a Planet / OLE backend (CouchDB-style HTTP API). The default dev server is `BuildConfig.PLANET_BASE_URL` (`app/build.gradle.kts:27`); override per-device via `local.properties`'s `sdk.dir` for the SDK path.

- Gradle Kotlin DSL with a version catalog at `gradle/libs.versions.toml` — add/change dependencies there, not inline in `app/build.gradle.kts`.
- JDK 21 is required (matches the CI in `.github/workflows/{build,test}.yml`). `compileSdk`/`targetSdk` 36, `minSdk` 28. Kotlin compiled to JVM target 11.
- `fix.sh` patches a `DashboardNewsActionsRepository.kt` string-interpolation typo that occasionally reappears after code generation / merges; run it if `assembleDebug` fails on that file.

## Commands

```bash
# Build debug APK
./gradlew assembleDebug

# Run unit tests (Robolectric + JVM)
./gradlew testDebugUnitTest

# Run a single test class or method
./gradlew :app:testDebugUnitTest --tests "org.ole.planet.myplanet.lite.auth.NetworkAuthServiceTest"
./gradlew :app:testDebugUnitTest --tests "*.DashboardNewsRepositoryTest.fetches posts"

# Instrumented tests (requires a device/emulator)
./gradlew connectedDebugAndroidTest
```

Tests require two JVM agents that are wired up in `app/build.gradle.kts:48-62`: a Mockito inline agent (`mockitoAgent` configuration) and a ByteBuddy agent for MockK (`mockkAgent`). If you add a new test configuration or task, replicate that `doFirst { jvmArgs(...) }` block — otherwise Mockito/MockK mocks of final classes will fail at runtime.

Robolectric logging is silenced via `systemProperty("robolectric.logging", "none")`; enable it temporarily when debugging test boot failures.

## Architecture

UI is plain Android (AppCompat + Material + ViewBinding, no Compose, no DI framework). Dependencies are wired through hand-written `object` singletons (e.g. `auth/AuthDependencies.kt`) that cache an `OkHttpClient` + `Moshi` and expose `overrideAuthService(...)` / `resetForTesting()` hooks — tests swap implementations through these overrides rather than through a DI graph.

Entry flow: `SplashScreen` → `MyPlanetLite` (login) → `DashboardActivity` / `TeamsActivity`. `BaseActivity` centralises locale application (`LanguagePreferences.applySavedLocale`) and edge-to-edge inset padding; every activity should extend it so language switching works consistently.

Layers inside `app/src/main/java/org/ole/planet/myplanet/lite/`:

- `auth/` — Retrofit `AuthApi`, a `sealed class AuthResult` (Success / Error / Failure.InvalidCredentials / Failure.NetworkError), `NetworkAuthService` which persists the `Set-Cookie` session into `SecureTokenStorage` (EncryptedSharedPreferences). Error messages from the server are currently in Spanish — keep that convention when extending.
- `dashboard/` — One Retrofit/OkHttp-backed `*Repository` per feature (news, courses, teams, surveys, voices, server config). Repositories are constructor-injected with `OkHttpClient`, `Moshi`, and a `CoroutineDispatcher` defaulting to `Dispatchers.IO`, which is how tests inject `TestDispatcher` and `MockWebServer`. `*Store` / `*Preferences` classes wrap `SharedPreferences` (often via `SecurePreferencesProvider`) for offline caches, draft outboxes and selection state.
- `profile/` — `UserProfileDatabase` is a hand-rolled `SQLiteOpenHelper` (versioned schema, currently v4, migrates a legacy `user_profile.db` into `.user_profile_data.db`). `UserProfileSync` reconciles it with the remote doc; `AvatarUpdateNotifier` broadcasts avatar changes to listening fragments.
- `survey/` + `surveys/` — Survey wizard flow plus translation caching (`SurveyTranslationCache` / `SurveyTranslationManager`) backed by ML Kit language ID (`libs.language.id`).
- `util/` — Pure Kotlin helpers (string/date/diff/markdown/cursor extensions, `SecurePreferencesProvider`, `ServerMetadataExtractor`). These have near-100% unit-test coverage in `app/src/test/.../util/`; prefer extending here over inlining logic.

Networking uses Retrofit + Moshi (Kotlin reflective adapter) over OkHttp with `usesCleartextTraffic="true"` and a custom `@xml/network_security_config` because the default dev Planet server is plain HTTP on a LAN IP. Session auth is cookie-based: the `Set-Cookie` header from `/_session` is stored verbatim and replayed as the `Cookie` header on subsequent requests — do not swap this for a bearer-token flow without also updating `TokenStorage` and every repository that reads `sessionCookie`.

Localisation: every user-visible string lives under `res/values-{ar,es,fr,hi,ne,pt,so}/` (plus default `values/`). Raw audio/video assets are duplicated per locale under `res/raw-*/`. When adding strings, add them to `values/strings.xml` and the translated variants — the test `SurveyTranslationManager` exercises language switching and will flake if keys drift.

## Conventions

- Kotlin code style is `official` (`gradle.properties`). Files typically start with a short author/date header comment — match it when creating new files in the same package.
- Do not edit `local.properties` (it holds a machine-specific `sdk.dir`) or commit it.
- `android.useAndroidX=true`, `android.nonTransitiveRClass=true` — import `R` from `org.ole.planet.myplanet.lite.R`, not transitively from libraries.
- CI (`.github/workflows/build.yml`, `test.yml`) runs on every branch push except `master`; keep `assembleDebug` and `testDebugUnitTest` green before pushing.
