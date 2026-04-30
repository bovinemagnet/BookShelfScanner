// shelfscan/iosApp/iosApp/ui/ReviewView.swift
import SwiftUI
import ShelfScanShared

struct ReviewView: View {
    let items: [MediaItem]
    let onDone: () -> Void

    var body: some View {
        NavigationStack {
            if items.isEmpty {
                ContentUnavailableView(
                    "No Books Detected",
                    systemImage: "books.vertical",
                    description: Text("Try capturing the shelf again with better lighting.")
                )
                .navigationTitle("Review Results")
                .toolbar {
                    ToolbarItem(placement: .confirmationAction) {
                        Button("Done") { onDone() }
                    }
                }
            } else {
                List(items, id: \.id) { item in
                    BookItemRow(item: item)
                }
                .navigationTitle("Review Results")
                .toolbar {
                    ToolbarItem(placement: .confirmationAction) {
                        Button("Save") { onDone() }
                            .buttonStyle(.borderedProminent)
                    }
                    ToolbarItem(placement: .cancellationAction) {
                        Button("Discard") { onDone() }
                            .foregroundColor(.red)
                    }
                }
            }
        }
    }
}

struct BookItemRow: View {
    let item: MediaItem

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(item.title ?? "(no title)")
                .font(.headline)
            Text(item.creatorName ?? "(no author)")
                .font(.subheadline)
                .foregroundColor(.secondary)
            HStack {
                Image(systemName: "circle.fill")
                    .font(.caption2)
                    .foregroundColor(confidenceColor(for: item.confidence.band))
                Text(confidenceLabel(for: item.confidence.band))
                    .font(.caption)
                    .foregroundColor(.secondary)
            }
        }
        .padding(.vertical, 4)
    }

    private func confidenceColor(for band: ConfidenceBand) -> Color {
        switch band {
        case .high: return .green
        case .medium: return .orange
        case .low, .needsReview: return .red
        default: return .gray
        }
    }

    private func confidenceLabel(for band: ConfidenceBand) -> String {
        switch band {
        case .high: return "High"
        case .medium: return "Medium"
        case .low: return "Low"
        case .needsReview: return "Needs Review"
        default: return "Unknown"
        }
    }
}
