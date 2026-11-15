import AudioToolbox
import AVFoundation
import Foundation
import CoreMedia
import XCTest
@testable import Orbin

/// These tests decode actual VP8/VP9 + Opus WebM bytes with the linked native dependency.
/// AVFoundation is used to validate/decode the resulting MP4, never to assume WebM support.
final class MediaConversionTests: XCTestCase {
    func testPinnedArgumentContractRejectsQuotesAndControlsWithoutInventedEscapes() {
        XCTAssertTrue(OrbinMediaConverter.supportsPinnedArgument("/Library/Application Support/Orbin/input.webm"))
        XCTAssertTrue(OrbinMediaConverter.supportsPinnedArgument("/private/input 'apostrophe'.webm"))
        XCTAssertFalse(OrbinMediaConverter.supportsPinnedArgument("/private/input \"quoted\".webm"))
        XCTAssertFalse(OrbinMediaConverter.supportsPinnedArgument("/private/input \\\"quoted\\\".webm"))
        XCTAssertFalse(OrbinMediaConverter.supportsPinnedArgument("/private/input\tname.webm"))
        XCTAssertFalse(OrbinMediaConverter.supportsPinnedArgument("/private/input\nname.webm"))
        XCTAssertFalse(OrbinMediaConverter.supportsPinnedArgument("/private/input\rname.webm"))
        XCTAssertFalse(OrbinMediaConverter.supportsPinnedArgument("/private/input\u{0}name.webm"))
        XCTAssertFalse(OrbinMediaConverter.supportsPinnedArgument(""))
    }

