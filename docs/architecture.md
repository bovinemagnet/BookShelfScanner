Below is an opinionated architecture document for the **ShelfScan** app, using **Kotlin as the primary implementation language**.

The design is based on a few current facts that matter for this decision: Kotlin Multiplatform is stable for Android and iOS, Compose Multiplatform is production-ready for Android and iOS, Android’s recommended camera stack for new apps is CameraX, ML Kit supports on-device text recognition on Android and iOS, and Apple provides native OCR through Vision’s `VNRecognizeTextRequest`. ([Kotlin][1])

---

# ShelfScan Kotlin Architecture Document

## 1. Purpose

This document defines the target architecture for a mobile application that allows a user to:

* take a photo of a shelf containing books, movies, or CDs
* detect individual items from the shelf image
* extract titles and creator names from visible text
* enrich extracted data through metadata lookup
* review and correct results
* save the recognized items into a personal catalog

This architecture is optimized for:

* Kotlin-first development
* shared business logic across Android and iOS
* native-quality camera and OCR integration
* offline-capable scanning with optional cloud enrichment
* incremental delivery from MVP to production scale

---

## 2. Executive Recommendation

### Recommended stack

* **Language:** Kotlin
* **Mobile architecture:** Kotlin Multiplatform
* **Shared UI:** Compose Multiplatform for most screens
* **Android camera:** CameraX
* **Android OCR:** ML Kit Text Recognition
* **iOS camera:** AVFoundation
* **iOS OCR:** Apple Vision
* **Networking:** Ktor client in shared code
* **Persistence:** shared database abstraction, with Room for KMP as the preferred direction
* **Backend:** Spring Boot in Kotlin for metadata normalization, sync, and account features

This is the right architecture because the product’s complexity is in the **image pipeline and recognition workflow**, not in rendering ordinary forms and lists. Kotlin Multiplatform lets you share domain, parsing, ranking, networking, sync, and persistence logic while still keeping the camera and OCR edges close to native APIs. CameraX is the recommended Android camera abstraction for new apps, and both ML Kit and Apple Vision support on-device OCR, which is important for responsiveness and privacy. Ktor’s client is multiplatform, which makes it a natural fit for shared networking code. Android now also documents Room support for Kotlin Multiplatform projects. ([Android Developers][2])

---

## 3. Architecture Principles

1. **Native where it matters**

   * camera preview, capture, frame control, image orientation, and OCR adapters remain platform-native

2. **Shared where it pays off**

   * business rules, scan orchestration, OCR result parsing, confidence scoring, metadata matching, persistence rules, sync, and export are shared

3. **Offline-first**

   * a scan should still produce useful OCR results without network access
   * metadata enrichment is additive, not mandatory

4. **Review before commit**

   * all scan output is user-reviewable before it becomes catalog truth

5. **Confidence-driven UX**

   * every recognized item carries confidence and provenance
   * low-confidence results are highlighted, not silently accepted

6. **Privacy by design**

   * raw shelf images stay local by default
   * server upload is explicit and optional

---

## 4. Scope of This Architecture

### Included

* iOS and Android client apps
* shared mobile business logic
* local persistence
* optional backend services
* recognition pipeline orchestration
* export and sync model

### Not included

* ML model training platform
* marketplace features
* social/community features
* collectible edition verification
* pricing/resale analytics

---

## 5. Target Architecture Overview

## 5.1 High-level view

```text
+------------------------------------------------------+
|                    Mobile Client                      |
|------------------------------------------------------|
| UI Layer (Compose Multiplatform)                     |
| Scan Review | Library | Search | Collections         |
|------------------------------------------------------|
| Shared Application Layer (KMP)                       |
| Scan Orchestrator | OCR Parser | Matcher | Sync      |
|------------------------------------------------------|
| Shared Domain Layer (KMP)                            |
| MediaItem | ScanSession | Collection | Confidence    |
|------------------------------------------------------|
| Shared Data Layer (KMP)                              |
| Repositories | API clients | Persistence adapters    |
|------------------------------------------------------|
| Platform Layer                                       |
| Android: CameraX + ML Kit                            |
| iOS: AVFoundation + Vision                           |
+------------------------------------------------------+

                 optional network

+------------------------------------------------------+
|                    Backend Services                   |
|------------------------------------------------------|
| Auth | Catalog Lookup | Metadata Normalization       |
| User Collections Sync | Export Jobs | Telemetry      |
+------------------------------------------------------+
```

