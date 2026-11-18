import CloudKit
import CryptoKit
import Foundation
import OrbinKit

/// A private-database transport. Each Kotlin sync lease owns its fetched records and staging.
@MainActor
final class OrbinCloudSync {
    static let shared = OrbinCloudSync()
    private let zone = CKRecordZone.ID(zoneName: "OrbinPortableData")
    private var containerID: String { "iCloud.\(Bundle.main.bundleIdentifier ?? "io.github.defuuls.orbin")" }
    private var database: CKDatabase { CKContainer(identifier: containerID).privateCloudDatabase }
    private let decoder = JSONDecoder()
    private var requests: [String: RunningRequest] = [:]
    private var cancelledRequests: Set<String> = []
    private var sessions: [String: Session] = [:]
    private var closedSessions: Set<String> = []
    private var installed = false
    private init() {}

    private final class Session {
        let id: String
        var records: [String: CKRecord] = [:]
        var staged: Set<URL> = []
        var directory: URL?
        var activeRequest: String?
        var closed = false
        init(_ id: String) { self.id = id }
    }

    private struct RunningRequest {
        let operation: String
        let sessionID: String?
        let task: Task<Void, Never>
    }

    private struct Request: Decodable {
        let requestId: String
        let sessionId: String?
        let items: [Item]
        private enum CodingKeys: String, CodingKey { case requestId, sessionId, items }
        init(from decoder: Decoder) throws {
            let values = try decoder.container(keyedBy: CodingKeys.self)
            let rawID = try values.decode(String.self, forKey: .requestId)
            guard let id = UUID(uuidString: rawID) else { throw SyncError.invalidRequest }
            requestId = id.uuidString
            if let rawSession = try values.decodeIfPresent(String.self, forKey: .sessionId) {
                guard let session = UUID(uuidString: rawSession) else { throw SyncError.invalidRequest }
                sessionId = session.uuidString
            } else {
                sessionId = nil
            }
            items = try values.decodeIfPresent([Item].self, forKey: .items) ?? []
        }
    }

    struct Item: Codable {
        var id: String
        var revision: Int64
        var origin: String
        var payload: String
        var deleted: Bool
        var assetPath: String?
        var thumbnailAssetPath: String?
        private enum CodingKeys: String, CodingKey {
            case id, revision, origin, payload, deleted, assetPath, thumbnailAssetPath
        }
        init(id: String, revision: Int64, origin: String, payload: String, deleted: Bool = false,
             assetPath: String? = nil, thumbnailAssetPath: String? = nil) {
            self.id = id; self.revision = revision; self.origin = origin; self.payload = payload
            self.deleted = deleted; self.assetPath = assetPath; self.thumbnailAssetPath = thumbnailAssetPath
        }
        init(from decoder: Decoder) throws {
            let values = try decoder.container(keyedBy: CodingKeys.self)
            id = try values.decode(String.self, forKey: .id)
            revision = try values.decode(Int64.self, forKey: .revision)
            origin = try values.decode(String.self, forKey: .origin)
            payload = try values.decode(String.self, forKey: .payload)
            deleted = try values.decodeIfPresent(Bool.self, forKey: .deleted) ?? false
            assetPath = try values.decodeIfPresent(String.self, forKey: .assetPath)
            thumbnailAssetPath = try values.decodeIfPresent(String.self, forKey: .thumbnailAssetPath)
        }
    }

    struct Reply: Encodable {
        var available = true
        var detail = ""
        var items: [Item] = []
        var error: String?
        var accountId: String?
    }

    func install() {
        guard !installed else { return }
        installed = true
        // No owned library file lives in the inbox; a previous process may have died staging.
        if let support = try? FileManager.default.url(for: .applicationSupportDirectory,
            in: .userDomainMask, appropriateFor: nil, create: true) {
            try? FileManager.default.removeItem(at: support.appendingPathComponent("CloudInbox", isDirectory: true))
        }
        NativeCloudSync.shared.setHandler { [weak self] operation, request, completion in
            Task { @MainActor in
                guard let self else {
                    completion(Self.encode(Reply(available: false, detail: "iCloud is unavailable.")))
                    return
                }
                if operation == "cancel" {
                    self.cancelRequest(request)
                    completion(Self.encode(Reply()))
                    return
                }
                do {
                    let envelope = try self.decoder.decode(Request.self, from: Data(request.utf8))
                    guard UUID(uuidString: envelope.requestId) != nil,
                          envelope.sessionId.map({ UUID(uuidString: $0) != nil }) ?? true,
                          self.requests[envelope.requestId] == nil else { throw SyncError.invalidRequest }
                    // Cancellation can reach the actor before its registration task does.
                    if self.cancelledRequests.remove(envelope.requestId) != nil {
                        completion(Self.encode(Self.cancelledReply()))
                        return
                    }
                    let task = Task { @MainActor in
                        await self.perform(operation, request: envelope, completion: { value in
                            _ = completion(value)
                        })
                    }
                    self.requests[envelope.requestId] = RunningRequest(
                        operation: operation, sessionID: envelope.sessionId, task: task)
                } catch {
                    completion(Self.encode(Reply(available: false, detail: "Invalid iCloud request.", error: "Invalid iCloud request.")))
                }
            }
        }
    }

