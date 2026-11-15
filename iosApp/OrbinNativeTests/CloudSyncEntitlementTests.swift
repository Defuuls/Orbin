import Foundation
import XCTest
@testable import Orbin

final class CloudSyncEntitlementTests: XCTestCase {
    func testUnsignedAndTruncatedExecutablesFailClosed() {
        XCTAssertNil(OrbinCloudEntitlements.signatureEntitlements(Data()))
        for size in [4, 16, 32, 48, 64] {
            XCTAssertNil(OrbinCloudEntitlements.signatureEntitlements(Data(repeating: 0, count: size)))
        }
    }

    func testReadsOnlyABoundedEntitlementBlobFromCodeSignature() throws {
        let values: [String: Any] = [
            "com.apple.developer.icloud-services": ["CloudKit"],
            "com.apple.developer.icloud-container-identifiers": ["iCloud.io.github.defuuls.orbin"]
        ]
        let plist = try PropertyListSerialization.data(fromPropertyList: values, format: .xml, options: 0)
        var data = Data(repeating: 0, count: 64)
        write(0xfeedfacf, at: 0, into: &data)
        write(1, at: 16, into: &data)
        write(0x1d, at: 32, into: &data)
        write(16, at: 36, into: &data)
        write(64, at: 40, into: &data)
        let signatureLength = 28 + plist.count
        write(UInt32(signatureLength), at: 44, into: &data)
        data.append(contentsOf: [UInt8](repeating: 0, count: signatureLength))
        write(0xfade0cc0, at: 64, into: &data, big: true)
        write(UInt32(signatureLength), at: 68, into: &data, big: true)
        write(1, at: 72, into: &data, big: true)
        write(5, at: 76, into: &data, big: true)
        write(20, at: 80, into: &data, big: true)
        write(0xfade7171, at: 84, into: &data, big: true)
        write(UInt32(plist.count + 8), at: 88, into: &data, big: true)
        data.replaceSubrange(92..<data.count, with: plist)
        let decoded = try XCTUnwrap(OrbinCloudEntitlements.signatureEntitlements(data))
        XCTAssertEqual(decoded["com.apple.developer.icloud-services"] as? [String], ["CloudKit"])
        XCTAssertNil(OrbinCloudEntitlements.signatureEntitlements(data.dropLast()))
        write(UInt32.max, at: 80, into: &data, big: true)
        XCTAssertNil(OrbinCloudEntitlements.signatureEntitlements(data))
    }

    private func write(_ value: UInt32, at offset: Int, into data: inout Data, big: Bool = false) {
        var encoded = big ? value.bigEndian : value.littleEndian
        withUnsafeBytes(of: &encoded) { bytes in
            data.replaceSubrange(offset..<(offset + 4), with: bytes)
        }
    }
}
