import AVFoundation
import Foundation
import CoreMedia
import Darwin
import OrbinFFmpegNative
import OrbinKit

/// Local WebM decoding and VideoToolbox H.264/AAC encoding, independent of browser playback.
/// FFmpeg executes on its own queue; cancellation targets this job's session only.
final class OrbinMediaConverter {
    static let shared = OrbinMediaConverter()
    private let queue: DispatchQueue
    private let lock = NSLock()
    private var jobs: [UUID: Job] = [:]
    // A coroutine may cancel immediately before its Swift registration reaches this lock.
    private var cancelledBeforeRegistration: Set<UUID> = []

    init(queue: DispatchQueue = DispatchQueue(label: "io.github.defuuls.orbin.media-conversion", qos: .userInitiated)) {
        self.queue = queue
    }

    func install() {
        NativeMediaConversion.shared.setHandler { [weak self] operation, requestID, sourcePath, completion in
            guard let self else { _ = completion(nil, "Media conversion is unavailable."); return }
            if operation == "cancel" {
                self.cancel(requestID)
                _ = completion(nil, nil)
                return
            }
            self.convert(requestID: requestID, source: URL(fileURLWithPath: sourcePath)) { result in
                switch result {
                case .success(let output): _ = completion(output.path, nil)
                case .failure: _ = completion(nil, "This video could not be converted for Photos.")
                }
            }
        }
    }

    func convert(requestID: String = UUID().uuidString, source: URL,
                 completion: @escaping (Result<URL, Error>) -> Void) {
        guard let identity = UUID(uuidString: requestID) else {
            DispatchQueue.main.async { completion(.failure(ConversionError.invalidInput)) }
            return
        }
        let job = Job()
        lock.lock()
        guard jobs[identity] == nil else {
            lock.unlock()
            DispatchQueue.main.async { completion(.failure(ConversionError.invalidInput)) }
            return
        }
        if cancelledBeforeRegistration.remove(identity) != nil { job.cancel() }
        jobs[identity] = job
        lock.unlock()
        queue.async { [self] in
            let result = Result { try run(source: source, job: job) }
            // Retain registration through delivery: a cancel while the main queue is busy still
            // reaches this exact job and cannot leave a completed but unconsumed export behind.
            DispatchQueue.main.async { [self] in
                let delivered: Result<URL, Error>
                do {
                    try job.checkCancellation()
                    delivered = result
                } catch {
                    if case .success(let output) = result {
                        try? FileManager.default.removeItem(at: output.deletingLastPathComponent())
                    }
                    delivered = .failure(error)
                }
                completion(delivered)
                lock.lock()
                if jobs[identity] === job { jobs.removeValue(forKey: identity) }
                lock.unlock()
            }
        }
    }

    func cancel(_ requestID: String) {
        guard let identity = UUID(uuidString: requestID) else { return }
        lock.lock()
        if let job = jobs[identity] {
            job.cancel()
        } else {
            cancelledBeforeRegistration.insert(identity)
        }
        lock.unlock()
    }

    private func run(source: URL, job: Job) throws -> URL {
        try job.checkCancellation()
        let source = source.standardizedFileURL.resolvingSymlinksInPath()
        let home = URL(fileURLWithPath: NSHomeDirectory(), isDirectory: true).resolvingSymlinksInPath().path
        guard source.isFileURL, source.path.hasPrefix(home + "/"),
              try source.resourceValues(forKeys: [.isRegularFileKey]).isRegularFile == true,
              !orbin_ffmpeg_is_gpl(), !orbin_ffmpeg_is_nonfree() else { throw ConversionError.invalidInput }
        let manager = FileManager.default
        let support = try manager.url(for: .applicationSupportDirectory, in: .userDomainMask,
                                      appropriateFor: nil, create: true)
        let directory = support.appendingPathComponent("Orbin/MediaExport/\(UUID().uuidString)", isDirectory: true)
        let stagedInput = directory.appendingPathComponent("input.webm")
        let output = directory.appendingPathComponent("converted.mp4")
        do {
            try manager.createDirectory(at: directory, withIntermediateDirectories: true,
                                        attributes: [.protectionKey: FileProtectionType.completeUntilFirstUserAuthentication])
            var values = URLResourceValues()
            values.isExcludedFromBackup = true
            var protectedDirectory = directory
            try protectedDirectory.setResourceValues(values)
            guard [stagedInput.path, output.path].allSatisfy(Self.supportsPinnedArgument) else {
                throw ConversionError.invalidInput
            }
            // The pinned backend joins argv and reparses without escapes. Copy to fixed names;
            // never alter the caller's bytes or change its file protection through a hard link.
            defer { try? manager.removeItem(at: stagedInput) }
            try manager.copyItem(at: source, to: stagedInput)
            try manager.setAttributes([.protectionKey: FileProtectionType.completeUntilFirstUserAuthentication],
                                      ofItemAtPath: stagedInput.path)
            try job.checkCancellation()
            // The pinned ffprobe parser requires a quoted input to remain the last argument.
            let probe = try? command(["-v", "error", "-protocol_whitelist", "file,pipe", "-f", Self.inputFormat, "-show_entries",
                                      "format=duration,bit_rate", "-of", "json", stagedInput.path], probe: true, job: job)
            let probeData = probe?.data(using: .utf8)
            let probeRoot = probeData.flatMap { try? JSONSerialization.jsonObject(with: $0) } as? [String: Any]
            let format = probeRoot?["format"] as? [String: Any]
            let parsedBitrate = (format?["bit_rate"] as? String).flatMap(Double.init)
            let inputBitrate = parsedBitrate.flatMap { $0.isFinite && $0 > 0 ? $0 : nil }
            // Match Neko's twice-source-bitrate target; constrain corrupt/unreasonable metadata.
            let bitrate = inputBitrate.map { Int(max(64_000, min(80_000_000, $0 * 2))) } ?? 4_000_000
            try job.checkCancellation()
            _ = try command([
                "-nostdin", "-hide_banner", "-loglevel", "error", "-y",
                "-protocol_whitelist", "file,pipe",
                "-f", Self.inputFormat, "-codec_whitelist", Self.inputCodecs, "-i", stagedInput.path,
                "-map", "0:v:0", "-map", "0:a:0?", "-sn", "-dn",
                "-vf", "crop=trunc(iw/2)*2:trunc(ih/2)*2:0:0",
                "-c:v", "h264_videotoolbox", "-allow_sw", "1", "-b:v", String(bitrate),
                "-pix_fmt", "yuv420p", "-c:a", "aac", "-movflags", "+faststart", output.path
            ], probe: false, job: job)
            try job.checkCancellation()
            guard (try output.resourceValues(forKeys: [.fileSizeKey]).fileSize ?? 0) > 0 else { throw ConversionError.failed }
            let asset = AVURLAsset(url: output)
            guard let video = asset.tracks(withMediaType: .video).first,
                  let format = video.formatDescriptions.first,
                  CMFormatDescriptionGetMediaSubType(format as! CMFormatDescription) == kCMVideoCodecType_H264 else { throw ConversionError.failed }
            try manager.setAttributes([.protectionKey: FileProtectionType.completeUntilFirstUserAuthentication], ofItemAtPath: output.path)
            return output
        } catch {
            try? manager.removeItem(at: directory)
            throw error
        }
    }

