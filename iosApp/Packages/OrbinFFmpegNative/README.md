This local SwiftPM package exposes the pinned FFmpegKit Extended C ABI through a
small C shim. It avoids importing upstream C++ umbrella headers into Swift.

The upstream XCFramework is downloaded by SwiftPM and verified against the
SHA-256 in Package.swift. It provides physical-device and Apple-silicon simulator
slices and is dynamically embedded by Xcode. No Flutter runtime is included.

Source/build tag: `v0.10.5-ios`, commit
`4e18e05b007024cdb2574fee6f90e55b27681038` in
https://github.com/akashskypatel/ffmpeg-kit-builders . Binary configuration is
base/small/LGPL, matching NekoSurf's locked 0.5.13 Flutter plugin dependency.
License texts and source notices ship in Orbin/ThirdPartyNotices.

`OrbinNativeTests/MediaConversionTests.swift` requires actual VP8/VP9+Opus
conversion to playable H.264/AAC MP4, even dimensions, and a decoded output frame.
Its tiny fixtures were generated locally with FFmpeg's `testsrc` and `sine`
filters (65×67, 6 fps, 0.6 seconds, libvpx/libvpx-vp9 + libopus); they contain no
third-party media. Tests also cover failed-input cleanup and queued cancellation.
