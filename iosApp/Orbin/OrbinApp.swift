import Network
import OrbinKit
import SwiftUI
import UIKit

/// The iOS app is a single full-screen Compose view: every screen, and navigation between them,
/// lives in the shared Kotlin code (`:app-ios`, linked here as the OrbinKit framework).
@main
struct OrbinApp: App {
    @UIApplicationDelegateAdaptor(OrbinBackgroundSessionAppDelegate.self) private var backgroundSessionDelegate

    init() {
        requireEncryptedDns()
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

/// Encrypted DNS for every connection the app makes, URLSession included, as Android's always-on
/// DNS-over-HTTPS: some networks (mobile carriers especially) block sites by answering their DNS
/// lookups wrongly. Cloudflare is Android's default resolver, reached by address so looking it up
/// does not go through the network's DNS either. Networks whose own DNS is already encrypted keep it.
private func requireEncryptedDns() {
    guard let resolver = URL(string: "https://cloudflare-dns.com/dns-query") else { return }
    let addresses: [NWEndpoint] = ["1.1.1.1", "1.0.0.1"].map { .hostPort(host: NWEndpoint.Host($0), port: 443) }
    NWParameters.PrivacyContext.default.requireEncryptedNameResolution(
        true,
        fallbackResolver: .https(resolver, serverAddresses: addresses)
    )
}

private struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}


/// Reconnects background URLSession when iOS relaunches Orbin to deliver transfer events, and
/// puts `OrbinSceneDelegate` on the window scene so home-screen quick actions reach the app.
private final class OrbinBackgroundSessionAppDelegate: NSObject, UIApplicationDelegate {
    func application(
        _ application: UIApplication,
        configurationForConnecting connectingSceneSession: UISceneSession,
        options: UIScene.ConnectionOptions
    ) -> UISceneConfiguration {
        let configuration = UISceneConfiguration(name: nil, sessionRole: connectingSceneSession.role)
        configuration.delegateClass = OrbinSceneDelegate.self
        return configuration
    }

    func application(
        _ application: UIApplication,
        handleEventsForBackgroundURLSession identifier: String,
        completionHandler: @escaping () -> Void
    ) {
        IosBackgroundMediaFetchKt.handleBackgroundMediaDownloadEvents(
            identifier: identifier,
            completionHandler: completionHandler
        )
    }
}

/// Hands a tapped home-screen quick action to the Kotlin side, which opens its tab: the one that
/// launched the app, or one tapped while it was already running.
private final class OrbinSceneDelegate: NSObject, UIWindowSceneDelegate {
    func scene(
        _ scene: UIScene,
        willConnectTo session: UISceneSession,
        options connectionOptions: UIScene.ConnectionOptions
    ) {
        if let item = connectionOptions.shortcutItem {
            _ = QuickActions.shared.perform(type: item.type)
        }
    }

    func windowScene(
        _ windowScene: UIWindowScene,
        performActionFor shortcutItem: UIApplicationShortcutItem,
        completionHandler: @escaping (Bool) -> Void
    ) {
        completionHandler(QuickActions.shared.perform(type: shortcutItem.type))
    }
}
