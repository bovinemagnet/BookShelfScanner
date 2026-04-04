import SwiftUI

struct ContentView: View {
    @StateObject private var router = AppRouter()

    var body: some View {
        switch router.currentScreen {
        case .home:
            HomeView(onStartScan: { router.navigate(to: .scan) })
        case .scan:
            ScanView(onScanComplete: { router.navigate(to: .review) })
        case .review:
            ReviewView(onDone: { router.navigate(to: .home) })
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
