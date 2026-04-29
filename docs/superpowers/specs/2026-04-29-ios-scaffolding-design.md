# iOS Scaffolding Design

- **Date:** 2026-04-29
- **Author:** Paul Snow
- **Status:** Approved (pending implementation plan)
- **Version:** 0.0.0

## Summary

Add an `iosMain` source set to `:shared` with Kotlin/Native bridges that let Swift implement
the `OcrEngine` and `ImagePreprocessor` platform interfaces via callback-style helpers. Wire
the existing Swift skeleton in `iosApp/` to call shared `ProcessCapturedImageUseCase` through
those bridges, replacing the local placeholder data structures. Provide a helper script for
linking the `ShelfScanShared` XCFramework into Xcode; the `project.pbxproj` itself is left
untouched.

This work is staged on Linux: the Kotlin side can be compiled and unit-tested locally, but
the Swift side and the final XCFramework link require a macOS engineer to verify.

## Goals

- The `:shared` module ships an `iosMain` source set so the iOS targets are no longer empty.
- iOS uses the same shared `ProcessCapturedImageUseCase` orchestration that Android uses.
- Swift can implement `OcrEngine`/`ImagePreprocessor` without dealing with Kotlin suspend
  ABI ergonomics.
- Bridge logic is unit-tested on JVM, so correctness is verifiable on Linux even though
  iOS runtime behaviour is not.
- The Swift skeleton (`ScanView`, `ReviewView`) no longer carries dead TODOs and
  hard-coded mock data that contradict the architecture doc.

## Non-Goals

- Real Vision/AVFoundation Kotlin/Native bindings via cinterop. Vision OCR remains in Swift.
- Editing `iosApp.xcodeproj/project.pbxproj` to add the framework reference. The README and
  a helper script document the manual one-time step.
- A macOS CI job. Deferred.
- Real iOS-side spine segmentation. The `SwiftIosImagePreprocessorCallback` ships as a
  passthrough mirroring the existing `PassthroughImagePreprocessor`.
- Wiring shared `CollectionRepository`/`ReviewViewModel` end-to-end on iOS. `ReviewView`
  accepts `[MediaItem]` and emits Save/Discard callbacks; full repository wiring is a
  follow-up increment.
- iOS UI/instrumented tests.

## Decisions

| # | Choice | Rejected alternatives | Reason |
|---|--------|-----------------------|--------|
| 1 | Swift-side adapters with `iosMain` Kotlin bridges (Option 2 from brainstorm) | Kotlin-side cinterop adapters; empty `iosMain` | Cinterop bindings can't be compile-tested on Linux. Empty `iosMain` abandons shared orchestration on iOS. |
| 2 | Callback interface + Kotlin suspend bridge (seam B) | Swift implements suspend interface directly; SKIE plugin | Direct suspend implementation is fragile across Kotlin/Native ABI changes. SKIE adds a third-party dependency we won't introduce blind. |
| 3 | Helper script for XCFramework linking (3b) | Edit `project.pbxproj` directly; document only | Editing pbxproj blind risks corrupting a file we can't open. Helper script is a single safe command on macOS. |
| 4 | Bounding boxes flattened to primitive fields across the FFI | DTO with nullable `CGRect`-equivalent | Swift→Kotlin generics over optional value types add friction with no benefit. |
| 5 | `IosShelfScanFactory.createProcessCapturedImageUseCase` defaults `metadataService` to `OpenLibraryMetadataLookupService()` | Require Swift to pass it explicitly | OpenLibrary is the only metadata source today; default keeps the Swift call site simple. |
| 6 | Suspend bridge logic lives in `commonMain` (`CallbackBackedOcrEngine`); `iosMain` only holds thin Swift-facing adapters | Put bridges in `iosMain` (test in `iosTest`); duplicate suspend logic per platform | Keeps the bridge JVM-testable on Linux. The iOS-only code is reduced to trivial delegation. |

## Architecture

### Module structure

