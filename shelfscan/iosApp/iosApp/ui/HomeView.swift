import SwiftUI

struct HomeView: View {
    let onStartScan: () -> Void

    var body: some View {
        VStack(spacing: 16) {
            Spacer()
            Image(systemName: "books.vertical")
                .font(.system(size: 72))
                .foregroundColor(.accentColor)
            Text("ShelfScan")
                .font(.largeTitle)
                .fontWeight(.bold)
            Text("Take a photo of a shelf to catalog your books.")
                .font(.body)
                .multilineTextAlignment(.center)
                .foregroundColor(.secondary)
                .padding(.horizontal, 32)
            Spacer()
            Button(action: onStartScan) {
                Label("Scan a Shelf", systemImage: "camera")
                    .frame(maxWidth: .infinity)
                    .padding()
            }
            .buttonStyle(.borderedProminent)
            .padding(.horizontal, 24)
            .padding(.bottom, 32)
        }
    }
}