    private func perform(_ operation: String, request: Request, completion: @escaping (String) -> Void) async {
        let reply: Reply
        do {
            try Task.checkCancellation()
            let handledReply = try await handle(operation, request: request)
            try Task.checkCancellation()
            reply = handledReply
        } catch is CancellationError {
            closeSession(request.sessionId, excluding: request.requestId)
            reply = Self.cancelledReply()
        } catch {
            closeSession(request.sessionId, excluding: request.requestId)
            // CloudKit descriptions may contain record contents; never forward or log them.
            let detail = Self.describe(error)
            reply = Reply(available: false, detail: detail, error: detail)
        }
        requests.removeValue(forKey: request.requestId)
        completion(Self.encode(reply))
    }

    private func handle(_ operation: String, request: Request) async throws -> Reply {
        if operation == "cleanup" {
            guard request.sessionId != nil else { throw SyncError.invalidRequest }
            closeSession(request.sessionId, excluding: request.requestId)
            return Reply()
        }
        // Constructing CKContainer without a matching signature can terminate a sideloaded app.
        guard OrbinCloudEntitlements.allows(container: containerID) else {
            return Reply(available: false, detail: "This build is not signed for Orbin iCloud sync.")
        }
        let status = try await CKContainer(identifier: containerID).accountStatus()
        try Task.checkCancellation()
        guard status == .available else {
            return Reply(available: false, detail: "Sign in to iCloud in iOS Settings to sync.")
        }
        let account = try await CKContainer(identifier: containerID).userRecordID()
        try Task.checkCancellation()
        if operation != "availability",
           UserDefaults.standard.string(forKey: "orbin.cloud.account") != account.recordName {
            return Reply(available: false, detail: "Your iCloud account changed. Enable sync again to continue.",
                         error: "Your iCloud account changed. Enable sync again to continue.")
        }
        switch operation {
        case "availability":
            return Reply(detail: "Available. Sync is off until you enable it.", accountId: account.recordName)
        case "fetch", "save":
            let session = try beginSession(request)
            defer { if session.activeRequest == request.requestId { session.activeRequest = nil } }
            if operation == "fetch" {
                _ = try await database.save(CKRecordZone(zoneID: zone))
                try check(session)
                return Reply(items: try await fetchAll(known: request.items, session: session))
            }
            guard request.items.count <= 100 else { throw SyncError.invalidRequest }
            try await save(request.items, session: session)
            return Reply()
        case "deleteAll":
            // No cancelled older fetch/save may recreate or repopulate the zone after deletion.
            let older = requests.filter { $0.key != request.requestId && ["fetch", "save"].contains($0.value.operation) }
            for (_, entry) in older { entry.task.cancel() }
            for id in Array(sessions.keys) { closeSession(id, excluding: request.requestId) }
            for (_, entry) in older { await entry.task.value }
            try Task.checkCancellation()
            do { _ = try await database.deleteRecordZone(withID: zone) }
            catch let error as CKError where error.code == .zoneNotFound || error.code == .unknownItem { }
            try Task.checkCancellation()
            return Reply(detail: "Orbin iCloud data deleted.")
        default: throw SyncError.invalidRequest
        }
    }

    private func beginSession(_ request: Request) throws -> Session {
        guard let id = request.sessionId, !closedSessions.contains(id) else { throw CancellationError() }
        let session = sessions[id] ?? Session(id)
        try check(session)
        guard session.activeRequest == nil else { throw SyncError.invalidRequest }
        sessions[id] = session
        session.activeRequest = request.requestId
        return session
    }

    private func check(_ session: Session) throws {
        try Task.checkCancellation()
        guard !session.closed, !closedSessions.contains(session.id) else { throw CancellationError() }
    }

