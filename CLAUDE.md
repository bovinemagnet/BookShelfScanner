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

## Architecture

**Kotlin Multiplatform** with shared business logic across Android and iOS. Unidirectional data flow (Action → ViewModel → StateFlow → UI).

### Module layout

- `shelfscan/shared/` — KMP shared module (JVM + iOS targets, Kotlin 2.3.20)
- `shelfscan/androidApp/` — Android app (Compose, CameraX, ML Kit OCR)
- `shelfscan/iosApp/` — iOS app (SwiftUI, AVFoundation, Apple Vision OCR)

### Shared code layers (under `com.shelfscan.shared`)

| Layer | Package | Purpose |
|---|---|---|
| Core models | `core.model` | Pure domain objects: `MediaItem`, `ScanSession`, `Collection`, `ConfidenceScore`, `DetectedSpine`, `OcrResult` |
| Core result | `core.result` | Result wrapper |
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

## Conventions

- Tests live in `shared/src/commonTest/` and run on JVM target
- Use British spelling in documentation and user-facing text
- Use TDD where practical, especially for changes to existing shared logic
- Domain errors are typed via `sealed interface ScanError`
- ViewModels use `sealed interface` actions and immutable `data class` state