```
shelfscan/shared/src/
├── commonMain/   (existing + 4 new files)
│   └── platform/
│       ├── OcrEngine.kt                 (existing — suspend interface)
│       ├── ImagePreprocessor.kt         (existing — suspend interface)
│       ├── MetadataLookupService.kt     (existing — suspend interface)
│       ├── PassthroughImagePreprocessor.kt    (existing)
│       ├── NoOpMetadataLookupService.kt       (existing)
│       ├── CallbackBackedOcrEngine.kt         (NEW — suspend OcrEngine; takes fn refs; JVM-testable)
│       ├── CallbackBackedImagePreprocessor.kt (NEW — same pattern for ImagePreprocessor)
│       ├── FfiOcrLine.kt                      (NEW — shared FFI DTO)
│       └── FfiDetectedSpine.kt                (NEW — shared FFI DTO)
├── commonTest/   (existing — gains bridge unit tests)
└── iosMain/      (NEW)
    └── platform/
        ├── IosOcrCallback.kt               (non-suspend interface, Swift implements)
        ├── IosImagePreprocessorCallback.kt (non-suspend interface, Swift implements)
        ├── SwiftBackedOcrEngine.kt         (thin adapter: wraps IosOcrCallback into CallbackBackedOcrEngine)
        ├── SwiftBackedImagePreprocessor.kt (thin adapter, same shape)
        └── IosShelfScanFactory.kt          (single Swift entry point)

shelfscan/iosApp/iosApp/
├── ShelfScanApp.swift           (no change)
├── ContentView.swift            (no change)
├── ui/
│   ├── HomeView.swift           (no change)
│   ├── ScanView.swift           (rewrite ScanViewModel — calls shared use case)
│   └── ReviewView.swift         (drop DetectedBookItem; accept shared MediaItem)
├── camera/
│   └── AVFoundationCameraAdapter.swift   (no change)
├── ocr/
│   └── VisionOcrAdapter.swift            (no change; wrapped by SwiftIosOcrCallback)
└── platform/                              (NEW)
    ├── SwiftIosOcrCallback.swift                   (implements IosOcrCallback)
    └── SwiftIosImagePreprocessorCallback.swift    (passthrough impl)

shelfscan/Scripts/                          (NEW)
└── link-shared-xcframework.sh              (build XCFramework + manual-link instructions)
```

### Bridge contracts

The bridge logic lives in `commonMain` (so it's JVM-testable) and uses plain Kotlin
function references. The `iosMain` adapter is a thin wrapper that turns a Swift-implemented
`IosOcrCallback` into the function references the common bridge expects.

```kotlin
// commonMain — JVM-testable suspend bridge
class CallbackBackedOcrEngine(
    private val recognize: (
        imagePath: String,
        onSuccess: (rawText: String, lines: List<FfiOcrLine>) -> Unit,
        onError: (message: String) -> Unit
    ) -> Unit
) : OcrEngine {
    override suspend fun recognizeText(image: ProcessedImage): OcrResult =
        suspendCancellableCoroutine { cont ->
            recognize(
                image.ref,
                { rawText, lines ->
                    cont.resume(OcrResult(rawText = rawText, lines = lines.map { /* map */ }))
                },
                { message -> cont.resumeWithException(IosOcrException(message)) }
            )
        }
}

data class FfiOcrLine(
    val text: String,
    val confidence: Double,
    val boundingBoxX: Double,
    val boundingBoxY: Double,
    val boundingBoxWidth: Double,
    val boundingBoxHeight: Double
)
```

```kotlin
// iosMain — thin adapter so Swift sees a normal interface (not a Kotlin function type)
interface IosOcrCallback {
    fun recognizeText(
        imagePath: String,
        onSuccess: (rawText: String, lines: List<FfiOcrLine>) -> Unit,
        onError: (message: String) -> Unit
    )
}

class SwiftBackedOcrEngine(callback: IosOcrCallback) : OcrEngine by CallbackBackedOcrEngine(
    recognize = { path, ok, err -> callback.recognizeText(path, ok, err) }
)
```

`IosImagePreprocessorCallback` and `SwiftBackedImagePreprocessor` follow the same pattern,
with two callback methods (`normalizeForOcr`, `detectShelfItems`) and the `FfiDetectedSpine`
DTO. The `CallbackBackedImagePreprocessor` lives in `commonMain` and carries the suspend
bridge logic.

**Why this shape:** Swift can't easily pass Kotlin function types into Kotlin code (the
generated header makes them awkward), so iOS gets a normal interface. JVM tests, however,
can construct `CallbackBackedOcrEngine` directly with lambdas — no iOS-specific code path
to test.

