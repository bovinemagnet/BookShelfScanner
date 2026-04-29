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
