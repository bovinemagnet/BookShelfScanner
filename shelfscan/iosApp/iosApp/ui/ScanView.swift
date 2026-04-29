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
