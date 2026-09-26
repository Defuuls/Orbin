import Network
import OrbinKit
import SwiftUI

/// The iOS app is a single full-screen Compose view: every screen, and navigation between them,
/// lives in the shared Kotlin code (`:app-ios`, linked here as the OrbinKit framework).
@main
struct OrbinApp: App {
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