    func testVP8AndVP9OddDimensionsBecomePlayableH264AACMP4() throws {
        for name in ["odd-vp8-opus", "odd-vp9-opus"] {
            let fixture = try XCTUnwrap(Bundle(for: Self.self).url(forResource: name, withExtension: "webm"))
            let directory = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString, isDirectory: true)
            try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
            // Caller paths keep their spaces and quotes; only the private job copy has a fixed name.
            let input = directory.appendingPathComponent("input \"\(name)\".webm")
            try FileManager.default.copyItem(at: fixture, to: input)
            let originalBytes = try Data(contentsOf: input)
            let originalProtection = try FileManager.default.attributesOfItem(atPath: input.path)[.protectionKey] as? String
            defer { try? FileManager.default.removeItem(at: directory) }
            let done = expectation(description: "Real \(name) transcoding")
            var result: Result<URL, Error>?
            OrbinMediaConverter.shared.convert(source: input) { value in result = value; done.fulfill() }
            wait(for: [done], timeout: 45)
            let output = try XCTUnwrap(result).get()
            defer { try? FileManager.default.removeItem(at: output.deletingLastPathComponent()) }
            XCTAssertEqual(output.pathExtension, "mp4")
            XCTAssertTrue(output.path.contains("/Orbin/MediaExport/"))
            XCTAssertTrue(FileManager.default.fileExists(atPath: input.path), "The owned original remains intact")
            XCTAssertEqual(try Data(contentsOf: input), originalBytes, "Staging must not modify the caller's original")
            XCTAssertEqual(try FileManager.default.attributesOfItem(atPath: input.path)[.protectionKey] as? String,
                           originalProtection, "Protecting the job copy must not mutate the original's protection")
            XCTAssertEqual(try FileManager.default.contentsOfDirectory(atPath: output.deletingLastPathComponent().path),
                           ["converted.mp4"], "A successful job must retain only its converted output")
            let asset = AVURLAsset(url: output)
            XCTAssertTrue(asset.isPlayable)
            let video = try XCTUnwrap(asset.tracks(withMediaType: .video).first)
            XCTAssertEqual(video.naturalSize.width, 64)
            XCTAssertEqual(video.naturalSize.height, 66)
            let videoFormat = try XCTUnwrap(video.formatDescriptions.first) as! CMFormatDescription
            XCTAssertEqual(CMFormatDescriptionGetMediaSubType(videoFormat), kCMVideoCodecType_H264)
            let audio = try XCTUnwrap(asset.tracks(withMediaType: .audio).first)
            let audioFormat = try XCTUnwrap(audio.formatDescriptions.first) as! CMFormatDescription
            XCTAssertEqual(CMFormatDescriptionGetMediaSubType(audioFormat), kAudioFormatMPEG4AAC)
            let generator = AVAssetImageGenerator(asset: asset)
            XCTAssertGreaterThan(try generator.copyCGImage(at: .zero, actualTime: nil).width, 0,
                                 "AVFoundation must decode an actual output frame")
            XCTAssertGreaterThan(CMTimeGetSeconds(asset.duration), 0.4)
        }
    }

    func testCorruptWebMDoesNotLeaveAnExportOrModifyInput() throws {
        let source = FileManager.default.temporaryDirectory.appendingPathComponent("\(UUID().uuidString).webm")
        let bytes = Data("This is not a WebM stream.".utf8)
        try bytes.write(to: source)
        defer { try? FileManager.default.removeItem(at: source) }
        let before = try exportDirectories()
        let done = expectation(description: "Reject corrupt stream")
        var result: Result<URL, Error>?
        OrbinMediaConverter.shared.convert(source: source) { value in result = value; done.fulfill() }
        wait(for: [done], timeout: 15)
        if case .some(.success) = result { XCTFail("A failed native conversion must not invent an MP4") }
        XCTAssertNotNil(result)
        XCTAssertEqual(try Data(contentsOf: source), bytes)
        XCTAssertEqual(try exportDirectories(), before)
    }

    func testQueuedCancellationDoesNotCreateAConvertedFile() throws {
        let queue = DispatchQueue(label: "orbin.test.suspended-converter")
        queue.suspend()
        let converter = OrbinMediaConverter(queue: queue)
        let done = expectation(description: "Cancel queued conversion")
        let request = UUID().uuidString
        var result: Result<URL, Error>?
        converter.convert(requestID: request, source: URL(fileURLWithPath: "/unused.webm")) { value in result = value; done.fulfill() }
        converter.cancel(request)
        queue.resume()
        wait(for: [done], timeout: 5)
        guard case .some(.failure(let error)) = result else {
            return XCTFail("Cancellation must stop queued native work")
        }
        XCTAssertEqual(error as? OrbinMediaConverter.ConversionError, .cancelled)
    }

    func testCancellationBeforeRegistrationIsConsumedWithoutStartingNativeWork() throws {
        let converter = OrbinMediaConverter()
        let request = UUID().uuidString
        let before = try exportDirectories()
        converter.cancel(request)
        let done = expectation(description: "Consume pre-registration cancellation")
        var result: Result<URL, Error>?
        // This nonexistent path would produce invalidInput if conversion started at all.
        converter.convert(requestID: request, source: URL(fileURLWithPath: "/never-read.webm")) {
            result = $0
            done.fulfill()
        }
        wait(for: [done], timeout: 5)
        guard case .some(.failure(let error)) = result else {
            return XCTFail("Pre-registration cancellation must stop native work")
        }
        XCTAssertEqual(error as? OrbinMediaConverter.ConversionError, .cancelled)
        XCTAssertEqual(try exportDirectories(), before)
        // Its tombstone is consumed exactly once and cannot cancel an unrelated UUID.
        let unrelated = expectation(description: "Independent request")
        var otherResult: Result<URL, Error>?
        converter.convert(source: URL(fileURLWithPath: "/other-unread.webm")) {
            otherResult = $0
            unrelated.fulfill()
        }
        wait(for: [unrelated], timeout: 5)
        guard case .some(.failure(let otherError)) = otherResult else {
            return XCTFail("An independent invalid source must fail normally")
        }
        XCTAssertNotEqual(otherError as? OrbinMediaConverter.ConversionError, .cancelled)
    }

    private func exportDirectories() throws -> Set<String> {
        let support = try FileManager.default.url(for: .applicationSupportDirectory, in: .userDomainMask,
                                                   appropriateFor: nil, create: true)
        let path = support.appendingPathComponent("Orbin/MediaExport", isDirectory: true)
        return Set((try? FileManager.default.contentsOfDirectory(atPath: path.path)) ?? [])
    }
}
