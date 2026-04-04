import Vision
import UIKit

/// Wraps Apple Vision's VNRecognizeTextRequest for on-device OCR.
/// Corresponds to `MlKitOcrAdapter` on Android.
struct VisionOcrAdapter {
    struct OcrResult {
        let lines: [OcrLine]
        var rawText: String { lines.map(\.text).joined(separator: "\n") }
    }

    struct OcrLine {
        let text: String
        let confidence: Float
        let boundingBox: CGRect?
    }

    /// Recognizes text in the image at the given URL using VNRecognizeTextRequest.
    func recognizeText(imageURL: URL) async throws -> OcrResult {
        guard let image = UIImage(contentsOfFile: imageURL.path),
              let cgImage = image.cgImage else {
            throw OcrError.imageLoadFailed
        }

        return try await withCheckedThrowingContinuation { continuation in
            let request = VNRecognizeTextRequest { request, error in
                if let error {
                    continuation.resume(throwing: error)
                    return
                }
                let observations = request.results as? [VNRecognizedTextObservation] ?? []
                let lines = observations.compactMap { observation -> OcrLine? in
                    guard let topCandidate = observation.topCandidates(1).first else { return nil }
                    let boundingBox = observation.boundingBox
                    return OcrLine(
                        text: topCandidate.string,
                        confidence: topCandidate.confidence,
                        boundingBox: boundingBox
                    )
                }
                continuation.resume(returning: OcrResult(lines: lines))
            }
            // Accurate mode gives better results for book spines
            request.recognitionLevel = .accurate
            request.usesLanguageCorrection = true

            let handler = VNImageRequestHandler(cgImage: cgImage, options: [:])
            do {
                try handler.perform([request])
            } catch {
                continuation.resume(throwing: error)
            }
        }
    }

    enum OcrError: Error {
        case imageLoadFailed
    }
}
