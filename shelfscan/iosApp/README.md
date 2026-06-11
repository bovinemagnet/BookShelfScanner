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

1. Build the shared XCFramework (on macOS; re-run whenever the shared module changes):

   ```bash
   cd ../  # shelfscan/
   ./Scripts/link-shared-xcframework.sh
   ```

   The Xcode project already links `ShelfScanShared.xcframework` from the Gradle
   output path (`shared/build/XCFrameworks/release`), so no manual link step is
   needed — the framework just has to exist there.

2. Open `iosApp.xcodeproj` in Xcode.
3. Select a simulator or connected device.
4. Build and run (`Cmd+R`).

## Continuous integration

`.github/workflows/ios.yml` runs on pushes to `main` that touch `shared/` or
`iosApp/` (and on demand via *Run workflow* in the Actions tab). On a macOS
runner it:

1. runs the shared test suite on the Kotlin/Native iOS simulator target
   (`:shared:iosSimulatorArm64Test`);
2. builds the XCFramework;
3. builds this app for the iOS simulator with `xcodebuild` (unsigned).

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