---

## 6. Architectural Decision Summary

## ADR-001: Use Kotlin Multiplatform

**Decision:** use Kotlin Multiplatform for shared code across Android and iOS.

**Why:**

* share business logic, state models, networking, parsing, ranking, sync
* preserve native capability for camera and OCR
* good fit for a JVM-oriented team
* lowers duplication without forcing a fully abstracted camera stack

**Trade-off:**

* iOS debugging and bridging are still more complex than pure native iOS
* developers need discipline around expect/actual boundaries

Kotlin Multiplatform is stable for Android and iOS, and JetBrains positions it for sharing app logic while keeping native integration where needed. ([Kotlin][1])

## ADR-002: Share most UI, but keep the scan screen platform-specialized

**Decision:** use Compose Multiplatform for the majority of screens, but keep the camera capture surface and some scan controls platform-specific.

**Why:**

* most of the app is standard UI: review lists, edit forms, search, collections, settings
* the scan surface is where platform camera behavior matters most
* this reduces risk in the hardest part of the app

**Trade-off:**

* one feature has two UI implementations at the edge
* some code duplication around capture controls

Compose Multiplatform is production-ready on Android and iOS, but that does not mean every feature should be shared. For camera-heavy apps, native capture integration is still the safer architectural choice. ([Kotlin][3])

## ADR-003: Use on-device OCR first

**Decision:** OCR runs on-device first; cloud enrichment is optional.

**Why:**

* faster feedback
* works offline
* better privacy posture
* lower backend cost

ML Kit provides on-device OCR on Android and iOS, and Apple Vision provides native text recognition through `VNRecognizeTextRequest`. ML Kit also notes that image quality strongly affects accuracy, which supports adding an explicit image-quality gate in the app. ([Google for Developers][4])

## ADR-004: Use CameraX on Android and AVFoundation on iOS

**Decision:** do not try to abstract the capture stack too early.

**Why:**

* CameraX is Google’s recommended stack for new Android camera apps
* AVFoundation is the native capture framework on iOS
* both are better suited than forcing a lowest-common-denominator abstraction for a recognition-first app

Android recommends CameraX for most developers and specifically recommends it for new apps. Apple’s AVFoundation provides the capture architecture for photo and video capture on iOS. ([Android Developers][2])

---

## 7. System Context

### Actors

* end user
* metadata providers
* optional backend service
* analytics/crash tooling

### External dependencies

* device camera
* device filesystem/photo library
* OCR engines
* metadata APIs
* optional auth provider
* optional cloud sync

---

## 8. Mobile Module Structure

I would structure the project like this:

```text
shelfscan/
  androidApp/
  iosApp/
  shared/
    core/
      common/
      model/
      result/
      logging/
      time/
    domain/
      scan/
      catalog/
      collection/
      export/
    data/
      api/
      db/
      repository/
      mapper/
    feature/
      scan/
      review/
      library/
      search/
      collection/
      settings/
    platform/
      camera/
      ocr/
      image/
      file/
      analytics/
```

### Module intent

#### `shared/core/common`

Foundational cross-cutting utilities:

* dispatcher providers
* error/result wrappers
* ids
* date/time wrappers
* common extensions

#### `shared/core/model`

Pure domain objects:

* `MediaItem`
* `ScanSession`
* `DetectedSpine`
* `RecognizedTextBlock`
* `CatalogMatch`
* `Collection`
* `ConfidenceScore`

#### `shared/domain/*`

Use cases and domain services:

* `StartScanSession`
* `ProcessCapturedImage`
* `ParseRecognitionCandidates`
* `MatchCatalogEntries`
* `SaveReviewedItems`
* `ExportCollection`

#### `shared/data/*`

Repository implementations and adapters:

