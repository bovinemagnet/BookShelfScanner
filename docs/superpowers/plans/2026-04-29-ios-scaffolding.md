# iOS Scaffolding Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add an `iosMain` source set with Kotlin bridges that let Swift implement `OcrEngine`/`ImagePreprocessor` via callback interfaces, and rewrite the Swift skeleton to call shared `ProcessCapturedImageUseCase` through those bridges.

**Architecture:** Suspend bridge logic lives in `commonMain` (`CallbackBackedOcrEngine`, `CallbackBackedImagePreprocessor`) so it is JVM-testable on Linux. `iosMain` holds two Swift-facing interfaces (`IosOcrCallback`, `IosImagePreprocessorCallback`), thin adapters that wrap them into the `commonMain` bridges, and a single factory `IosShelfScanFactory`. Swift implements the callback interfaces (wrapping the existing `VisionOcrAdapter`) and constructs use cases through the factory. The XCFramework is linked into Xcode via a helper script — `project.pbxproj` is not edited.

**Tech Stack:** Kotlin Multiplatform 2.3.20, kotlinx.coroutines 1.10.2 (`suspendCancellableCoroutine`), kotlin.test + JUnit (commonTest runs on JVM), Swift 5.9 + SwiftUI (iOS 17), Ktor 3.1.3 (already on Kotlin/Native via existing `OpenLibraryMetadataLookupService`).

**Spec:** [`docs/superpowers/specs/2026-04-29-ios-scaffolding-design.md`](../specs/2026-04-29-ios-scaffolding-design.md)

**Verification on Linux:**
- All `commonMain` and JVM tests run normally.
- The `:androidApp:assembleDebug` regression check runs normally.
- Kotlin/Native iOS target compilation is **best-effort**: `gradle21w :shared:compileKotlinIosArm64` may succeed (the K/N compiler can sometimes do cross-platform klib compilation from Linux) or it may fail because the Apple SDK toolchain isn't available. Either outcome is acceptable for this plan — we document the result.
- Swift code is reviewed by source only; no Swift compilation step is available on Linux.

---

## File Structure

### New files in `:shared/commonMain`

```
shelfscan/shared/src/commonMain/kotlin/com/shelfscan/shared/platform/
├── FfiOcrLine.kt                       (DTO: text, confidence, bounding-box primitives)
├── FfiDetectedSpine.kt                 (DTO: id, cropRef, bounding-box primitives, confidence)
├── CallbackOcrException.kt             (RuntimeException for OCR bridge failures)
├── CallbackImagePreprocessorException.kt (RuntimeException for preprocessor bridge failures)
├── CallbackBackedOcrEngine.kt          (suspend OcrEngine; takes plain function refs; JVM-testable)
└── CallbackBackedImagePreprocessor.kt  (suspend ImagePreprocessor; takes plain function refs; JVM-testable)
```

### New files in `:shared/iosMain`

```
shelfscan/shared/src/iosMain/kotlin/com/shelfscan/shared/platform/
├── IosOcrCallback.kt                   (non-suspend interface, Swift implements)
├── IosImagePreprocessorCallback.kt     (non-suspend interface, Swift implements)
├── SwiftBackedOcrEngine.kt             (5-line adapter delegating to CallbackBackedOcrEngine)
├── SwiftBackedImagePreprocessor.kt     (5-line adapter, same shape)
└── IosShelfScanFactory.kt              (singleton; Swift entry point)
```

### New files in `:shared/commonTest`

```
shelfscan/shared/src/commonTest/kotlin/com/shelfscan/shared/platform/
├── CallbackBackedOcrEngineTest.kt
└── CallbackBackedImagePreprocessorTest.kt
```

### New + modified files in `iosApp/`

```
shelfscan/iosApp/iosApp/
├── platform/                                    (NEW directory)
│   ├── SwiftIosOcrCallback.swift                (NEW — wraps VisionOcrAdapter)
│   └── SwiftIosImagePreprocessorCallback.swift  (NEW — passthrough impl)
├── ui/
│   ├── ScanView.swift                           (MODIFIED — ScanViewModel calls shared use case)
│   └── ReviewView.swift                         (MODIFIED — accepts shared MediaItem)
└── README.md                                    (MODIFIED — points at helper script)
```

### New helper script

```
shelfscan/Scripts/link-shared-xcframework.sh    (NEW — builds XCFramework, prints manual-link steps)
```

---

## Task 1: Add commonMain FFI DTOs and exceptions

**Files:**
- Create: `shelfscan/shared/src/commonMain/kotlin/com/shelfscan/shared/platform/FfiOcrLine.kt`
- Create: `shelfscan/shared/src/commonMain/kotlin/com/shelfscan/shared/platform/FfiDetectedSpine.kt`
- Create: `shelfscan/shared/src/commonMain/kotlin/com/shelfscan/shared/platform/CallbackOcrException.kt`
- Create: `shelfscan/shared/src/commonMain/kotlin/com/shelfscan/shared/platform/CallbackImagePreprocessorException.kt`

These are pure data containers + exception types. No tests — they have no behaviour.

- [ ] **Step 1: Create `FfiOcrLine.kt`**

```kotlin
package com.shelfscan.shared.platform

/**
 * FFI-friendly DTO carrying a single recognised text line across the Swift/Kotlin boundary.
 *
 * Bounding box is flattened to primitive `Float` fields because Swift→Kotlin generics over
 * optional value types add friction with no benefit. `hasBoundingBox = false` indicates the
 * underlying recognised block had no spatial information.
 */
data class FfiOcrLine(
    val text: String,
    val confidence: Float,
    val hasBoundingBox: Boolean,
    val boundingBoxLeft: Float,
    val boundingBoxTop: Float,
    val boundingBoxRight: Float,
    val boundingBoxBottom: Float,
)
```

- [ ] **Step 2: Create `FfiDetectedSpine.kt`**

```kotlin
package com.shelfscan.shared.platform

/**
 * FFI-friendly DTO describing a single detected spine (or full-image segment) returned
 * from the iOS-side image preprocessor.
 */
data class FfiDetectedSpine(
    val id: String,
    val cropRef: String,
    val boundingBoxLeft: Float,
    val boundingBoxTop: Float,
    val boundingBoxRight: Float,
    val boundingBoxBottom: Float,
    val confidence: Double,
)
```

- [ ] **Step 3: Create `CallbackOcrException.kt`**

