import SwiftUI
import AVFoundation

struct ScanView: View {
    let onScanComplete: () -> Void

    @StateObject private var viewModel = ScanViewModel()
    @State private var showPermissionAlert = false

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
                            viewModel.captureAndProcess { onScanComplete() }
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
        .alert("Camera Unavailable", isPresented: $showPermissionAlert) {
            Button("OK", role: .cancel) {}
        }
    }
}

@MainActor
final class ScanViewModel: ObservableObject {
    @Published var cameraAuthorized = false
    @Published var isProcessing = false

    let captureSession = AVCaptureSession()
    private let cameraAdapter = AVFoundationCameraAdapter()
    private let ocrAdapter = VisionOcrAdapter()

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

    func captureAndProcess(completion: @escaping () -> Void) {
        isProcessing = true
        Task {
            do {
                let imageURL = try await cameraAdapter.capturePhoto()
                let ocrResult = try await ocrAdapter.recognizeText(imageURL: imageURL)
                // In a full implementation, pass ocrResult to the shared KMP domain layer
                _ = ocrResult
                await MainActor.run {
                    isProcessing = false
                    completion()
                }
            } catch {
                await MainActor.run { isProcessing = false }
            }
        }
    }
}
