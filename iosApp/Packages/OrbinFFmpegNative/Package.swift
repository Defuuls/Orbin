// swift-tools-version: 5.9
import PackageDescription

// The LGPL, non-GPL base build used by NekoSurf 0.5.13. A checksum covers the entire
// upstream XCFramework archive, including device and Apple-silicon simulator slices.
let package = Package(
    name: "OrbinFFmpegNative",
    platforms: [.iOS(.v16)],
    products: [.library(name: "OrbinFFmpegNative", targets: ["OrbinFFmpegNative"])],
    targets: [
        .binaryTarget(
            name: "ffmpegkit",
            url: "https://github.com/akashskypatel/ffmpeg-kit-builders/releases/download/v0.10.5-ios/bundle-base-ios-universal-small-lgpl.xcframework.zip",
            checksum: "34d13d53814e1bc9148354d62d122a058e1d07d8a81b3cba3e5b89edf645316c"
        ),
        .target(name: "OrbinFFmpegNative", dependencies: ["ffmpegkit"], publicHeadersPath: "include")
    ]
)