* OCR adapter facade
* metadata API client
* local database adapters
* file/export services
* sync engine

#### `shared/feature/*`

Presentation models, state machines, screen coordinators:

* scan
* review
* library
* search
* settings

#### `shared/platform/*`

Interfaces with expect/actual or injected adapters:

* camera contracts
* OCR contracts
* image pre-processing
* local file access
* analytics bridge

---

## 9. Platform Split

## 9.1 Shared responsibilities

These should be shared:

* scan workflow orchestration
* result parsing
* OCR normalization
* confidence scoring
* catalog lookup
* collection management
* export generation
* sync logic
* business validation
* analytics event definitions
* screen state models for non-camera screens

## 9.2 Android-specific responsibilities

* CameraX integration
* capture lifecycle binding
* image orientation handling for Android image sources
* ML Kit text recognizer adapter
* Android permissions
* Android file/media store integration

## 9.3 iOS-specific responsibilities

* AVFoundation capture session
* photo capture pipeline
* Vision text recognition adapter
* iOS permissions
* iOS image/Photos integration

---

## 10. UI Architecture

## 10.1 UI pattern

Use **unidirectional data flow** with:

* `ViewModel` / screen model per feature
* immutable state objects
* intent/actions from UI
* reducer-like state transitions
* suspend use cases for long-running work

### Why

Recognition flows are asynchronous and multi-stage. You want deterministic UI state, especially for:

* capture started
* image quality failed
* OCR in progress
* matching in progress
* review ready
* save failed
* retry pending

## 10.2 Suggested screen list

* Splash / startup
* Permissions onboarding
* Home
* Scan capture
* Scan review
* Item edit
* Collections
* Collection details
* Search
* Export
* Settings
* Diagnostics / developer screen

## 10.3 Shared versus native UI

### Shared with Compose Multiplatform

* home
* collections
* search
* review list
* item editor
* export flow
* settings

### Native/platform-specialized

* live camera preview screen
* certain advanced capture controls
* platform-specific photo picker integrations

This is my strong recommendation. Sharing the live camera surface is where teams often burn time for little payoff.

---

## 11. Recognition Pipeline

This is the heart of the product.

## 11.1 Pipeline stages

```text
Capture Image
   ->
Quality Check
   ->
Shelf Region Detection
   ->
Item Segmentation
   ->
Per-Item Image Normalization
   ->
OCR
   ->
Title/Creator Parsing
   ->
Media Type Classification
   ->
Catalog Lookup
   ->
Confidence Scoring
   ->
Review UI
   ->
Save / Export / Sync
```

## 11.2 Stage definitions

### 1. Capture image

Input:

* camera photo
* gallery import

Output:

* `CapturedImage`

### 2. Quality check

Checks:

* blur score
* brightness
* skew/perspective
* clipping
* estimated text readability

Output:

* `ImageQualityAssessment`

If below threshold:

* warn user
* allow retry
* optionally allow continue anyway

### 3. Shelf region detection

Identify probable shelf content region and ignore:

* walls
* decorative gaps
* hands
* furniture edges
* glare regions

Output:

* `ShelfRegion`

### 4. Item segmentation

Find likely item boundaries:

* spine separators
* front-cover regions
* grouped box sets
* partial occlusions

Output:

* `List<DetectedSpine>`

### 5. Per-item normalization

For each candidate:

* rotate vertical text
* crop margins
* improve contrast
* de-noise
* sharpen lightly
* keep original crop for provenance

Output:

* `NormalizedItemImage`

### 6. OCR

Use platform OCR:

* Android -> ML Kit
* iOS -> Vision

Output:

* `List<RecognizedTextBlock>`

### 7. Title/creator parsing

Convert raw OCR into likely structured fields:

* title
* subtitle
* author/artist/director
* edition hints
* series hints

This logic belongs in shared Kotlin, not in the OCR adapters.

### 8. Media type classification

Based on:

* OCR terms
* object shape heuristics
* metadata match patterns
* user-selected scan mode if present

Output:

* `BOOK | MOVIE | CD | UNKNOWN`

### 9. Catalog lookup

