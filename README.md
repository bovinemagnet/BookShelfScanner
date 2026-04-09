# ShelfScan

A Kotlin Multiplatform mobile app that scans photos of bookshelves to detect individual books, extract titles and authors via OCR, enrich results through catalogue lookup, and let users review, save, and export their collections.

Currently in early development — **Phase 1: Books-only MVP** (Android first).

## How It Works

1. Point your camera at a bookshelf and capture a photo (or import from gallery).
2. The app checks image quality, then segments individual book spines.
3. On-device OCR extracts visible text from each spine.
4. Titles and authors are parsed, then optionally enriched via catalogue lookup.
5. Results are presented in an editable review list with confidence scores.
6. Save to a named collection and export as CSV or JSON.

## Prerequisites

- **JDK 17** or later
- **Android SDK** with `compileSdk 36` and `minSdk 26`
- **Gradle 8+** (a Gradle wrapper is included in `shelfscan/`)

For iOS development (Phase 2):
- Xcode with iOS SDK
- macOS

## Building

All Gradle commands are run from the `shelfscan/` directory.

```bash
cd shelfscan

# Build the shared KMP module (JVM + iOS targets)
gradle :shared:build

# Build the Android debug APK
gradle :androidApp:assembleDebug

# Build the Android release APK (requires signing config — see CI/CD below)
gradle :androidApp:assembleRelease
```

Or using the included Gradle wrapper:

```bash
cd shelfscan
./gradlew :androidApp:assembleDebug
```

## Running Tests

Shared module tests are written in `shared/src/commonTest/` and run on the JVM target.

```bash
cd shelfscan

# Run all shared tests
gradle :shared:jvmTest

# Run a single test class
gradle :shared:jvmTest --tests "com.shelfscan.shared.domain.ParseDetectedItemUseCaseTest"
```

## Running Android Instrumented Tests

Instrumented tests (e.g. OCR, spine detection) require a running Android emulator or connected device.

### Setting up an emulator

```bash
# Install a system image
$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager "system-images;android-34;google_apis;x86_64"

# Create an AVD
$ANDROID_HOME/cmdline-tools/latest/bin/avdmanager create avd -n Pixel_API_34 \
  -k "system-images;android-34;google_apis;x86_64" -d pixel_6

# Start the emulator
$ANDROID_HOME/emulator/emulator -avd Pixel_API_34 &

# Wait for the device to finish booting
adb wait-for-device
```

### Running the tests

```bash
cd shelfscan

# Run all instrumented tests
gradle :androidApp:connectedAndroidTest

# Check connected devices
adb devices
```

### Test bookshelf image

Place a real bookshelf photo at `androidApp/src/androidTest/assets/test_bookshelf.jpg` for meaningful OCR test results.

## Project Structure

```
shelfscan/
  shared/          — KMP shared module (JVM + iOS targets)
  androidApp/      — Android app (Jetpack Compose, CameraX, ML Kit OCR)
  iosApp/          — iOS app (SwiftUI, AVFoundation, Apple Vision OCR) [not yet wired into Gradle]
```

### Shared Module Layers

| Layer | Package | Purpose |
|---|---|---|
| Core models | `core.model` | Pure domain objects — `MediaItem`, `ScanSession`, `Collection`, `ConfidenceScore`, etc. |
| Core result | `core.result` | `AppResult<T>` sealed class for error handling |
| Domain | `domain.scan` | Pipeline orchestration — `ProcessCapturedImageUseCase`, `ParseDetectedItemUseCase`, `ScoreConfidenceUseCase` |
| Domain | `domain.export` | `ExportCollectionUseCase` (CSV/JSON) |
| Data | `data.repository` | `ScanRepository`, `CollectionRepository` interfaces + default implementations |
| Feature | `feature.scan` | `ScanViewModel` — capture flow state machine |
| Feature | `feature.review` | `ReviewViewModel` — review/edit/save/export |
| Platform | `platform` | `OcrEngine`, `ImagePreprocessor`, `MetadataLookupService` — implemented natively per platform |

### Platform Implementations

Platform-specific capabilities are defined as interfaces in `shared/platform/` and implemented natively:

- **Android:** `CameraXAdapter`, `MlKitOcrAdapter` (in `androidApp/`)
- **iOS:** `AVFoundationCameraAdapter`, `VisionOcrAdapter` (in `iosApp/`)

### Recognition Pipeline

```
Capture → Quality Check → Spine Segmentation → Per-Item OCR
  → Title/Author Parsing → Catalogue Lookup → Confidence Scoring → Review UI
```

Orchestrated by `ProcessCapturedImageUseCase`.

### Confidence Scoring

```
0.25 × segmentation + 0.25 × OCR + 0.20 × parser + 0.30 × catalogMatch
```

Bands: **HIGH** (≥0.75), **MEDIUM** (≥0.50), **LOW** (≥0.25), **NEEDS_REVIEW** (<0.25).

## Key Dependencies

| Component | Library | Version |
|---|---|---|
| Language | Kotlin | 2.3.20 |
| Coroutines | kotlinx-coroutines | 1.10.2 |
| Android camera | CameraX | 1.6.0 |
| Android OCR | ML Kit Text Recognition | 16.0.1 |
| Android UI | Jetpack Compose BOM | 2025.03.00 |
| Networking | Ktor client | 3.1.3 |
| iOS camera | AVFoundation | native |
| iOS OCR | Apple Vision | native |

## CI/CD

A GitHub Actions workflow (`.github/workflows/android-release.yml`) handles automated builds:

- **On push to `main`:** builds a debug APK and uploads it as a workflow artefact.
- **On GitHub Release:** builds a signed release APK and attaches it to the release.

Release signing requires the following repository secrets:

| Secret | Description |
|---|---|
| `KEYSTORE_BASE64` | Base64-encoded `.jks` keystore file |
| `KEYSTORE_PASSWORD` | Keystore password |
| `KEY_ALIAS` | Signing key alias |
| `KEY_PASSWORD` | Signing key password |

## Architecture

The app follows a **Kotlin Multiplatform** architecture with shared business logic across Android and iOS, using **unidirectional data flow** (Action → ViewModel → StateFlow → UI). Domain errors are typed via `sealed interface ScanError`. ViewModels use `sealed interface` actions and immutable `data class` state.

See `docs/architecture.md` for full architectural decisions and rationale, and `docs/prd.md` for the product requirements document.

## Licence

TBD
