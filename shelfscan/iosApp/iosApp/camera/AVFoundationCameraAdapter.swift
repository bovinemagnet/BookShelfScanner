import AVFoundation
import UIKit

/// Wraps AVFoundation capture session for ShelfScan.
/// Corresponds to `CameraXAdapter` on Android.
final class AVFoundationCameraAdapter: NSObject {
    private var photoOutput: AVCapturePhotoOutput?
    private var photoContinuation: CheckedContinuation<URL, Error>?

    /// Configures the AVCaptureSession with the back camera and photo output.
    func configure(session: AVCaptureSession) async {
        session.beginConfiguration()
        defer { session.commitConfiguration() }

        guard let device = AVCaptureDevice.default(.builtInWideAngleCamera, for: .video, position: .back),
              let input = try? AVCaptureDeviceInput(device: device),
              session.canAddInput(input) else { return }

        session.addInput(input)

        let output = AVCapturePhotoOutput()
        guard session.canAddOutput(output) else { return }
        session.addOutput(output)
        photoOutput = output
    }

    /// Captures a single photo and returns its file URL.
    func capturePhoto() async throws -> URL {
        guard let output = photoOutput else {
            throw CameraError.notConfigured
        }
        return try await withCheckedThrowingContinuation { continuation in
            photoContinuation = continuation
            let settings = AVCapturePhotoSettings()
            output.capturePhoto(with: settings, delegate: self)
        }
    }

    enum CameraError: Error {
        case notConfigured
        case captureFailed(String)
    }
}

extension AVFoundationCameraAdapter: AVCapturePhotoCaptureDelegate {
    func photoOutput(
        _ output: AVCapturePhotoOutput,
        didFinishProcessingPhoto photo: AVCapturePhoto,
        error: Error?
    ) {
        if let error {
            photoContinuation?.resume(throwing: error)
            photoContinuation = nil
            return
        }
        guard let data = photo.fileDataRepresentation() else {
            photoContinuation?.resume(throwing: CameraError.captureFailed("No data"))
            photoContinuation = nil
            return
        }
        let url = FileManager.default.temporaryDirectory
            .appendingPathComponent("capture_\(Date().timeIntervalSince1970).jpg")
        do {
            try data.write(to: url)
            photoContinuation?.resume(returning: url)
        } catch {
            photoContinuation?.resume(throwing: error)
        }
        photoContinuation = nil
    }
}

/// SwiftUI representable that displays the AVCaptureSession preview.
import SwiftUI

struct CameraPreviewView: UIViewRepresentable {
    let session: AVCaptureSession

    func makeUIView(context: Context) -> PreviewUIView {
        PreviewUIView(session: session)
    }

    func updateUIView(_ uiView: PreviewUIView, context: Context) {}
}

final class PreviewUIView: UIView {
    override class var layerClass: AnyClass { AVCaptureVideoPreviewLayer.self }

    var previewLayer: AVCaptureVideoPreviewLayer {
        layer as! AVCaptureVideoPreviewLayer
    }

    init(session: AVCaptureSession) {
        super.init(frame: .zero)
        previewLayer.session = session
        previewLayer.videoGravity = .resizeAspectFill
    }

    required init?(coder: NSCoder) { fatalError("init(coder:) not supported") }
}