    private func fetchAll(known: [Item], session: Session) async throws -> [Item] {
        try check(session)
        // This lease's previous staging, if any, is never shared with a newer sync generation.
        releaseFiles(session)
        session.records.removeAll()
        let knownByID = Dictionary(known.map { ($0.id, $0) }, uniquingKeysWith: { first, _ in first })
        let keys = Field.sensitive.flatMap { [$0.encrypted, $0.legacy] }
            + ["revision", "deleted", "hasFile", "hasThumbnail"]
        var result = try await database.records(
            matching: CKQuery(recordType: "OrbinItem", predicate: NSPredicate(value: true)),
            inZoneWith: zone, desiredKeys: keys, resultsLimit: 100)
        try check(session)
        var items: [Item] = []
        while true {
            for (_, match) in result.matchResults {
                try check(session)
                let record = try match.get()
                guard let id = Field.itemID.read(record),
                      let revision = record["revision"] as? Int64,
                      let origin = Field.origin.read(record),
                      let payload = Field.payload.read(record),
                      id.utf8.count <= 1024, payload.utf8.count <= 1_048_576,
                      revision >= 0 else { throw SyncError.invalidRequest }
                var item = Item(id: id, revision: revision, origin: origin, payload: payload,
                                deleted: (record["deleted"] as? Int64 ?? 0) == 1)
                let local = knownByID[id]
                let needsFile = !item.deleted && (record["hasFile"] as? Int64 ?? 0) == 1
                    && (local?.revision != revision || local?.origin != origin || local?.assetPath == nil)
                let needsThumbnail = !item.deleted && (record["hasThumbnail"] as? Int64 ?? 0) == 1
                    && (local?.revision != revision || local?.origin != origin || local?.thumbnailAssetPath == nil)
                var fullRecord = record
                if needsFile || needsThumbnail {
                    fullRecord = try await database.record(for: record.recordID)
                    try check(session)
                }
                if !item.deleted, let asset = fullRecord["file"] as? CKAsset, let source = asset.fileURL {
                    item.assetPath = try await stage(source, session: session)
                }
                if !item.deleted, let asset = fullRecord["thumbnail"] as? CKAsset, let source = asset.fileURL {
                    item.thumbnailAssetPath = try await stage(source, session: session)
                }
                session.records[id] = fullRecord
                items.append(item)
                guard items.count <= 100_000 else { throw SyncError.invalidRequest }
            }
            guard let cursor = result.queryCursor else { return items }
            result = try await database.records(continuingMatchFrom: cursor, desiredKeys: keys, resultsLimit: 100)
            try check(session)
        }
    }

    private func save(_ items: [Item], session: Session) async throws {
        try check(session)
        let records = try items.map { item -> CKRecord in
            guard item.id.utf8.count <= 1024, item.payload.utf8.count <= 1_048_576,
                  item.revision >= 0 else { throw SyncError.invalidRequest }
            let recordName = SHA256.hash(data: Data(item.id.utf8)).map { String(format: "%02x", $0) }.joined()
            let record = session.records[item.id] ?? CKRecord(recordType: "OrbinItem",
                recordID: CKRecord.ID(recordName: recordName, zoneID: zone))
            Field.itemID.write(item.id, to: record)
            record["revision"] = item.revision as CKRecordValue
            Field.origin.write(item.origin, to: record)
            Field.payload.write(item.payload, to: record)
            record["deleted"] = (item.deleted ? 1 : 0) as CKRecordValue
            if item.deleted {
                record["file"] = nil; record["hasFile"] = 0 as CKRecordValue
                record["thumbnail"] = nil; record["hasThumbnail"] = 0 as CKRecordValue
            } else if let path = item.assetPath {
                record["file"] = try checkedAsset(path)
                record["hasFile"] = 1 as CKRecordValue
                record["thumbnail"] = try item.thumbnailAssetPath.map { try checkedAsset($0) }
                record["hasThumbnail"] = (item.thumbnailAssetPath == nil ? 0 : 1) as CKRecordValue
            }
            return record
        }
        try check(session)
        let result = try await database.modifyRecords(saving: records, deleting: [],
            savePolicy: .ifServerRecordUnchanged, atomically: true)
        try check(session)
        for (id, saved) in result.saveResults {
            let record = try saved.get()
            guard record.recordID == id else { throw SyncError.invalidRequest }
            if let key = Field.itemID.read(record) { session.records[key] = record }
        }
    }

    private func cancelRequest(_ rawID: String) {
        guard let uuid = UUID(uuidString: rawID) else { return }
        let id = uuid.uuidString
        if let entry = requests[id] {
            entry.task.cancel()
            closeSession(entry.sessionID, excluding: id)
        } else {
            cancelledRequests.insert(id)
        }
    }

