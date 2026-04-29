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
