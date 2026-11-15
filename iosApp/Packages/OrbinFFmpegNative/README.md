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

The pinned native implementation serializes even its argv API into a command
string. Its downstream FFmpeg/FFprobe parsers only recognize leading double
quotes and provide no escape syntax. Orbin copies the validated original into
the existing protected, backup-excluded job directory as `input.webm`, writes
`converted.mp4` alongside it, and rejects embedded double quotes/control
characters in actual backend arguments. Ordinary spaces in Application Support
paths remain valid. FFprobe's input stays last because its pinned argument-count
pass does not reset quoted-token state. The original is never renamed or
hardlinked; every outcome removes the input copy, and failures remove the job.
No shell command is executed.

Relevant pinned source: `FFmpegKit/src/FFmpegKitConfig.cpp` (`buildCommandString`,
`executeFFmpeg`, `executeFFprobe`) and the `split_args` functions in
`FFmpegKit/src/ffmpeg_lib.c` and `FFmpegKit/src/ffprobe_lib.c` at the commit above.

`OrbinNativeTests/MediaConversionTests.swift` requires actual VP8/VP9+Opus
conversion to playable H.264/AAC MP4, even dimensions, and a decoded output frame.
Its tiny fixtures were generated locally with FFmpeg's `testsrc` and `sine`
filters (65×67, 6 fps, 0.6 seconds, libvpx/libvpx-vp9 + libopus); they contain no
third-party media. Tests also cover failed-input cleanup and queued cancellation.
The quote/space filename test retains the caller's bytes and file protection,
and requires that a successful output directory contain no staged original.