```kotlin
package com.shelfscan.shared.platform

class CallbackOcrException(message: String) : RuntimeException(message)
```

- [ ] **Step 4: Create `CallbackImagePreprocessorException.kt`**

```kotlin
package com.shelfscan.shared.platform

class CallbackImagePreprocessorException(message: String) : RuntimeException(message)
```

- [ ] **Step 5: Verify shared still compiles**

Run: `cd shelfscan && gradle21w :shared:compileKotlinJvm`

Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add shelfscan/shared/src/commonMain/kotlin/com/shelfscan/shared/platform/FfiOcrLine.kt \
        shelfscan/shared/src/commonMain/kotlin/com/shelfscan/shared/platform/FfiDetectedSpine.kt \
        shelfscan/shared/src/commonMain/kotlin/com/shelfscan/shared/platform/CallbackOcrException.kt \
        shelfscan/shared/src/commonMain/kotlin/com/shelfscan/shared/platform/CallbackImagePreprocessorException.kt
git commit -m "Add FFI DTOs and bridge exceptions for callback-backed platform adapters"
```

---

## Task 2: TDD CallbackBackedOcrEngine

**Files:**
- Create: `shelfscan/shared/src/commonTest/kotlin/com/shelfscan/shared/platform/CallbackBackedOcrEngineTest.kt`
- Create: `shelfscan/shared/src/commonMain/kotlin/com/shelfscan/shared/platform/CallbackBackedOcrEngine.kt`

- [ ] **Step 1: Write the failing test file**

```kotlin
package com.shelfscan.shared.platform