Search metadata provider(s) using:

* title candidate
* creator candidate
* media type
* optional series or year tokens

Output:

* ranked `CatalogMatch` list

### 10. Confidence scoring

Combine:

* segmentation confidence
* OCR confidence
* parser certainty
* catalog match score
* text completeness

Output:

* final user-facing confidence band

### 11. Review and commit

User edits low-confidence results before saving.

---

## 12. Core Domain Model

```kotlin
data class MediaItem(
    val id: String,
    val mediaType: MediaType,
    val title: String?,
    val creatorName: String?,
    val subtitle: String?,
    val normalizedTitle: String?,
    val normalizedCreatorName: String?,
    val confidence: ConfidenceScore,
    val source: ItemSource,
    val cropRef: String?,
    val rawText: List<String>,
    val externalIds: Map<String, String> = emptyMap()
)

enum class MediaType {
    BOOK, MOVIE, CD, UNKNOWN
}

data class ScanSession(
    val id: String,
    val createdAt: Instant,
    val sourceImageRef: String,
    val quality: ImageQualityAssessment,
    val status: ScanStatus,
    val detectedItems: List<MediaItem> = emptyList()
)

data class CatalogMatch(
    val source: CatalogSource,
    val mediaType: MediaType,
    val title: String,
    val creatorName: String?,
    val year: Int?,
    val score: Double,
    val externalId: String
)

data class ConfidenceScore(
    val value: Double,
    val band: ConfidenceBand,
    val reasons: List<String>
)
```

---

## 13. Shared Interfaces

These interfaces should define the seam between shared business logic and native implementations.

```kotlin
interface OcrEngine {
    suspend fun recognizeText(image: ProcessedImage): OcrResult
}

interface ImagePreprocessor {
    suspend fun normalizeForOcr(image: CapturedImage): ProcessedImage
    suspend fun detectShelfItems(image: CapturedImage): List<DetectedSpine>
}

interface MetadataLookupService {
    suspend fun search(
        mediaType: MediaType,
        title: String?,
        creatorName: String?
    ): List<CatalogMatch>
}

interface ScanRepository {
    suspend fun saveSession(session: ScanSession)
    suspend fun getSession(id: String): ScanSession?
}

interface CollectionRepository {
    suspend fun saveItems(collectionId: String, items: List<MediaItem>)
    suspend fun getCollectionItems(collectionId: String): List<MediaItem>
}
```

---

## 14. Recommended Package Design

I would avoid a package layout like `controller/service/repository` on mobile. That is backend thinking and usually ages badly in apps.

Use **feature + layer**:

```text
feature/scan/
  ScanViewModel.kt
  ScanState.kt
  ScanAction.kt
  ScanScreen.kt
  ScanCoordinator.kt

domain/scan/
  ProcessCapturedImageUseCase.kt
  ParseDetectedItemUseCase.kt
  ScoreConfidenceUseCase.kt

data/repository/
  DefaultScanRepository.kt
```

This aligns the codebase around user value rather than technical stereotypes.

---

## 15. Local Persistence

## 15.1 Persistence goals

* save reviewed scan sessions
* save collections and items
* cache metadata lookups
* support offline library browsing
* support idempotent rescan merge

## 15.2 Recommendation

Use a local relational store with:

* `scan_session`
* `detected_item`
* `collection`
* `collection_item`
* `metadata_cache`

Android now documents Room setup for Kotlin Multiplatform, which makes Room a credible path for shared database code if you want to stay close to mainstream Android tooling. ([Android Developers][5])

## 15.3 Persistence rules

* raw shelf image path stored separately from item rows
* cropped item image references stored as file refs, not blobs in DB
* metadata cache has TTL/versioning
* user edits always override machine suggestions

---

## 16. Networking

## 16.1 Recommendation

Use **Ktor client** in shared code.

Why:

* multiplatform
* good Kotlin ergonomics
* clean plugin model
* easy mocking for tests

Ktor’s client is documented for multiplatform projects and supports platform-specific engines per target. ([Ktor Framework][6])

## 16.2 Backend responsibilities

