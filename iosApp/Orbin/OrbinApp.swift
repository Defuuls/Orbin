import Network
import OrbinKit
import UIKit

/// UIKit owns the window and its status-bar controller; every screen remains shared Compose.
@main
final class OrbinAppDelegate: UIResponder, UIApplicationDelegate {
    override init() {
        super.init()
        // Encrypted DNS must be in place before any HTTP client is built. The applicator is
        // registered first, then the default resolver (Cloudflare, encryption required) is
        // applied; AppGraph later applies the saved resolver when settings load.
        // registerBackgroundRefresh can itself initialize AppGraph, so it comes last.
        installEncryptedDns()
        OrbinCloudSync.shared.install()
        OrbinMediaConverter.shared.install()
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
/// resolver (Cloudflare, Google, OpenDNS or NextDNS), reached by address so looking the resolver
/// up does not go through the network's DNS either.
///
/// Kotlin always asks for `required`. `requireEncryptedNameResolution(false, ...)` does not mean
/// "prefer encrypted, fall back if blocked": it means no requirement, so the system resolver is
/// used and `fallbackResolver` is never consulted. Only `true` makes this process's lookups go to
/// the DoH resolver (when the system's own resolver is not already encrypted). There is no
/// fallback to ordinary DNS: on a network that blocks the resolver, lookups fail.
///
/// This covers URLSession and Network.framework in this process only. WKWebView, AVPlayer and
/// background URLSession transfers resolve names elsewhere; Kotlin's `SystemMediaPolicy` turns
/// those off when the reader chooses strict encrypted DNS.
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