### Factory

```kotlin
object IosShelfScanFactory {
    fun createProcessCapturedImageUseCase(
        ocrCallback: IosOcrCallback,
        preprocessorCallback: IosImagePreprocessorCallback,
        metadataService: MetadataLookupService = OpenLibraryMetadataLookupService()
    ): ProcessCapturedImageUseCase
}
```

The factory is the single Kotlin symbol Swift constructs use cases through. Its exact
constructor wiring will be confirmed against the real `ProcessCapturedImageUseCase`
signature during implementation.

### Runtime data flow (iOS)

1. User taps capture in `ScanView`.
2. Swift `ScanViewModel` calls `AVFoundationCameraAdapter.capturePhoto()` → image URL.
3. Swift `ScanViewModel` invokes `processUseCase.execute(captured)` (constructed once at
   init via `IosShelfScanFactory`).
4. Inside the use case, `SwiftBackedImagePreprocessor.detectShelfItems` (suspend) bridges
   to `IosImagePreprocessorCallback.detectShelfItems` (Swift).
5. Same for OCR via `SwiftBackedOcrEngine` → `IosOcrCallback` →
   `VisionOcrAdapter.recognizeText` (Swift).
6. Shared `OpenLibraryMetadataLookupService` runs directly on Kotlin/Native via Ktor.
7. Result returned to Swift; `ReviewView` receives `[MediaItem]`.

## Swift-side changes

- **`SwiftIosOcrCallback.swift`** wraps the existing `VisionOcrAdapter`. Maps the Swift
  `OcrLine` (with optional `CGRect`) into the FFI-flat `IosOcrLine` DTO.
- **`SwiftIosImagePreprocessorCallback.swift`** ships as a passthrough: `normalizeForOcr`
  returns the input image ref unchanged; `detectShelfItems` returns one full-image spine
  with `confidence = 0.5`. Mirrors `PassthroughImagePreprocessor.kt`.
- **`ScanView.swift`**: `ScanViewModel` constructs the use case via `IosShelfScanFactory`
  in its initialiser; `captureAndProcess` invokes it and stores the result on
  `@Published var lastResult`. The `// In a full implementation, pass ocrResult to the
  shared KMP domain layer` TODO is removed.
- **`ReviewView.swift`**: drops the local `DetectedBookItem` struct and mock data; takes
  `[MediaItem]` from shared. A small `EditableMediaItemWrapper` `ObservableObject` (under
  ~30 lines) provides SwiftUI-friendly bindings since Kotlin `StateFlow` doesn't bridge
  cleanly without SKIE.
- **No changes** to `ContentView`, `HomeView`, `ShelfScanApp`, `AVFoundationCameraAdapter`,
  `VisionOcrAdapter`, `Info.plist`, or `project.pbxproj`.

## XCFramework linking

`Scripts/link-shared-xcframework.sh` runs `gradle :shared:assembleShelfScanSharedXCFramework`
and prints the produced path plus three-step manual Xcode instructions. The README is
updated to point at the script. The pbxproj is not edited.

## Error handling

- Bridge failures from Swift surface as `IosOcrException` / `IosImagePreprocessorException`
  on the Kotlin side; existing `ProcessCapturedImageUseCase` error paths catch them.
- Swift `ScanViewModel.captureAndProcess` already catches and resets `isProcessing` on
  failure; the rewrite preserves this.
- `ReviewView` shows an empty list when `lastResult` is `nil` rather than crashing.

