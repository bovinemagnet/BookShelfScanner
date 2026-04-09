# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

ShelfScan is a Kotlin Multiplatform (KMP) mobile app that scans photos of shelves to detect books, movies, and CDs, extract titles/creators via OCR, enrich via catalogue lookup, and let users review, save, and export results. Currently in early development (books-only MVP phase).

## Build & Test Commands

All Gradle commands must be run from the `shelfscan/` directory. Use `gradle21w` (on PATH) instead of `./gradlew`.

```bash
# Build shared module (JVM + iOS targets)
cd shelfscan && gradle21w :shared:build

# Run shared module tests (JVM)
cd shelfscan && gradle21w :shared:jvmTest

# Run a single test class
cd shelfscan && gradle21w :shared:jvmTest --tests "com.shelfscan.shared.domain.ParseDetectedItemUseCaseTest"

# Build Android app
cd shelfscan && gradle21w :androidApp:assembleDebug
```

A Gradle wrapper is included in `shelfscan/` but prefer `gradle21w` (on PATH) for consistency.

## Android Instrumented Tests

Instrumented tests (OCR, spine detection) require a running Android emulator or connected device.

```bash
# Set up emulator (one-time)
$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager "system-images;android-34;google_apis;x86_64"
$ANDROID_HOME/cmdline-tools/latest/bin/avdmanager create avd -n Pixel_API_34 \
  -k "system-images;android-34;google_apis;x86_64" -d pixel_6

# Start emulator
$ANDROID_HOME/emulator/emulator -avd Pixel_API_34 &
adb wait-for-device

# Run instrumented tests
cd shelfscan && gradle21w :androidApp:connectedAndroidTest
```

Test bookshelf image: `androidApp/src/androidTest/assets/test_bookshelf.jpg` (4000×3000 JPG).

## CI/CD

GitHub Actions workflow (`.github/workflows/android-release.yml`) builds a debug APK on every push to `main` and a signed release APK on GitHub Release events. CI uses `./gradlew` directly (not `gradle21w`). Signing requires repository secrets: `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`.

## Architecture

**Kotlin Multiplatform** with shared business logic across Android and iOS. Unidirectional data flow (Action → ViewModel → StateFlow → UI).

### Module layout

- `shelfscan/shared/` — KMP shared module (JVM + iOS targets, Kotlin 2.3.20)
- `shelfscan/androidApp/` — Android app (Compose, CameraX, ML Kit OCR)
- `shelfscan/iosApp/` — iOS app (SwiftUI, AVFoundation, Apple Vision OCR) — not yet in `settings.gradle.kts`

Only `:shared` and `:androidApp` are currently included in the Gradle build.

### Shared code layers (under `com.shelfscan.shared`)

| Layer | Package | Purpose |
|---|---|---|
| Core models | `core.model` | Pure domain objects: `MediaItem`, `ScanSession`, `Collection`, `ConfidenceScore`, `DetectedSpine`, `OcrResult` |
| Core result | `core.result` | `AppResult<T>` sealed class (Success/Failure) with `onSuccess`/`onFailure` extensions |
| Domain use cases | `domain.scan` | `ProcessCapturedImageUseCase` (orchestrates the full pipeline), `ParseDetectedItemUseCase`, `ScoreConfidenceUseCase` |
| Domain export | `domain.export` | `ExportCollectionUseCase` (CSV/JSON) |
| Data repositories | `data.repository` | `ScanRepository`, `CollectionRepository` interfaces + default implementations |
| Feature ViewModels | `feature.scan` | `ScanViewModel` — capture flow state machine |
| Feature ViewModels | `feature.review` | `ReviewViewModel` — review/edit/save/export |
| Platform interfaces | `platform` | `OcrEngine`, `ImagePreprocessor`, `MetadataLookupService` — implemented natively per platform |

### Recognition pipeline flow

Capture → Image Quality Check → Shelf Item Segmentation → Per-Item OCR → Title/Creator Parsing → Catalogue Lookup → Confidence Scoring → Review UI → Save/Export

The pipeline is orchestrated by `ProcessCapturedImageUseCase`, which calls platform interfaces (`ImagePreprocessor`, `OcrEngine`, `MetadataLookupService`) and shared use cases (`ParseDetectedItemUseCase`, `ScoreConfidenceUseCase`).

### Platform seam pattern

Platform-specific capabilities are defined as interfaces in `shared/platform/` and implemented natively:
- **Android:** `CameraXAdapter`, `MlKitOcrAdapter` (in `androidApp/`)
- **iOS:** `AVFoundationCameraAdapter`, `VisionOcrAdapter` (in `iosApp/`)
- **Test/stub:** `PassthroughImagePreprocessor`, `NoOpMetadataLookupService` (in `shared/platform/`) — used for testing and as defaults before platform implementations are wired up

### Confidence scoring formula

```
0.25 × segmentation + 0.25 × OCR + 0.20 × parser + 0.30 × catalogMatch
```

Bands: HIGH (≥0.75), MEDIUM (≥0.50), LOW (≥0.25), NEEDS_REVIEW (<0.25).

## Key Dependencies

- Kotlin 2.3.20, kotlinx-coroutines 1.10.2
- Android: CameraX 1.6.0, ML Kit Text Recognition 16.0.1, Compose BOM 2025.03.00
- iOS: AVFoundation, Apple Vision (native frameworks)
- Networking: Ktor 3.1.3

## Project Phase

Currently in **Phase 1: Books-only MVP** (Android first). See `docs/prd.md` for full product requirements and `docs/architecture.md` for architectural decisions and rationale.

## Conventions

- Tests live in `shared/src/commonTest/` and run on JVM target
- Use British spelling in documentation and user-facing text
- Use TDD where practical, especially for changes to existing shared logic
- Domain errors are typed via `sealed interface ScanError`
- ViewModels use `sealed interface` actions and immutable `data class` state