    /// Exact pinned parser has raw double-quote delimiters, no escapes, and loses empty tokens.
    /// The file comes from an anonymous poster, and the bundled FFmpeg can open far more formats
    /// than a WebM needs. Naming the container stops FFmpeg guessing a different demuxer from
    /// the file's contents, and the codec list refuses every decoder but the ones WebM uses, so
    /// a file that only claims to be WebM cannot reach the rest of the library's parsers.
    static let inputFormat = "matroska"
    /// Native and library decoder names for VP8, VP9, Opus and Vorbis; a build has one or the other.
    static let inputCodecs = "vp8,vp9,libvpx,libvpx-vp9,opus,libopus,vorbis,libvorbis"

    static func supportsPinnedArgument(_ value: String) -> Bool {
        !value.isEmpty && !value.contains("\"") &&
            value.unicodeScalars.allSatisfy { !CharacterSet.controlCharacters.contains($0) }
    }

    private func command(_ arguments: [String], probe: Bool, job: Job) throws -> String {
        // No shell is involved, but this pinned native backend serializes and reparses argv.
        // Ordinary spaces round-trip; quotes/controls require the fixed protected staging above.
        guard arguments.allSatisfy(Self.supportsPinnedArgument) else { throw ConversionError.invalidInput }
        let allocated = arguments.map { strdup($0) }
        defer { allocated.forEach { free($0) } }
        guard allocated.allSatisfy({ $0 != nil }) else { throw ConversionError.failed }
        let arguments: [UnsafePointer<CChar>?] = allocated.map { value in value.map { UnsafePointer<CChar>($0) } }
        guard let session = arguments.withUnsafeBufferPointer({
            orbin_ffmpeg_create(Int32($0.count), $0.baseAddress, probe)
        }) else { throw ConversionError.failed }
        defer { job.detach(session); orbin_ffmpeg_close(session, probe) }
        try job.attach(session, probe: probe)
        orbin_ffmpeg_execute(session, probe)
        try job.checkCancellation()
        guard orbin_ffmpeg_result(session) == 0 else { throw ConversionError.failed }
        guard let output = orbin_ffmpeg_output(session) else { return "" }
        defer { orbin_ffmpeg_free(output) }
        return String(cString: output)
    }

    enum ConversionError: Error, Equatable { case invalidInput, cancelled, failed }

    private final class Job {
        private let lock = NSLock()
        private var cancelled = false
        private var session: UnsafeMutableRawPointer?
        private var probe = false

        func attach(_ value: UnsafeMutableRawPointer, probe: Bool) throws {
            lock.lock()
            defer { lock.unlock() }
            guard !cancelled else { throw ConversionError.cancelled }
            session = value
            self.probe = probe
        }
        func detach(_ value: UnsafeMutableRawPointer) {
            lock.lock()
            if session == value { session = nil }
            lock.unlock()
        }
        func cancel() {
            lock.lock()
            cancelled = true
            if let session { orbin_ffmpeg_cancel(session, probe) }
            lock.unlock()
        }
        func checkCancellation() throws {
            lock.lock()
            defer { lock.unlock() }
            if cancelled { throw ConversionError.cancelled }
        }
    }
}