import com.shelfscan.shared.core.model.ProcessedImage
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class CallbackBackedOcrEngineTest {

    private val sampleImage = ProcessedImage(ref = "/tmp/img.jpg", widthPx = 100, heightPx = 200)

    @Test
    fun `resumes successfully when callback fires onSuccess`() = runBlocking {
        val engine = CallbackBackedOcrEngine(
            recognize = { _, onSuccess, _ ->
                onSuccess(
                    "hello world",
                    listOf(
                        FfiOcrLine(
                            text = "hello world",
                            confidence = 0.9f,
                            hasBoundingBox = false,
                            boundingBoxLeft = 0f,
                            boundingBoxTop = 0f,
                            boundingBoxRight = 0f,
                            boundingBoxBottom = 0f,
                        )
                    )
                )
            }
        )

        val result = engine.recognizeText(sampleImage)

        assertEquals("hello world", result.rawText)
        assertEquals(1, result.blocks.size)
        assertEquals("hello world", result.blocks[0].text)
        assertEquals(0.9f, result.blocks[0].confidence)
        assertNull(result.blocks[0].boundingBox)
    }

    @Test
    fun `propagates CallbackOcrException when callback fires onError`() = runBlocking {
        val engine = CallbackBackedOcrEngine(
            recognize = { _, _, onError -> onError("vision failed") }
        )

        val ex = assertFailsWith<CallbackOcrException> {
            engine.recognizeText(sampleImage)
        }
        assertEquals("vision failed", ex.message)
    }

    @Test
    fun `passes the image ref through to the callback as imagePath`() = runBlocking {
        var capturedPath = ""
        val engine = CallbackBackedOcrEngine(
            recognize = { path, onSuccess, _ ->
                capturedPath = path
                onSuccess("", emptyList())
            }
        )

        engine.recognizeText(sampleImage)

        assertEquals("/tmp/img.jpg", capturedPath)
    }

    @Test
    fun `maps bounding box when hasBoundingBox is true`() = runBlocking {
        val engine = CallbackBackedOcrEngine(
            recognize = { _, onSuccess, _ ->
                onSuccess(
                    "boxed",
                    listOf(
                        FfiOcrLine(
                            text = "boxed",
                            confidence = 0.8f,
                            hasBoundingBox = true,
                            boundingBoxLeft = 10f,
                            boundingBoxTop = 20f,
                            boundingBoxRight = 110f,
                            boundingBoxBottom = 60f,
                        )
                    )
                )
            }
        )

        val result = engine.recognizeText(sampleImage)
        val box = result.blocks[0].boundingBox
        assertEquals(10f, box?.left)
        assertEquals(20f, box?.top)
        assertEquals(110f, box?.right)
        assertEquals(60f, box?.bottom)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd shelfscan && gradle21w :shared:jvmTest --tests "com.shelfscan.shared.platform.CallbackBackedOcrEngineTest"`

Expected: FAIL with "Unresolved reference: CallbackBackedOcrEngine".

- [ ] **Step 3: Implement `CallbackBackedOcrEngine`**

```kotlin
package com.shelfscan.shared.platform

import com.shelfscan.shared.core.model.BoundingBox
import com.shelfscan.shared.core.model.OcrResult
import com.shelfscan.shared.core.model.ProcessedImage
import com.shelfscan.shared.core.model.RecognizedTextBlock
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Suspend bridge for `OcrEngine` implementations whose recognition step is callback-based.
 *
 * Lives in `commonMain` so it is JVM-testable. The `iosMain` `SwiftBackedOcrEngine` adapts a
 * Swift-implemented `IosOcrCallback` into the `recognize` function reference this class
 * accepts.
 */
class CallbackBackedOcrEngine(
    private val recognize: (
        imagePath: String,
        onSuccess: (rawText: String, lines: List<FfiOcrLine>) -> Unit,
        onError: (message: String) -> Unit,
    ) -> Unit,
) : OcrEngine {

    override suspend fun recognizeText(image: ProcessedImage): OcrResult =
        suspendCancellableCoroutine { cont ->
            recognize(
                image.ref,
                { rawText, lines ->
                    val blocks = lines.map { line ->
                        RecognizedTextBlock(
                            text = line.text,
                            confidence = line.confidence,
                            boundingBox = if (line.hasBoundingBox) {
                                BoundingBox(
                                    left = line.boundingBoxLeft,
                                    top = line.boundingBoxTop,
                                    right = line.boundingBoxRight,
                                    bottom = line.boundingBoxBottom,
                                )
                            } else null,
                        )
                    }
                    cont.resume(OcrResult(blocks = blocks, rawText = rawText))
                },
                { message ->
                    cont.resumeWithException(CallbackOcrException(message))
                },
            )
        }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `cd shelfscan && gradle21w :shared:jvmTest --tests "com.shelfscan.shared.platform.CallbackBackedOcrEngineTest"`

Expected: BUILD SUCCESSFUL, 4 tests passed.

- [ ] **Step 5: Commit**

```bash
git add shelfscan/shared/src/commonMain/kotlin/com/shelfscan/shared/platform/CallbackBackedOcrEngine.kt \
        shelfscan/shared/src/commonTest/kotlin/com/shelfscan/shared/platform/CallbackBackedOcrEngineTest.kt
git commit -m "Add CallbackBackedOcrEngine: callback-to-suspend bridge for OcrEngine"
```

---

## Task 3: TDD CallbackBackedImagePreprocessor

**Files:**
- Create: `shelfscan/shared/src/commonTest/kotlin/com/shelfscan/shared/platform/CallbackBackedImagePreprocessorTest.kt`
- Create: `shelfscan/shared/src/commonMain/kotlin/com/shelfscan/shared/platform/CallbackBackedImagePreprocessor.kt`

- [ ] **Step 1: Write the failing test file**

```kotlin
package com.shelfscan.shared.platform

import com.shelfscan.shared.core.model.CapturedImage
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class CallbackBackedImagePreprocessorTest {

    private val sampleImage = CapturedImage(ref = "/tmp/img.jpg", widthPx = 100, heightPx = 200)

    @Test
    fun `normalizeForOcr resumes with ProcessedImage on success`() = runBlocking {
        val preprocessor = CallbackBackedImagePreprocessor(
            normalize = { _, _, _, onSuccess, _ ->
                onSuccess("/tmp/normalized.jpg", 800, 600)
            },
            detect = { _, _, _, onSuccess, _ -> onSuccess(emptyList()) },
        )

        val result = preprocessor.normalizeForOcr(sampleImage)

        assertEquals("/tmp/normalized.jpg", result.ref)
        assertEquals(800, result.widthPx)
        assertEquals(600, result.heightPx)
    }

    @Test
    fun `normalizeForOcr propagates CallbackImagePreprocessorException on error`() = runBlocking {
        val preprocessor = CallbackBackedImagePreprocessor(
            normalize = { _, _, _, _, onError -> onError("disk full") },
            detect = { _, _, _, onSuccess, _ -> onSuccess(emptyList()) },
        )

        val ex = assertFailsWith<CallbackImagePreprocessorException> {
            preprocessor.normalizeForOcr(sampleImage)
        }
        assertEquals("disk full", ex.message)
    }

    @Test
    fun `detectShelfItems maps spines correctly on success`() = runBlocking {
        val preprocessor = CallbackBackedImagePreprocessor(
            normalize = { _, _, _, onSuccess, _ -> onSuccess("", 0, 0) },
            detect = { _, _, _, onSuccess, _ ->
                onSuccess(
                    listOf(
                        FfiDetectedSpine(
                            id = "spine_0",
                            cropRef = "/tmp/spine_0.jpg",
                            boundingBoxLeft = 0f,
                            boundingBoxTop = 0f,
                            boundingBoxRight = 50f,
                            boundingBoxBottom = 200f,
                            confidence = 0.7,
                        )
                    )
                )
            },
        )

        val spines = preprocessor.detectShelfItems(sampleImage)

        assertEquals(1, spines.size)
        assertEquals("spine_0", spines[0].id)
        assertEquals("/tmp/spine_0.jpg", spines[0].cropRef)
        assertEquals(0f, spines[0].boundingBox.left)
        assertEquals(50f, spines[0].boundingBox.right)
        assertEquals(0.7, spines[0].confidence)
    }

    @Test
    fun `detectShelfItems propagates CallbackImagePreprocessorException on error`() = runBlocking {
        val preprocessor = CallbackBackedImagePreprocessor(
            normalize = { _, _, _, onSuccess, _ -> onSuccess("", 0, 0) },
            detect = { _, _, _, _, onError -> onError("segmentation failed") },
        )

        val ex = assertFailsWith<CallbackImagePreprocessorException> {
            preprocessor.detectShelfItems(sampleImage)
        }
        assertEquals("segmentation failed", ex.message)
    }

    @Test
    fun `passes image ref and dimensions through to callbacks`() = runBlocking {
        var capturedNormalizePath = ""
        var capturedDetectWidth = 0
        val preprocessor = CallbackBackedImagePreprocessor(
            normalize = { path, _, _, onSuccess, _ ->
                capturedNormalizePath = path
                onSuccess("", 0, 0)
            },
            detect = { _, w, _, onSuccess, _ ->
                capturedDetectWidth = w
                onSuccess(emptyList())
            },
        )

        preprocessor.normalizeForOcr(sampleImage)
        preprocessor.detectShelfItems(sampleImage)

        assertEquals("/tmp/img.jpg", capturedNormalizePath)
        assertEquals(100, capturedDetectWidth)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd shelfscan && gradle21w :shared:jvmTest --tests "com.shelfscan.shared.platform.CallbackBackedImagePreprocessorTest"`

Expected: FAIL with "Unresolved reference: CallbackBackedImagePreprocessor".

- [ ] **Step 3: Implement `CallbackBackedImagePreprocessor`**

```kotlin
package com.shelfscan.shared.platform

import com.shelfscan.shared.core.model.BoundingBox
import com.shelfscan.shared.core.model.CapturedImage
import com.shelfscan.shared.core.model.DetectedSpine
import com.shelfscan.shared.core.model.ProcessedImage
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Suspend bridge for `ImagePreprocessor` implementations whose normalisation and
 * segmentation steps are callback-based.
 *
 * Lives in `commonMain` so it is JVM-testable. The `iosMain` `SwiftBackedImagePreprocessor`
 * adapts a Swift-implemented `IosImagePreprocessorCallback` into the function references
 * this class accepts.
 */
class CallbackBackedImagePreprocessor(
    private val normalize: (
        imagePath: String,
        widthPx: Int,
        heightPx: Int,
        onSuccess: (processedRef: String, widthPx: Int, heightPx: Int) -> Unit,
        onError: (message: String) -> Unit,
    ) -> Unit,
    private val detect: (
        imagePath: String,
        widthPx: Int,
        heightPx: Int,
        onSuccess: (spines: List<FfiDetectedSpine>) -> Unit,
        onError: (message: String) -> Unit,
    ) -> Unit,
) : ImagePreprocessor {

    override suspend fun normalizeForOcr(image: CapturedImage): ProcessedImage =
        suspendCancellableCoroutine { cont ->
            normalize(
                image.ref,
                image.widthPx,
                image.heightPx,
                { ref, w, h -> cont.resume(ProcessedImage(ref = ref, widthPx = w, heightPx = h)) },
                { message -> cont.resumeWithException(CallbackImagePreprocessorException(message)) },
            )
        }

    override suspend fun detectShelfItems(image: CapturedImage): List<DetectedSpine> =
        suspendCancellableCoroutine { cont ->
            detect(
                image.ref,
                image.widthPx,
                image.heightPx,
                { spines ->
                    val mapped = spines.map { spine ->
                        DetectedSpine(
                            id = spine.id,
                            cropRef = spine.cropRef,
                            boundingBox = BoundingBox(
                                left = spine.boundingBoxLeft,
                                top = spine.boundingBoxTop,
                                right = spine.boundingBoxRight,
                                bottom = spine.boundingBoxBottom,
                            ),
                            confidence = spine.confidence,
                        )
                    }
                    cont.resume(mapped)
                },
                { message -> cont.resumeWithException(CallbackImagePreprocessorException(message)) },
            )
        }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `cd shelfscan && gradle21w :shared:jvmTest --tests "com.shelfscan.shared.platform.CallbackBackedImagePreprocessorTest"`

Expected: BUILD SUCCESSFUL, 5 tests passed.

- [ ] **Step 5: Run the full JVM test suite to confirm nothing else broke**

Run: `cd shelfscan && gradle21w :shared:jvmTest`

Expected: BUILD SUCCESSFUL, all tests pass (existing + 9 new).

- [ ] **Step 6: Commit**

```bash
git add shelfscan/shared/src/commonMain/kotlin/com/shelfscan/shared/platform/CallbackBackedImagePreprocessor.kt \
        shelfscan/shared/src/commonTest/kotlin/com/shelfscan/shared/platform/CallbackBackedImagePreprocessorTest.kt
git commit -m "Add CallbackBackedImagePreprocessor: callback-to-suspend bridge"
```

---

## Task 4: Android regression check

We've added code to `commonMain`; verify Android still builds.

- [ ] **Step 1: Build the Android debug APK**

Run: `cd shelfscan && gradle21w :androidApp:assembleDebug`

Expected: BUILD SUCCESSFUL.

- [ ] **Step 2: No commit needed** — no files changed in this step. If the build fails, fix the regression before continuing.

---

## Task 5: Add iosMain Swift-facing interfaces

**Files:**
- Create: `shelfscan/shared/src/iosMain/kotlin/com/shelfscan/shared/platform/IosOcrCallback.kt`
- Create: `shelfscan/shared/src/iosMain/kotlin/com/shelfscan/shared/platform/IosImagePreprocessorCallback.kt`

The Kotlin Multiplatform default hierarchy template auto-creates the `iosMain` source set as the parent of `iosX64Main`/`iosArm64Main`/`iosSimulatorArm64Main`. We do not need to declare it in `build.gradle.kts`.

- [ ] **Step 1: Create the iosMain directory tree**

Run: `mkdir -p shelfscan/shared/src/iosMain/kotlin/com/shelfscan/shared/platform`

- [ ] **Step 2: Create `IosOcrCallback.kt`**

```kotlin
package com.shelfscan.shared.platform

/**
 * Swift-facing OCR callback. A Swift class implements this protocol (via the
 * Kotlin/Native-generated Objective-C protocol) and is passed to
 * `IosShelfScanFactory.createProcessCapturedImageUseCase`.
 *
 * `imagePath` is a filesystem path on iOS. `onSuccess` carries the raw recognised text
 * (newline-joined) and a list of `FfiOcrLine`. `onError` carries a user-readable message.
 */
interface IosOcrCallback {
    fun recognizeText(
        imagePath: String,
        onSuccess: (rawText: String, lines: List<FfiOcrLine>) -> Unit,
        onError: (message: String) -> Unit,
    )
}
```

- [ ] **Step 3: Create `IosImagePreprocessorCallback.kt`**

```kotlin
package com.shelfscan.shared.platform

/**
 * Swift-facing image preprocessor callback. Two methods, one for normalisation and one
 * for segmentation. Both take primitive image dimensions to keep the Swift call sites
 * simple and avoid Kotlin/Native generics over Swift value types.
 */
interface IosImagePreprocessorCallback {
    fun normalizeForOcr(
        imagePath: String,
        widthPx: Int,
        heightPx: Int,
        onSuccess: (processedRef: String, widthPx: Int, heightPx: Int) -> Unit,
        onError: (message: String) -> Unit,
    )

    fun detectShelfItems(
        imagePath: String,
        widthPx: Int,
        heightPx: Int,
        onSuccess: (spines: List<FfiDetectedSpine>) -> Unit,
        onError: (message: String) -> Unit,
    )
}
```

- [ ] **Step 4: Best-effort compile check for iosArm64**

Run: `cd shelfscan && gradle21w :shared:compileKotlinIosArm64`

Expected outcomes (either is acceptable):
- **PASS:** Klib compilation succeeded on Linux. Note this in the commit message.
- **FAIL** with a message about the Kotlin/Native distribution or Apple SDK toolchain not being available on Linux. This is expected for a Linux-only environment. Document the failure mode in the commit message but **do not** alter the source — the code is correct; the toolchain is the limiting factor.

- [ ] **Step 5: Commit**

```bash
git add shelfscan/shared/src/iosMain/kotlin/com/shelfscan/shared/platform/IosOcrCallback.kt \
        shelfscan/shared/src/iosMain/kotlin/com/shelfscan/shared/platform/IosImagePreprocessorCallback.kt
git commit -m "Add iosMain Swift-facing callback interfaces (IosOcrCallback, IosImagePreprocessorCallback)"
```

---

## Task 6: Add iosMain thin adapters and factory

**Files:**
- Create: `shelfscan/shared/src/iosMain/kotlin/com/shelfscan/shared/platform/SwiftBackedOcrEngine.kt`
- Create: `shelfscan/shared/src/iosMain/kotlin/com/shelfscan/shared/platform/SwiftBackedImagePreprocessor.kt`
- Create: `shelfscan/shared/src/iosMain/kotlin/com/shelfscan/shared/platform/IosShelfScanFactory.kt`

The adapters delegate to the `commonMain` `CallbackBacked*` classes via class delegation (`by`), so they remain trivial.

- [ ] **Step 1: Create `SwiftBackedOcrEngine.kt`**

```kotlin
package com.shelfscan.shared.platform

/**
 * iOS-only adapter that turns a Swift-implemented `IosOcrCallback` into the function-ref
 * shape `CallbackBackedOcrEngine` expects, then delegates `OcrEngine` to that bridge.
 */
class SwiftBackedOcrEngine(callback: IosOcrCallback) : OcrEngine by CallbackBackedOcrEngine(
    recognize = { path, onSuccess, onError ->
        callback.recognizeText(path, onSuccess, onError)
    },
)
```

- [ ] **Step 2: Create `SwiftBackedImagePreprocessor.kt`**

```kotlin
package com.shelfscan.shared.platform

/**
 * iOS-only adapter that turns a Swift-implemented `IosImagePreprocessorCallback` into the
 * function-ref shape `CallbackBackedImagePreprocessor` expects, then delegates
 * `ImagePreprocessor` to that bridge.
 */
class SwiftBackedImagePreprocessor(
    callback: IosImagePreprocessorCallback,
) : ImagePreprocessor by CallbackBackedImagePreprocessor(
    normalize = { path, w, h, onSuccess, onError ->
        callback.normalizeForOcr(path, w, h, onSuccess, onError)
    },
    detect = { path, w, h, onSuccess, onError ->
        callback.detectShelfItems(path, w, h, onSuccess, onError)
    },
)
```

- [ ] **Step 3: Create `IosShelfScanFactory.kt`**

```kotlin
package com.shelfscan.shared.platform

import com.shelfscan.shared.data.metadata.OpenLibraryMetadataLookupService
import com.shelfscan.shared.data.repository.DefaultScanRepository
import com.shelfscan.shared.data.repository.ScanRepository
import com.shelfscan.shared.domain.scan.ProcessCapturedImageUseCase
import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/**
 * Single Swift entry point for constructing the shared scan pipeline on iOS.
 *
 * Swift implements `IosOcrCallback` and `IosImagePreprocessorCallback`, hands them to
 * `createProcessCapturedImageUseCase`, and uses the returned `ProcessCapturedImageUseCase`
 * exactly the same way Android does.
 *
 * Defaults:
 * - `metadataService` defaults to a live `OpenLibraryMetadataLookupService` over Ktor
 *   (works on Kotlin/Native — same code path as Android).
 * - `scanRepository` defaults to an in-memory `DefaultScanRepository`.
 */
object IosShelfScanFactory {
    /**
     * Note on parameter types: `metadataService` and `scanRepository` are nullable so that
     * Swift call sites can pass `nil` to opt into the defaults. Kotlin default arguments
     * don't propagate through Kotlin/Native to Swift; we get the same effect by
     * substituting the default inside the body when `null` is passed.
     */
    fun createProcessCapturedImageUseCase(
        ocrCallback: IosOcrCallback,
        preprocessorCallback: IosImagePreprocessorCallback,
        metadataService: MetadataLookupService? = null,
        scanRepository: ScanRepository? = null,
    ): ProcessCapturedImageUseCase = ProcessCapturedImageUseCase(
        imagePreprocessor = SwiftBackedImagePreprocessor(preprocessorCallback),
        ocrEngine = SwiftBackedOcrEngine(ocrCallback),
        metadataLookupService = metadataService ?: defaultMetadataService(),
        scanRepository = scanRepository ?: DefaultScanRepository(),
    )

    private fun defaultMetadataService(): MetadataLookupService {
        val client = HttpClient {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }
        return OpenLibraryMetadataLookupService(client = client)
    }
}
```

- [ ] **Step 4: Best-effort compile check for iosArm64**

Run: `cd shelfscan && gradle21w :shared:compileKotlinIosArm64`

Expected: same as Task 5 Step 4 — PASS or FAIL-due-to-toolchain are both acceptable.

- [ ] **Step 5: Re-run the full JVM test suite to confirm commonMain wasn't affected**

Run: `cd shelfscan && gradle21w :shared:jvmTest`

Expected: BUILD SUCCESSFUL, all tests still pass.

- [ ] **Step 6: Commit**

```bash
git add shelfscan/shared/src/iosMain/kotlin/com/shelfscan/shared/platform/SwiftBackedOcrEngine.kt \
        shelfscan/shared/src/iosMain/kotlin/com/shelfscan/shared/platform/SwiftBackedImagePreprocessor.kt \
        shelfscan/shared/src/iosMain/kotlin/com/shelfscan/shared/platform/IosShelfScanFactory.kt
git commit -m "Add iosMain SwiftBacked adapters and IosShelfScanFactory entry point"
```

---

## Task 7: Add Swift IosOcrCallback wrapper around VisionOcrAdapter

**Files:**
- Create: `shelfscan/iosApp/iosApp/platform/SwiftIosOcrCallback.swift`

The existing `VisionOcrAdapter` returns a Swift `OcrResult` with optional `CGRect` bounding boxes. This wrapper turns that into the FFI-friendly `IosOcrLine`/callback shape expected by `IosOcrCallback` from the framework.

- [ ] **Step 1: Create the platform directory and the file**

```bash
mkdir -p shelfscan/iosApp/iosApp/platform
```

```swift
// shelfscan/iosApp/iosApp/platform/SwiftIosOcrCallback.swift
import Foundation
import ShelfScanShared

/// Swift implementation of the Kotlin `IosOcrCallback` interface.
/// Wraps the existing `VisionOcrAdapter` and adapts its results into the FFI DTOs.
final class SwiftIosOcrCallback: IosOcrCallback {

    private let adapter = VisionOcrAdapter()

    func recognizeText(
        imagePath: String,
        onSuccess: @escaping (String, [FfiOcrLine]) -> Void,
        onError: @escaping (String) -> Void
    ) {
        Task {
            do {
                let url = URL(fileURLWithPath: imagePath)
                let result = try await adapter.recognizeText(imageURL: url)
                let lines: [FfiOcrLine] = result.lines.map { line in
                    let box = line.boundingBox
                    return FfiOcrLine(
                        text: line.text,
                        confidence: line.confidence,
                        hasBoundingBox: box != nil,
                        boundingBoxLeft: Float(box?.origin.x ?? 0),
                        boundingBoxTop: Float(box?.origin.y ?? 0),
                        boundingBoxRight: Float((box?.origin.x ?? 0) + (box?.size.width ?? 0)),
                        boundingBoxBottom: Float((box?.origin.y ?? 0) + (box?.size.height ?? 0))
                    )
                }
                onSuccess(result.rawText, lines)
            } catch {
                onError(String(describing: error))
            }
        }
    }
}
```

> **Reviewer note:** This file cannot be compiled on Linux. A macOS reviewer must verify `IosOcrCallback` and `FfiOcrLine` resolve from the framework's auto-generated header and that the constructor parameter labels match what Kotlin/Native exports. If the symbol names differ (e.g., `FfiOcrLine` exports as `PlatformFfiOcrLine`), update only the Swift identifiers — the Kotlin source stays as written.

- [ ] **Step 2: Commit**

```bash
git add shelfscan/iosApp/iosApp/platform/SwiftIosOcrCallback.swift
git commit -m "Add Swift IosOcrCallback wrapping VisionOcrAdapter"
```

---

## Task 8: Add Swift passthrough IosImagePreprocessorCallback

**Files:**
- Create: `shelfscan/iosApp/iosApp/platform/SwiftIosImagePreprocessorCallback.swift`

A passthrough mirroring `PassthroughImagePreprocessor.kt` from `commonMain`. Real Vision-based segmentation is out of scope.

- [ ] **Step 1: Create the file**

```swift
// shelfscan/iosApp/iosApp/platform/SwiftIosImagePreprocessorCallback.swift
import Foundation
import ShelfScanShared

/// Swift implementation of the Kotlin `IosImagePreprocessorCallback` interface.
///
/// Passthrough behaviour mirroring the shared `PassthroughImagePreprocessor`:
/// - `normalizeForOcr` returns the input image reference and dimensions unchanged.
/// - `detectShelfItems` returns a single full-image spine with `confidence = 0.5`.
final class SwiftIosImagePreprocessorCallback: IosImagePreprocessorCallback {

    func normalizeForOcr(
        imagePath: String,
        widthPx: Int32,
        heightPx: Int32,
        onSuccess: @escaping (String, Int32, Int32) -> Void,
        onError: @escaping (String) -> Void
    ) {
        onSuccess(imagePath, widthPx, heightPx)
    }

    func detectShelfItems(
        imagePath: String,
        widthPx: Int32,
        heightPx: Int32,
        onSuccess: @escaping ([FfiDetectedSpine]) -> Void,
        onError: @escaping (String) -> Void
    ) {
        let spine = FfiDetectedSpine(
            id: "spine_0",
            cropRef: imagePath,
            boundingBoxLeft: 0,
            boundingBoxTop: 0,
            boundingBoxRight: Float(widthPx),
            boundingBoxBottom: Float(heightPx),
            confidence: 0.5
        )
        onSuccess([spine])
    }
}
```

> **Reviewer note:** Kotlin `Int` exports as Swift `Int32` from Kotlin/Native frameworks. If a macOS reviewer finds a different mapping (e.g. `KotlinInt`), adjust the parameter types accordingly.

- [ ] **Step 2: Commit**

```bash
git add shelfscan/iosApp/iosApp/platform/SwiftIosImagePreprocessorCallback.swift
git commit -m "Add Swift passthrough IosImagePreprocessorCallback"
```

---

## Task 9: Rewrite Swift ScanViewModel to call shared use case

**Files:**
- Modify: `shelfscan/iosApp/iosApp/ui/ScanView.swift`

The current `ScanViewModel` does Vision OCR in Swift and has a TODO comment to "pass ocrResult to the shared KMP domain layer." We replace it with one that constructs `ProcessCapturedImageUseCase` via `IosShelfScanFactory` and calls it.

- [ ] **Step 1: Replace the file contents**

```swift
// shelfscan/iosApp/iosApp/ui/ScanView.swift
import SwiftUI
import AVFoundation
import ShelfScanShared

struct ScanView: View {
    let onScanComplete: (ScanSession) -> Void

    @StateObject private var viewModel = ScanViewModel()

    var body: some View {
        ZStack {
            if viewModel.cameraAuthorized {
                CameraPreviewView(session: viewModel.captureSession)
                    .ignoresSafeArea()
                VStack {
                    Spacer()
                    if viewModel.isProcessing {
                        ProgressView("Analyzing shelf…")
                            .padding()
                            .background(.ultraThinMaterial, in: RoundedRectangle(cornerRadius: 12))
                    } else {
                        Button(action: {
                            viewModel.captureAndProcess { session in
                                onScanComplete(session)
                            }
                        }) {
                            Image(systemName: "camera.circle.fill")
                                .font(.system(size: 72))
                                .foregroundColor(.white)
                                .shadow(radius: 4)
                        }
                    }
                }
                .padding(.bottom, 40)
            } else {
                ContentUnavailableView(
                    "Camera Access Required",
                    systemImage: "camera.slash",
                    description: Text("ShelfScan needs camera access to scan your shelf.")
                )
                Button("Open Settings") {
                    if let url = URL(string: UIApplication.openSettingsURLString) {
                        UIApplication.shared.open(url)
                    }
                }
                .buttonStyle(.bordered)
            }
        }
        .onAppear { viewModel.requestCameraAccess() }
    }
}

@MainActor
final class ScanViewModel: ObservableObject {
    @Published var cameraAuthorized = false
    @Published var isProcessing = false

    let captureSession = AVCaptureSession()
    private let cameraAdapter = AVFoundationCameraAdapter()

    private let processUseCase: ProcessCapturedImageUseCase = IosShelfScanFactory.shared
        .createProcessCapturedImageUseCase(
            ocrCallback: SwiftIosOcrCallback(),
            preprocessorCallback: SwiftIosImagePreprocessorCallback(),
            metadataService: nil,
            scanRepository: nil
        )

    func requestCameraAccess() {
        Task {
            let status = AVCaptureDevice.authorizationStatus(for: .video)
            switch status {
            case .authorized:
                cameraAuthorized = true
                await setupCamera()
            case .notDetermined:
                let granted = await AVCaptureDevice.requestAccess(for: .video)
                cameraAuthorized = granted
                if granted { await setupCamera() }
            default:
                cameraAuthorized = false
            }
        }
    }

    private func setupCamera() async {
        await cameraAdapter.configure(session: captureSession)
        captureSession.startRunning()
    }

    func captureAndProcess(completion: @escaping (ScanSession) -> Void) {
        isProcessing = true
        Task {
            do {
                let imageURL = try await cameraAdapter.capturePhoto()
                let captured = CapturedImage(
                    ref: imageURL.path,
                    widthPx: 0,   // exact dimensions populated by a future enhancement
                    heightPx: 0
                )
                let sessionId = "session_\(Int(Date().timeIntervalSince1970 * 1000))"
                let session = try await processUseCase.execute(image: captured, sessionId: sessionId)
                await MainActor.run {
                    self.isProcessing = false
                    completion(session)
                }
            } catch {
                await MainActor.run { self.isProcessing = false }
            }
        }
    }
}
```

> **Reviewer notes for macOS:**
> 1. Kotlin objects export as `.shared` accessors in Swift (`IosShelfScanFactory.shared`). Verify this against the auto-generated header.
> 2. The factory's `metadataService` and `scanRepository` parameters are typed as nullable in Kotlin specifically so Swift can pass `nil` and trigger the internal defaults (`OpenLibraryMetadataLookupService` and `DefaultScanRepository`).
> 3. `CapturedImage` width/height of `0` is a known follow-up — populating real photo metadata is deferred.

- [ ] **Step 2: Commit**

```bash
git add shelfscan/iosApp/iosApp/ui/ScanView.swift
git commit -m "Wire ScanViewModel to shared ProcessCapturedImageUseCase via IosShelfScanFactory"
```

---

## Task 10: Rewrite Swift ReviewView to use shared MediaItem

**Files:**
- Modify: `shelfscan/iosApp/iosApp/ui/ReviewView.swift`
- Modify: `shelfscan/iosApp/iosApp/ContentView.swift` (to thread the session through)

`ReviewView` currently has its own `DetectedBookItem` Swift struct with hardcoded mock data. We replace it with one that takes a list of shared `MediaItem` from the scan session.

- [ ] **Step 1: Replace `ReviewView.swift`**

```swift
// shelfscan/iosApp/iosApp/ui/ReviewView.swift
import SwiftUI
import ShelfScanShared

struct ReviewView: View {
    let items: [MediaItem]
    let onDone: () -> Void

    var body: some View {
        NavigationStack {
            if items.isEmpty {
                ContentUnavailableView(
                    "No Books Detected",
                    systemImage: "books.vertical",
                    description: Text("Try capturing the shelf again with better lighting.")
                )
                .navigationTitle("Review Results")
                .toolbar {
                    ToolbarItem(placement: .confirmationAction) {
                        Button("Done") { onDone() }
                    }
                }
            } else {
                List(items, id: \.id) { item in
                    BookItemRow(item: item)
                }
                .navigationTitle("Review Results")
                .toolbar {
                    ToolbarItem(placement: .confirmationAction) {
                        Button("Save") { onDone() }
                            .buttonStyle(.borderedProminent)
                    }
                    ToolbarItem(placement: .cancellationAction) {
                        Button("Discard") { onDone() }
                            .foregroundColor(.red)
                    }
                }
            }
        }
    }
}

struct BookItemRow: View {
    let item: MediaItem

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(item.title ?? "(no title)")
                .font(.headline)
            Text(item.creatorName ?? "(no author)")
                .font(.subheadline)
                .foregroundColor(.secondary)
            HStack {
                Image(systemName: "circle.fill")
                    .font(.caption2)
                    .foregroundColor(confidenceColor(for: item.confidence.band))
                Text(confidenceLabel(for: item.confidence.band))
                    .font(.caption)
                    .foregroundColor(.secondary)
            }
        }
        .padding(.vertical, 4)
    }

    private func confidenceColor(for band: ConfidenceBand) -> Color {
        switch band {
        case .high: return .green
        case .medium: return .orange
        case .low, .needsReview: return .red
        default: return .gray
        }
    }

    private func confidenceLabel(for band: ConfidenceBand) -> String {
        switch band {
        case .high: return "High"
        case .medium: return "Medium"
        case .low: return "Low"
        case .needsReview: return "Needs Review"
        default: return "Unknown"
        }
    }
}
```

> **Reviewer note for macOS:** Kotlin enums export to Swift as classes with static lowercase-named members (e.g. `ConfidenceBand.high`, `.medium`, `.low`, `.needsReview`). If the generated names differ (e.g. uppercase, or with a `Companion`), adjust the switch cases accordingly. The `default` branch protects against future additions to the enum.

- [ ] **Step 2: Update `ContentView.swift` to thread the session**

```swift
// shelfscan/iosApp/iosApp/ContentView.swift
import SwiftUI
import ShelfScanShared

struct ContentView: View {
    @StateObject private var router = AppRouter()
    @State private var lastSession: ScanSession? = nil

    var body: some View {
        switch router.currentScreen {
        case .home:
            HomeView(onStartScan: { router.navigate(to: .scan) })
        case .scan:
            ScanView { session in
                lastSession = session
                router.navigate(to: .review)
            }
        case .review:
            ReviewView(
                items: lastSession?.detectedItems ?? [],
                onDone: {
                    lastSession = nil
                    router.navigate(to: .home)
                }
            )
        }
    }
}

enum AppScreen {
    case home, scan, review
}

final class AppRouter: ObservableObject {
    @Published var currentScreen: AppScreen = .home

    func navigate(to screen: AppScreen) {
        currentScreen = screen
    }
}
```

- [ ] **Step 3: Commit**

```bash
git add shelfscan/iosApp/iosApp/ui/ReviewView.swift shelfscan/iosApp/iosApp/ContentView.swift
git commit -m "Replace ReviewView mock data with shared MediaItem from ScanSession"
```

---

## Task 11: Add XCFramework helper script

**Files:**
- Create: `shelfscan/Scripts/link-shared-xcframework.sh`

- [ ] **Step 1: Create the script**

```bash
mkdir -p shelfscan/Scripts
```

```bash
#!/usr/bin/env bash
# shelfscan/Scripts/link-shared-xcframework.sh
#
# Builds the ShelfScanShared XCFramework for iOS targets and prints the
# path along with the manual steps a developer needs in Xcode to link it.
#
# Run on macOS only. The Linux dev environment cannot produce an XCFramework.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SHELFSCAN_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

cd "$SHELFSCAN_DIR"

if command -v gradle21w >/dev/null 2>&1; then
    GRADLE=gradle21w
else
    GRADLE=./gradlew
fi

echo "Building XCFramework..."
"$GRADLE" :shared:assembleShelfScanSharedXCFramework

XCF_PATH="$SHELFSCAN_DIR/shared/build/XCFrameworks/release/ShelfScanShared.xcframework"
echo
echo "Built: $XCF_PATH"
echo
echo "Next steps in Xcode (one-time):"
echo "  1. Open $SHELFSCAN_DIR/iosApp/iosApp.xcodeproj"
echo "  2. Select target 'iosApp' -> General -> Frameworks, Libraries, and Embedded Content"
echo "  3. Click '+' -> Add Other... -> Add Files..."
echo "  4. Navigate to and select: $XCF_PATH"
echo "  5. Set Embed to 'Embed & Sign'"
echo "  6. Build and run (Cmd+R)"
```

- [ ] **Step 2: Make it executable**

Run: `chmod +x shelfscan/Scripts/link-shared-xcframework.sh`

- [ ] **Step 3: Verify the script is well-formed shell**

Run: `bash -n shelfscan/Scripts/link-shared-xcframework.sh`

Expected: no output (syntactically valid).

- [ ] **Step 4: Commit**

```bash
git add shelfscan/Scripts/link-shared-xcframework.sh
git commit -m "Add helper script to build ShelfScanShared XCFramework on macOS"
```

---

## Task 12: Update iosApp README

**Files:**
- Modify: `shelfscan/iosApp/README.md`

Replace the manual three-step instructions with a pointer at the helper script and document the new platform/ directory.

- [ ] **Step 1: Replace the file contents**

```markdown
# ShelfScan iOS App

This directory contains the native iOS app built with SwiftUI + AVFoundation + Apple Vision.

## Requirements

- Xcode 15 or later
- iOS 17 SDK
- macOS 13+

## Structure

```
iosApp/
  iosApp/
    ShelfScanApp.swift          - @main SwiftUI entry point
    ContentView.swift           - Top-level navigation router; threads the ScanSession
    Info.plist                  - App permissions (camera, photo library)
    ui/
      HomeView.swift            - Home screen
      ScanView.swift            - Camera capture screen, calls shared use case
      ReviewView.swift          - Displays shared MediaItem list from the ScanSession
    camera/
      AVFoundationCameraAdapter.swift  - AVCaptureSession + photo capture
                                         CameraPreviewView (UIViewRepresentable)
    ocr/
      VisionOcrAdapter.swift    - VNRecognizeTextRequest OCR (Apple Vision)
    platform/
      SwiftIosOcrCallback.swift               - Implements shared IosOcrCallback;
                                                wraps VisionOcrAdapter
      SwiftIosImagePreprocessorCallback.swift - Implements shared IosImagePreprocessorCallback
                                                (passthrough; full-image spine)
  iosApp.xcodeproj/
    project.pbxproj             - Xcode project definition
```

## Building

1. Build and link the shared XCFramework (one-time, on macOS):

   ```bash
   cd ../  # shelfscan/
   ./Scripts/link-shared-xcframework.sh
   ```

   The script builds the framework and prints the path plus the Xcode link steps.

2. Open `iosApp.xcodeproj` in Xcode and follow the steps the script printed to add
   the framework to the project.
3. Select a simulator or connected device.
4. Build and run (`Cmd+R`).

## Integration with shared KMP module

The Swift app calls into the shared KMP module through `IosShelfScanFactory`:

```swift
import ShelfScanShared

let processUseCase = IosShelfScanFactory.shared.createProcessCapturedImageUseCase(
    ocrCallback: SwiftIosOcrCallback(),
    preprocessorCallback: SwiftIosImagePreprocessorCallback(),
    metadataService: nil,    // defaults to OpenLibraryMetadataLookupService
    scanRepository: nil      // defaults to in-memory DefaultScanRepository
)

let session = try await processUseCase.execute(
    image: capturedImage,
    sessionId: "session_..."
)
print(session.detectedItems)
```

`SwiftIosOcrCallback` wraps `VisionOcrAdapter`. `SwiftIosImagePreprocessorCallback` is
currently a passthrough; replace it with a Vision-based spine detector when iOS-side
segmentation is desired.

## iOS-specific responsibilities

| Component         | Implementation                                                  |
|-------------------|-----------------------------------------------------------------|
| Camera capture    | `AVFoundationCameraAdapter` using `AVCaptureSession`            |
| OCR               | `VisionOcrAdapter` using `VNRecognizeTextRequest` (`.accurate`) |
| Image preprocessor| `SwiftIosImagePreprocessorCallback` (passthrough placeholder)   |
| Business logic    | Shared KMP module via `IosShelfScanFactory`                     |
| UI                | SwiftUI with MVVM (`ObservableObject` / `@StateObject`)         |
```

- [ ] **Step 2: Commit**

```bash
git add shelfscan/iosApp/README.md
git commit -m "Update iosApp README: document XCFramework helper script and new platform/ directory"
```

---

## Task 13: Final regression check and summary

- [ ] **Step 1: Run all JVM tests one more time**

Run: `cd shelfscan && gradle21w :shared:jvmTest`

Expected: BUILD SUCCESSFUL, all tests pass.

- [ ] **Step 2: Run Android debug build**

Run: `cd shelfscan && gradle21w :androidApp:assembleDebug`

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Best-effort iOS klib compile**

Run: `cd shelfscan && gradle21w :shared:compileKotlinIosArm64`

Expected: PASS, or FAIL-due-to-toolchain. Either is acceptable; record the result.

- [ ] **Step 4: Verify the working tree is clean**

Run: `git status`

Expected: `nothing to commit, working tree clean`.

- [ ] **Step 5: Print the commit list**

Run: `git log --oneline main..HEAD` (or, if working on `main`, `git log --oneline -15`).

Expected: ~12 new commits covering Tasks 1–12. The branch is ready for a macOS engineer to pick up the iOS-side verification.

---

## What's not done (explicit follow-ups)

- iOS Swift code has not been compiled or run; verification is source-review only.
- `iosApp.xcodeproj/project.pbxproj` is unchanged — XCFramework linking is manual via the helper script.
- `CapturedImage.widthPx`/`heightPx` are passed as `0` in `ScanViewModel` — populating real photo metadata is a follow-up.
- `ReviewView` is read-only — editing/saving is deferred to a separate increment that wires shared `ReviewViewModel` and `CollectionRepository`.
- `SwiftIosImagePreprocessorCallback` is a passthrough — real Vision-based spine detection is deferred.
- macOS GitHub Actions job to build the XCFramework / iosApp is deferred.