    private func closeSession(_ id: String?, excluding requestID: String) {
        guard let id else { return }
        closedSessions.insert(id)
        for (key, entry) in requests where key != requestID && entry.sessionID == id { entry.task.cancel() }
        guard let session = sessions.removeValue(forKey: id) else { return }
        session.closed = true
        releaseFiles(session)
        session.records.removeAll()
    }

    private func releaseFiles(_ session: Session) {
        for file in session.staged { try? FileManager.default.removeItem(at: file) }
        if let directory = session.directory { try? FileManager.default.removeItem(at: directory) }
        session.staged.removeAll()
        session.directory = nil
    }

    private func checkedAsset(_ path: String) throws -> CKAsset {
        let file = URL(fileURLWithPath: path).resolvingSymlinksInPath().standardizedFileURL
        let support = try FileManager.default.url(for: .applicationSupportDirectory,
            in: .userDomainMask, appropriateFor: nil, create: true)
            .appendingPathComponent("Orbin", isDirectory: true).resolvingSymlinksInPath().path
        let roots = [support + "/ManagedMedia/", support + "/MediaExport/"]
        let values = try file.resourceValues(forKeys: [.isRegularFileKey, .fileSizeKey])
        guard roots.contains(where: { file.path.hasPrefix($0) }), values.isRegularFile == true,
              let size = values.fileSize, size <= 1_073_741_824 else { throw SyncError.invalidRequest }
        return CKAsset(fileURL: file)
    }

    private func stage(_ source: URL, session: Session) async throws -> String {
        try check(session)
        let values = try source.resourceValues(forKeys: [.fileSizeKey, .isRegularFileKey])
        guard values.isRegularFile == true, let size = values.fileSize, size <= 1_073_741_824 else { throw SyncError.invalidRequest }
        let directory = try FileManager.default.url(for: .applicationSupportDirectory,
            in: .userDomainMask, appropriateFor: nil, create: true)
            .appendingPathComponent("CloudInbox", isDirectory: true).appendingPathComponent(session.id, isDirectory: true)
        session.directory = directory
        let destination = directory.appendingPathComponent(UUID().uuidString)
        // Register ownership before copying; a lock can clean this lease while I/O unwinds.
        session.staged.insert(destination)
        let copy = Task.detached(priority: .utility) {
            try Self.copyAsset(source, destination: destination, directory: directory)
        }
        try await withTaskCancellationHandler(operation: {
            try await copy.value
        }, onCancel: {
            copy.cancel()
        })
        try check(session)
        return destination.path
    }

    /// Large assets must not block the main actor and prevent the user's lock/cancel from running.
    nonisolated private static func copyAsset(_ source: URL, destination: URL, directory: URL) throws {
        var complete = false
        defer {
            if !complete {
                try? FileManager.default.removeItem(at: destination)
                if Task.isCancelled { try? FileManager.default.removeItem(at: directory) }
            }
        }
        try Task.checkCancellation()
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true,
            attributes: [.protectionKey: FileProtectionType.completeUntilFirstUserAuthentication])
        var protectedDirectory = directory
        var protection = URLResourceValues(); protection.isExcludedFromBackup = true
        try protectedDirectory.setResourceValues(protection)
        try Task.checkCancellation()
        try FileManager.default.copyItem(at: source, to: destination)
        try Task.checkCancellation()
        try FileManager.default.setAttributes([.protectionKey: FileProtectionType.completeUntilFirstUserAuthentication],
                                             ofItemAtPath: destination.path)
        complete = true
    }

    /// The fields that say what the reader follows, saves and reads.
    ///
    /// They are stored in `encryptedValues`, which CloudKit encrypts on the device before upload
    /// (end to end when the account has Advanced Data Protection). An ordinary record field is
    /// only encrypted at rest with keys Apple holds. The record name is already a hash of the id.
    ///
    /// CloudKit fixes a field's type the first time it is saved, so the encrypted copies need
    /// their own names. A record written before this change is still read from its plain field;
    /// the next save moves it across and clears the plain copy.
    struct Field {
        let encrypted: String
        let legacy: String

        static let itemID = Field(encrypted: "secureItemID", legacy: "itemID")
        static let origin = Field(encrypted: "secureOrigin", legacy: "origin")
        static let payload = Field(encrypted: "securePayload", legacy: "payload")
        static let sensitive = [itemID, origin, payload]

        func read(_ record: CKRecord) -> String? {
            (record.encryptedValues[encrypted] as? String) ?? (record[legacy] as? String)
        }

        func write(_ value: String, to record: CKRecord) {
            record.encryptedValues[encrypted] = value as NSString
            record[legacy] = nil
        }
    }

    private enum SyncError: Error { case invalidRequest }
    private static func encode(_ reply: Reply) -> String {
        String(decoding: (try? JSONEncoder().encode(reply)) ?? Data("{}".utf8), as: UTF8.self)
    }
    private static func cancelledReply() -> Reply {
        Reply(available: false, detail: "iCloud request cancelled.", error: "iCloud request cancelled.")
    }
    private static func describe(_ error: Error) -> String {
        guard let cloud = error as? CKError else { return "iCloud sync failed. Try again." }
        switch cloud.code {
        case .notAuthenticated: return "Sign in to iCloud in iOS Settings."
        case .quotaExceeded: return "Your iCloud storage is full. Local data is kept."
        case .networkUnavailable, .networkFailure: return "iCloud is offline. Try again when connected."
        case .serverRecordChanged, .batchRequestFailed: return "Another device changed your data. Tap Sync now to merge again."
        case .permissionFailure, .missingEntitlement: return "This build is not provisioned for Orbin iCloud sync."
        case .serviceUnavailable, .requestRateLimited, .zoneBusy: return "iCloud is busy. Try again shortly."
        default: return "iCloud sync is incomplete. Local data is kept; try again."
        }
    }
}

