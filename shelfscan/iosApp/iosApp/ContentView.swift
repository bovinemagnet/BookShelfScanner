// shelfscan/iosApp/iosApp/ContentView.swift
import SwiftUI
import ShelfScanShared

struct ContentView: View {
    @StateObject private var router = AppRouter()
    @State private var lastSession: ScanSession? = nil

    var body: some View {
        switch router.currentScreen {
        case .home:
            HomeView(onStartScan: { router.navigate(to: .scan) })
        case .scan:
            ScanView { session in
                lastSession = session
                router.navigate(to: .review)
            }
        case .review:
            ReviewView(
                items: lastSession?.detectedItems ?? [],
                onDone: {
                    lastSession = nil
                    router.navigate(to: .home)
                }
            )
        }
    }
}

enum AppScreen {
    case home, scan, review
}

final class AppRouter: ObservableObject {
    @Published var currentScreen: AppScreen = .home

    func navigate(to screen: AppScreen) {
        currentScreen = screen
    }
}
