# ![ole_logo](https://github.com/user-attachments/assets/088e6283-42bb-4e6c-b6ed-67f32f341c44) myPlanet Lite

> A lightweight, offline-first native Android learning app for the Open Learning Exchange (OLE) ecosystem.

[![license: AGPL v3](https://img.shields.io/badge/License-AGPL%20v3-blue.svg)](https://www.gnu.org/licenses/agpl-3.0)
[![myPlanet-Lite build](https://github.com/open-learning-exchange/myplanet-lite/actions/workflows/build.yml/badge.svg)](https://github.com/open-learning-exchange/myplanet-lite/actions/workflows/build.yml)
[![myPlanet-Lite test](https://github.com/open-learning-exchange/myplanet-lite/actions/workflows/test.yml/badge.svg)](https://github.com/open-learning-exchange/myplanet-lite/actions/workflows/test.yml)
[![chat on discord](https://img.shields.io/discord/1079980988421132369?logo=discord&color=%237785cc)](https://discord.gg/BVrFEeNtQZ)

**myPlanet Lite** is a native Android application designed to connect learners and educators to the [Open Learning Exchange](https://ole.org)’s Learning Management System, [Planet](https://github.com/open-learning-exchange/planet). Engineered for offline-first resilience and high performance on resource-constrained devices, myPlanet Lite enables continuous learning, content synchronization, and community collaboration in remote environments with intermittent or no internet connectivity.

- [Feature Highlights](#feature-highlights)
- [About myPlanet Lite](#about-myplanet-lite)
- [Architecture & Tech Stack](#architecture--tech-stack)
- [Getting Started](#getting-started)
- [Development & Testing](#development--testing)
- [Conventions](#conventions)
- [Contact & Community](#contact--community)

---

## Feature Highlights

- 📴 **Offline-First Resilience** — Seamlessly access downloaded courses, learning materials, and surveys without an active internet connection.
- 📚 **Courses & Course Wizard** — Browse structured courses, view attachments, step through interactive course modules, and track progress offline.
- 🎬 **Multimedia & Document Viewer** — Built-in hardware-accelerated media player powered by AndroidX Media3 (ExoPlayer) for video and audio playback, integrated PDF reader, and Markdown rendering.
- 📋 **Surveys, Drafts & Outbox Sync** — Take surveys offline with automatic draft saving and an outbox queue that reconciles and syncs responses upon reconnecting.
- 👥 **Community Voices & Teams** — Stay engaged with the learning community through news feeds, team discussions, team rosters, and member profiles.
- 🌐 **Multi-Server & Custom Backend** — Connect out-of-the-box to major Planet instances (Planet Learning, Planet Earth, Planet Guatemala, Planet Somalia) or configure custom LAN / local server endpoints.
- 🌍 **Multilingual & RTL Support** — Localized into 8 languages: English, Spanish (Español), French (Français), Portuguese (Português), Arabic (العربية with full RTL support), Somali (Soomaali), Nepali (नेपाली), and Hindi (हिन्दी), featuring ML Kit-assisted survey translation support.
- 🔗 **Deep Linking** — Direct navigation support for web survey URLs and custom deep links (`myplanetlite://`).

---

## About myPlanet Lite

myPlanet Lite serves as a streamlined native client within the Open Learning Exchange suite. Built specifically to complement and serve alongside [myPlanet](https://github.com/open-learning-exchange/myplanet), it emphasizes minimal overhead, responsive navigation, and robust offline data handling.

The app acts as a local bridge to Planet servers (CouchDB-compatible REST APIs). Users can synchronize educational content when connectivity is available, store lessons locally, record audio or capture media for submissions, and submit survey evaluations asynchronously.

---

## Architecture & Tech Stack

myPlanet Lite uses a modern, lightweight Android architecture designed for stability and testability without unnecessary framework overhead:

- **Platform & Language**: Native Android with Kotlin, compiled to JVM target 17 (`compileSdk 37`, `targetSdk 36`, `minSdk 28`).
- **UI Layer**: AppCompat, Material Components, ViewBinding, and custom edge-to-edge system insets handling.
- **Networking**: Retrofit 2 + Moshi (Kotlin reflective adapter) over OkHttp, utilizing cookie-based session management (`/_session`) for Planet backends.
- **Data & Persistence**:
  - `EncryptedSharedPreferences` via `SecurePreferencesProvider` for secure session tokens and credentials.
  - Hand-rolled `SQLiteOpenHelper` (`UserProfileDatabase`) for local profile management.
  - Disk-backed offline stores for courses, resources, survey drafts, and pending outbox payloads.
- **Media & Rendering**:
  - AndroidX Media3 (ExoPlayer, UI, Transformer, Effect) for audio/video playback and waveform extraction.
  - Markwon for Markdown and rich HTML rendering.
  - uCrop and PhotoView for image editing and viewing.
  - ML Kit Language Identification for automatic survey translation workflows.
- **Dependency Management**: Centralized Gradle Version Catalog at `gradle/libs.versions.toml`.

---

## Getting Started

### Prerequisites

- **Android Studio**: Android Studio Ladybug / Meerkat or later.
- **Java Development Kit**: JDK 21 (Temurin or OpenJDK recommended).
- **Android SDK**: API Level 37 (`minSdk 28`).

### Cloning the Repository

```bash
git clone https://github.com/open-learning-exchange/myplanet-lite.git
cd myplanet-lite
```

---

## Development & Testing

### Build Commands

```bash
# Build the debug APK
./gradlew assembleDebug

# Build release APK
./gradlew assembleRelease
```

### Running Tests

Unit tests run with Robolectric and JVM mocks, configured with Mockito and MockK ByteBuddy dynamic agents:

```bash
# Run all unit tests
./gradlew testDebugUnitTest

# Run a specific unit test class
./gradlew :app:testDebugUnitTest --tests "org.ole.planet.myplanet.lite.auth.NetworkAuthServiceTest"

# Run instrumented tests on a connected device/emulator
./gradlew connectedDebugAndroidTest
```

> [!NOTE]
> For unit tests mocking final classes, the Gradle test task is pre-configured with JDK dynamic agent loading flags (`-XX:+EnableDynamicAgentLoading`, Mockito agent, and ByteBuddy agent).

---

## Conventions

- **Code Style**: Follows the `official` Kotlin coding conventions defined in `gradle.properties`.
- **Imports**: Non-transitive R classes are enabled (`android.nonTransitiveRClass=true`); import resources explicitly via `org.ole.planet.myplanet.lite.R`.
- **Localization**: User-visible strings must be defined in `res/values/strings.xml` and localized across `res/values-{ar,es,fr,hi,ne,pt,so}/`.
- **CI / Workflows**: Automated GitHub Actions workflows run on all pushes to validate builds and execute unit tests (`.github/workflows/build.yml`, `test.yml`).

---

## Contact & Community

myPlanet Lite is an open-source project created and maintained by the [Open Learning Exchange](https://ole.org).

- **Discord**: Join the [OLE Discord Community](https://discord.gg/BVrFEeNtQZ) for discussions, feedback, and contributor support.
- **Issue Tracker**: Report bugs and submit feature requests on [GitHub Issues](https://github.com/open-learning-exchange/myplanet-lite/issues).
- **Organization Website**: [https://ole.org](https://ole.org)