/// Validate the executable's entitlement blob AND its provisioning profile before using CloudKit.
/// An unsigned/sideloaded app fails closed, including the simulator unless genuinely entitled.
enum OrbinCloudEntitlements {
    static func allows(container: String) -> Bool {
        guard let executable = Bundle.main.executableURL,
              let bytes = try? Data(contentsOf: executable, options: .mappedIfSafe),
              let signed = signatureEntitlements(bytes), permits(signed, container) else { return false }
        guard let profile = Bundle.main.url(forResource: "embedded", withExtension: "mobileprovision") else { return true }
        guard let data = try? Data(contentsOf: profile),
              let text = String(data: data, encoding: .isoLatin1),
              let start = text.range(of: "<?xml"), let end = text.range(of: "</plist>"),
              let plist = dictionary(Data(text[start.lowerBound..<end.upperBound].utf8)),
              let entitlements = plist["Entitlements"] as? [String: Any] else { return false }
        return permits(entitlements, container)
    }

    private static func permits(_ values: [String: Any], _ container: String) -> Bool {
        let services = values["com.apple.developer.icloud-services"] as? [String] ?? []
        let containers = values["com.apple.developer.icloud-container-identifiers"] as? [String] ?? []
        return services.contains("CloudKit") && containers.contains(container)
    }

    private static func dictionary(_ data: Data) -> [String: Any]? {
        (try? PropertyListSerialization.propertyList(from: data, format: nil)) as? [String: Any]
    }

    static func signatureEntitlements(_ data: Data) -> [String: Any]? {
        func integer(_ offset: Int, _ bigEndian: Bool = false) -> Int? {
            guard offset >= 0, offset <= data.count - 4 else { return nil }
            let bytes = Array(data[offset..<(offset + 4)])
            let ordered = bigEndian ? bytes : Array(bytes.reversed())
            return ordered.reduce(0) { ($0 << 8) | Int($1) }
        }
        // iOS device executables and our arm64-only simulator framework use a thin 64-bit Mach-O.
        guard integer(0) == 0xfeedfacf, let count = integer(16), count <= 1024 else { return nil }
        var position = 32
        for _ in 0..<count {
            guard let command = integer(position), let size = integer(position + 4),
                  size >= 8, position <= data.count - size else { return nil }
            defer { position += size }
            guard command == 0x1d else { continue }
            guard let offset = integer(position + 8), let length = integer(position + 12),
                  length >= 12, offset <= data.count - length,
                  integer(offset, true) == 0xfade0cc0,
                  let entries = integer(offset + 8, true), entries <= 128 else { return nil }
            for entry in 0..<entries {
                guard let relative = integer(offset + 16 + entry * 8, true), relative <= length - 8 else { return nil }
                let start = offset + relative
                guard integer(start, true) == 0xfade7171,
                      let blobLength = integer(start + 4, true), blobLength >= 8,
                      blobLength <= length - relative else { continue }
                return dictionary(data.subdata(in: (start + 8)..<(start + blobLength)))
            }
            return nil
        }
        return nil
    }
}
