import Network
import OrbinKit
import UIKit

/// UIKit owns the window and its status-bar controller; every screen remains shared Compose.
@main
final class OrbinAppDelegate: UIResponder, UIApplicationDelegate {
    override init() {
        super.init()
        // Encrypted DNS must be in place before any HTTP client is built. The applicator is
        // registered first, then Android's default (Cloudflare, fallback allowed) is applied;
        // AppGraph later updates both when settings load. registerBackgroundRefresh can itself
        // initialize AppGraph, so it comes last.
        installEncryptedDns()
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

/// Wires process-wide Network.framework DNS-over-HTTPS to the Kotlin settings: the saved
/// resolver (Cloudflare, Google, OpenDNS or NextDNS) and whether encrypted resolution is
/// required. Reached by address so looking the resolver up does not go through the network's
/// DNS either. When [required] is false, the system may fall back to ordinary DNS if DoH fails,
/// matching Android. When true, lookups fail instead of falling back.
private func installEncryptedDns() {
    EncryptedDns.shared.setApplicator { url, bootstrapIpsCsv, requiredFlag in
        applyOrbinEncryptedDns(
            resolverURL: url,
            bootstrap: bootstrapIpsCsv.split(separator: ",").map(String.init).filter { !$0.isEmpty },
            required: requiredFlag == "true"
        )
    }
    EncryptedDns.shared.applyDefault()
}

private func applyOrbinEncryptedDns(resolverURL: String, bootstrap: [String], required: Bool) {
    guard let resolver = URL(string: resolverURL) else { return }
    let addresses: [NWEndpoint] = bootstrap.map { .hostPort(host: NWEndpoint.Host($0), port: 443) }
    NWParameters.PrivacyContext.default.requireEncryptedNameResolution(
        required,
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
