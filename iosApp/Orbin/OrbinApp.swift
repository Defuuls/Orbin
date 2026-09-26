import OrbinKit
import SwiftUI

/// The iOS app is a single full-screen Compose view: every screen, and navigation between them,
/// lives in the shared Kotlin code (`:app-ios`, linked here as the OrbinKit framework).
@main
struct OrbinApp: App {
    init() {
        // iOS only runs a background task registered before launch finishes.
        BackgroundRefreshKt.registerBackgroundRefresh()
    }

    var body: some Scene {
        WindowGroup {
            ComposeView()
                // Compose lays out around the notch and home indicator itself.
                .ignoresSafeArea()
        }
    }
}

private struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}