## Testing strategy

### Verifiable on Linux

1. **Klib compilation**: `gradle21w :shared:compileKotlinIosArm64`,
   `:compileKotlinIosX64`, `:compileKotlinIosSimulatorArm64`. If any of these turn out to
   require a macOS host in this Kotlin version, the implementation plan will document
   the actual failure mode and fall back to syntactic review. (Best-current-knowledge:
   klib compilation works cross-platform; XCFramework assemble does not.)
2. **JVM unit tests** in `commonTest` against `CallbackBackedOcrEngine` /
   `CallbackBackedImagePreprocessor`:
   - Resumes successfully when the success callback fires.
   - Propagates `IosOcrException` / `IosImagePreprocessorException` on the error callback.
   - `FfiOcrLine` → shared `OcrLine` mapping is correct (text, confidence, bounding box).
   - Both `normalizeForOcr` and `detectShelfItems` paths covered for the preprocessor bridge.

   The `iosMain`-only `SwiftBackedOcrEngine` / `SwiftBackedImagePreprocessor` adapters are
   intentionally trivial (one delegating constructor) and don't need their own tests —
   they're audited by code review.

   `IosShelfScanFactory` itself lives in `iosMain` (so it can take `IosOcrCallback` /
   `IosImagePreprocessorCallback` parameters); we test its wiring contract by
   constructing an equivalent `ProcessCapturedImageUseCase` from `commonTest` using
   `CallbackBackedOcrEngine` directly and exercising the same shared use case.
3. **Android regression check**: `gradle21w :androidApp:assembleDebug` still succeeds.

### Not verifiable on Linux (acknowledged gap)

- Swift compilation.
- XCFramework link in Xcode.
- End-to-end Vision OCR + AVFoundation behaviour on a device/simulator.
- The auto-generated Objective-C header for the framework, unless a macOS host is
  available to run `assembleShelfScanSharedXCFramework`.

### Definition of done

- All JVM tests pass.
- iOS klib compilation succeeds (or the failure is documented).
- Android debug build still passes.
- Swift source has been hand-reviewed for symbol-name parity with the Kotlin exports.
- README + helper script clearly document the macOS-side step.
- The implementation plan explicitly flags every step that needs macOS to fully verify.

## Risks

| Risk | Likelihood | Mitigation |
|------|------------|------------|
| Klib compilation for iOS targets requires macOS in this Kotlin version | Medium | Document failure mode; rely on JVM tests + source review. |
| `ProcessCapturedImageUseCase` constructor signature differs from spec assumption | Low | Implementation plan reads real source first and adjusts factory wiring. |
| Kotlin/Native exports `IosOcrCallback` with a different Swift symbol name than expected | Low | Reviewer with macOS host inspects the generated `.h` and adjusts `SwiftIosOcrCallback` symbol names. Document this as a known follow-up. |
| Swift `EditableMediaItemWrapper` grows past ~30 lines as `ReviewView` needs more state | Low | Keep wrapper focused; defer richer state to a `ReviewViewModel` rewrite increment. |
| `:shared` `iosMain` adds a dependency that breaks `:androidApp` build | Low | Android regression check is part of definition of done. |

## Out of scope (explicit)

- Editing `project.pbxproj`.
- Replacing OpenLibrary with a different metadata source on iOS.
- Real iOS spine detection.
- Shared `CollectionRepository`/`ReviewViewModel` end-to-end iOS wiring.
- macOS GitHub Actions job.
- iOS UI/instrumented tests.

## Open questions resolved during brainstorm

- **Q:** Kotlin-side cinterop or Swift-side adapters? → Swift-side (Option 2).
- **Q:** Direct suspend, callback bridge, or SKIE? → Callback bridge (seam B).
- **Q:** Edit pbxproj or use a helper script? → Helper script (Safe scope).
- **Q:** Flat primitives or DTO with `CGRect`-equivalent? → Flat primitives.
- **Q:** Default `metadataService` in factory? → Yes, default to `OpenLibraryMetadataLookupService()`.
