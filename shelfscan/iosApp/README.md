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
    ContentView.swift           - Top-level navigation router
    Info.plist                  - App permissions (camera, photo library)
    ui/
      HomeView.swift            - Home screen
      ScanView.swift            - Camera capture screen (AVFoundation)
      ReviewView.swift          - Editable results list
    camera/
      AVFoundationCameraAdapter.swift  - AVCaptureSession + photo capture
                                         CameraPreviewView (UIViewRepresentable)
    ocr/
      VisionOcrAdapter.swift    - VNRecognizeTextRequest OCR (Apple Vision)
  iosApp.xcodeproj/
    project.pbxproj             - Xcode project definition
```

## Building

1. Open `iosApp.xcodeproj` in Xcode.
2. Select a simulator or connected device.
3. Build and run (`Cmd+R`).

## Integration with shared KMP module

The shared business logic is in `../shared/`. To integrate:

### 1. Build the XCFramework from the shared module

Run from the `shelfscan/` directory on macOS:

```bash
./gradlew :shared:assembleShelfScanSharedXCFramework
```

This outputs to `shared/build/XCFrameworks/release/ShelfScanShared.xcframework`.

### 2. Link the framework in Xcode

- In Xcode, select the `iosApp` target → **General** → **Frameworks, Libraries, and Embedded Content**
- Click **+** → **Add Other…** → **Add Files…**
- Navigate to and select `ShelfScanShared.xcframework`
- Set **Embed** to **Embed & Sign**

### 3. Import in Swift

```swift
import ShelfScanShared

// Use domain use cases directly
let scoreUseCase = ScoreConfidenceUseCase()
let result = scoreUseCase.execute(
    input: ScoreConfidenceUseCaseScoreInput(
        segmentationConfidence: 0.9,
        ocrConfidence: 0.85,
        parserConfidence: 0.7,
        catalogMatchConfidence: 0.8,
        reasons: []
    )
)
print(result.band) // HIGH
```

## iOS-specific responsibilities (per architecture doc)

| Component | Implementation |
|-----------|---------------|
| Camera capture | `AVFoundationCameraAdapter` using `AVCaptureSession` |
| OCR | `VisionOcrAdapter` using `VNRecognizeTextRequest` (`.accurate` mode) |
| Image quality gate | Shared `ImageQualityAssessment` via KMP |
| Business logic | Shared KMP module (`ParseDetectedItemUseCase`, `ScoreConfidenceUseCase`, etc.) |
| UI | SwiftUI with MVVM (`ObservableObject` / `@StateObject`) |