The app can launch without a backend, but once you move beyond MVP, I recommend a backend for:

* metadata normalization
* account-based sync
* export jobs
* abuse control / API key shielding
* telemetry
* future image-assisted enrichment

## 16.3 Backend stack

Since your background is already JVM and Spring-heavy, I would use:

* **Spring Boot in Kotlin**
* PostgreSQL
* object storage only if explicit image upload is later enabled

I would not force Ktor server unless you specifically want a smaller all-Kotlin stack. For your likely team profile, Spring Boot is the lower-risk option.

---

## 17. Concurrency Model

Use Kotlin coroutines throughout shared code.

### Principles

* `Dispatchers.Default` for parsing, ranking, heuristics
* `Dispatchers.IO` for local DB/file/network
* platform capture callbacks immediately hand off to coroutine-backed pipelines
* all UI observes immutable state streams

### Flow model

* `StateFlow` for screen state
* `SharedFlow` for one-off UI events
* bounded work queue for scan jobs
* cancellation propagated when user retakes image

### Important rule

Never run OCR and catalog lookup as opaque UI-blocking chains. The review screen should progressively enrich.

Example:

1. show detected crops
2. show raw OCR
3. update with parsed title/creator
4. update with matched metadata

That makes the app feel dramatically faster.

---

## 18. Error Handling Strategy

Use typed domain errors.

```kotlin
sealed interface ScanError {
    data object CameraUnavailable : ScanError
    data object PermissionDenied : ScanError
    data object ImageTooBlurry : ScanError
    data object OcrFailed : ScanError
    data object MetadataLookupFailed : ScanError
    data object SaveFailed : ScanError
}
```

### UX policy

* quality failures -> corrective action
* OCR failures -> partial results + manual entry path
* metadata failures -> keep OCR-only result
* save failures -> retry with local queue

---

## 19. Confidence Model

Do not return a single magic score from OCR and pretend it means truth.

Use a composed score:

```text
finalConfidence =
  0.25 * segmentationConfidence +
  0.25 * ocrConfidence +
  0.20 * parserConfidence +
  0.30 * catalogMatchConfidence
```

Expose user-friendly bands:

* High
* Medium
* Low
* Needs Review

Every low-confidence record should surface reasons:

* blurred text
* weak creator match
* multiple possible titles
* partial spine visible

This matters more than raw AI cleverness. Users trust systems that explain uncertainty.

---

## 20. Security and Privacy

### Default posture

* images remain on device
* OCR runs on device
* only normalized metadata queries go to backend/provider
* user explicitly opts in to image upload

### Data handling

* encrypt sensitive local data where required by platform policy
* do not log raw OCR text in production without opt-in diagnostics
* redact personal collection names from analytics

### Auth

For MVP:

* anonymous local mode allowed

For production:

* optional signed-in sync mode
* token-based auth
* device-linked local cache

ML Kit emphasizes on-device execution for mobile ML scenarios, which supports this privacy-first approach. ([Google for Developers][7])

---

## 21. Observability

### Metrics

* scan started
* scan quality failed
* OCR completed
* catalog match accepted
* manual correction count
* save completed
* export completed

### Diagnostics

Keep an internal diagnostic payload:

* image quality score
* item count
* OCR latency
* metadata latency
* top confidence reasons

Do not capture full shelf images in telemetry by default.

---

## 22. Testing Strategy

## 22.1 Shared code tests

* parser unit tests
* confidence scoring tests
* catalog ranking tests
* repository contract tests
* export tests

## 22.2 Platform tests

### Android

* CameraX integration tests
* ML Kit adapter tests
* permission flows

### iOS

* AVFoundation capture tests
* Vision adapter tests
* lifecycle/permission flows

## 22.3 Golden tests

Build a test corpus of:

* clear shelves
* skewed shelves
* low light
* mixed books/DVDs/CDs
* decorative fonts
* partial occlusion

This product will fail or succeed based on corpus discipline, not on architecture diagrams.

## 22.4 Network tests

Ktor supports testability through client mocking, which is useful for shared API client tests. ([Ktor Framework][8])

