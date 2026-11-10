import Network
import OrbinKit
import UIKit

/// UIKit owns the window and its status-bar controller; every screen remains shared Compose.
@main
final class OrbinAppDelegate: UIResponder, UIApplicationDelegate {
    override init() {
        super.init()
        // This also precedes background URLSession callbacks, which can initialize AppGraph.
        requireEncryptedDns()
        BackgroundRefreshKt.registerBackgroundRefresh()
    }

    func application(
        _ application: UIApplication,
        configurationForConnecting connectingSceneSession: UISceneSession,
        options: UIScene.ConnectionOptions
    ) -> UISceneConfiguration {
        let configuration = UISceneConfiguration(name: "Orbin", sessionRole: connectingSceneSession.role)
        configuration.sceneClass = UIWindowScene.self
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

/// Hands a tapped home-screen quick action to the Kotlin side, which opens its tab: the one that
/// launched the app, or one tapped while it was already running.
private final class OrbinSceneDelegate: UIResponder, UIWindowSceneDelegate {
    var window: UIWindow?

    func scene(
        _ scene: UIScene,
        willConnectTo session: UISceneSession,
        options connectionOptions: UIScene.ConnectionOptions
    ) {
        guard let windowScene = scene as? UIWindowScene else { return }
        if let item = connectionOptions.shortcutItem {
            _ = QuickActions.shared.perform(type: item.type)
        }
        let window = UIWindow(windowScene: windowScene)
        // Compose receives the full window and handles notch/home-indicator safe areas itself.
        window.rootViewController = MainViewControllerKt.MainViewController()
        self.window = window
        window.makeKeyAndVisible()
    }

    func windowScene(
        _ windowScene: UIWindowScene,
        performActionFor shortcutItem: UIApplicationShortcutItem,
        completionHandler: @escaping (Bool) -> Void
    ) {
        completionHandler(QuickActions.shared.perform(type: shortcutItem.type))
    }
}
