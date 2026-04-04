import SwiftUI

struct ReviewView: View {
    let onDone: () -> Void

    @State private var detectedItems: [DetectedBookItem] = [
        DetectedBookItem(title: "Clean Code", creator: "Robert C. Martin", confidence: 0.91),
        DetectedBookItem(title: "The Pragmatic Programmer", creator: "Andrew Hunt", confidence: 0.87),
        DetectedBookItem(title: "Design Patterns", creator: "Gang of Four", confidence: 0.73)
    ]

    var body: some View {
        NavigationStack {
            List {
                ForEach($detectedItems) { $item in
                    BookItemRow(item: $item)
                }
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

struct DetectedBookItem: Identifiable {
    let id = UUID()
    var title: String
    var creator: String
    var confidence: Double

    var confidenceBand: String {
        switch confidence {
        case 0.75...: return "High"
        case 0.50...: return "Medium"
        case 0.25...: return "Low"
        default: return "Needs Review"
        }
    }

    var confidenceColor: Color {
        switch confidence {
        case 0.75...: return .green
        case 0.50...: return .orange
        default: return .red
        }
    }
}

struct BookItemRow: View {
    @Binding var item: DetectedBookItem

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            TextField("Title", text: $item.title)
                .font(.headline)
            TextField("Author", text: $item.creator)
                .font(.subheadline)
                .foregroundColor(.secondary)
            HStack {
                Image(systemName: "circle.fill")
                    .font(.caption2)
                    .foregroundColor(item.confidenceColor)
                Text(item.confidenceBand)
                    .font(.caption)
                    .foregroundColor(.secondary)
            }
        }
        .padding(.vertical, 4)
    }
}