---

## 23. Performance Targets

### MVP targets

* camera preview ready: under 1 second after entering scan screen
* first OCR results visible: under 3 seconds after capture on modern devices
* full enriched review list: under 8 seconds for a typical shelf image
* save reviewed session: under 500 ms local only

### Optimization rules

* downscale only as much as OCR quality allows
* process per detected item in parallel with a cap
* cache previous metadata matches
* separate “fast OCR” from “deep enrichment”

---

## 24. Delivery Phases

## Phase 1: Books-only MVP

* Android first
* CameraX
* ML Kit OCR
* local-only persistence
* manual review and save
* CSV export

## Phase 2: iOS parity

* AVFoundation
* Vision OCR
* shared review/library UI
* shared networking and persistence logic

## Phase 3: Catalog enrichment

* backend metadata normalization
* deduplication across scans
* signed-in sync

## Phase 4: Mixed media

* movies and CDs
* better media classification
* box set handling

My strong opinion: **start with books only**. Books are the cleanest path to proving the segmentation/OCR/review loop. Once that works well, expand to movies and CDs.

---

## 25. Example Runtime Flow

```text
User taps Scan
 -> native camera screen opens
 -> user captures photo
 -> platform adapter stores image reference
 -> shared ProcessCapturedImage use case starts
 -> shared quality gate runs
 -> shared segmentation runs
 -> per-item OCR dispatched to platform OcrEngine
 -> shared parser builds candidate title/creator pairs
 -> shared matcher queries metadata service
 -> review state emitted incrementally
 -> user edits low-confidence rows
 -> SaveReviewedItems persists collection entries
```

---

## 26. Suggested Build Baseline

Use:

* current stable Kotlin
* Kotlin Multiplatform shared module
* Compose Multiplatform for shared UI
* Ktor client
* Room-for-KMP path for persistence if you want maximal sharing

Kotlin’s current stable line and the KMP/Compose compatibility guidance are documented by JetBrains and Android’s Kotlin Multiplatform guidance is now part of official Android docs as well. ([Kotlin][9])

---

## 27. Final Recommendation

If I were leading this build, I would do it this way:

* **Kotlin Multiplatform for shared logic**
* **Compose Multiplatform for all non-camera screens**
* **native camera screen on each platform**
* **CameraX + ML Kit on Android**
* **AVFoundation + Vision on iOS**
* **Spring Boot Kotlin backend later, not on day one**
* **books-only MVP first**

That is the best balance of:

* delivery speed
* technical control
* recognition quality
* maintainability
* fit with your JVM/Kotlin background

---

If you want, I can turn this into a **formal engineering architecture RFC** with:

* a C4 model
* module dependency rules
* Gradle setup
* package naming
* sample interfaces and class skeletons
* an MVP backlog tied to this design.

[1]: https://kotlinlang.org/docs/multiplatform/supported-platforms.html?utm_source=chatgpt.com "Stability of supported platforms | Kotlin Multiplatform"
[2]: https://developer.android.com/media/camera/camerax?utm_source=chatgpt.com "CameraX overview | Android media"
[3]: https://kotlinlang.org/compose-multiplatform/?utm_source=chatgpt.com "Compose Multiplatform – Beautiful UIs Everywhere"
[4]: https://developers.google.com/ml-kit/vision/text-recognition/v2/android?utm_source=chatgpt.com "Recognize text in images with ML Kit on Android"
[5]: https://developer.android.com/kotlin/multiplatform/room?utm_source=chatgpt.com "Set up Room Database for KMP | Kotlin"
[6]: https://ktor.io/docs/client-create-multiplatform-application.html?utm_source=chatgpt.com "Creating a cross-platform mobile application"
[7]: https://developers.google.com/ml-kit?utm_source=chatgpt.com "ML Kit"
[8]: https://ktor.io/docs/client-testing.html?utm_source=chatgpt.com "Testing in Ktor Client"
[9]: https://kotlinlang.org/docs/multiplatform/multiplatform-compatibility-guide.html?utm_source=chatgpt.com "Compatibility guide for Kotlin Multiplatform"
