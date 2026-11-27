import AVFoundation
import Photos
import UIKit

/// Builds a story video from photo library assets with AVFoundation.
///
/// Reports progress through [onUpdate] using the same wire format as the Android engine:
/// `status` (running / succeeded / failed / cancelled), `progress`, `error`, `filePath`.
final class StoryCompiler {

    var onUpdate: (([String: Any]) -> Void)?

    private let workQueue = DispatchQueue(label: "app.mystory.compiler", qos: .userInitiated)
    private let lock = NSLock()
    private var exportSession: AVAssetExportSession?
    private var cancelRequested = false
    private var isRunning = false
    private var progressTimer: Timer?
    private var backgroundTask: UIBackgroundTaskIdentifier = .invalid
    private var temporaryFiles: [URL] = []

    func start(items: [StoryItemRequest], settings: StorySettings) throws {
        lock.lock()
        if isRunning {
            lock.unlock()
            throw StoryCompilerError.invalidRequest("A story is already being created.")
        }
        isRunning = true
        cancelRequested = false
        lock.unlock()

        let assets: [String: PHAsset]
        do {
            assets = try fetchAssets(for: items)
        } catch {
            lock.lock()
            isRunning = false
            lock.unlock()
            throw error
        }
        beginBackgroundTask()
        emit(status: "running", progress: 0)

        workQueue.async { [weak self] in
            guard let self else { return }
            do {
                let output = try self.compile(items: items, assets: assets, settings: settings)
                self.finish(status: "succeeded", progress: 100, filePath: output.path)
            } catch StoryCompilerCancelled.cancelled {
                self.finish(status: "cancelled", progress: 0)
            } catch {
                self.finish(status: "failed", progress: 0, error: error.localizedDescription)
            }
        }
    }

    func cancel() {
        lock.lock()
        cancelRequested = true
        let session = exportSession
        lock.unlock()
        session?.cancelExport()
    }

    // MARK: - Pipeline

    private func fetchAssets(for items: [StoryItemRequest]) throws -> [String: PHAsset] {
        let ids = items.map(\.id)
        let result = PHAsset.fetchAssets(withLocalIdentifiers: ids, options: nil)
        var assets: [String: PHAsset] = [:]
        result.enumerateObjects { asset, _, _ in assets[asset.localIdentifier] = asset }
        if let missing = ids.first(where: { assets[$0] == nil }) {
            throw StoryCompilerError.assetUnavailable("A selected item is no longer available (\(missing)).")
        }
        return assets
    }

    private func compile(
        items: [StoryItemRequest],
        assets: [String: PHAsset],
        settings: StorySettings
    ) throws -> URL {
        var segments: [StorySegment] = []
        for (index, item) in items.enumerated() {
            try throwIfCancelled()
            guard let asset = assets[item.id] else {
                throw StoryCompilerError.assetUnavailable("A selected item is no longer available.")
            }
            let segment = try item.isVideo
                ? loadVideo(asset)
                : loadPhoto(asset, settings: settings, index: index)
            segments.append(segment)
            emit(status: "running", progress: min(20, (index + 1) * 20 / items.count))
        }

        try throwIfCancelled()
        let built = try StoryComposition.build(segments: segments, settings: settings)
        let outputURL = FileManager.default.temporaryDirectory
            .appendingPathComponent("my-story-\(UUID().uuidString).mp4")
        try export(built, to: outputURL)
        return outputURL
    }

    private func loadVideo(_ asset: PHAsset) throws -> StorySegment {
        let options = PHVideoRequestOptions()
        options.isNetworkAccessAllowed = true
        options.deliveryMode = .highQualityFormat
        options.version = .current

        let semaphore = DispatchSemaphore(value: 0)
        var loaded: AVAsset?
        PHImageManager.default().requestAVAsset(forVideo: asset, options: options) { avAsset, _, _ in
            loaded = avAsset
            semaphore.signal()
        }
        semaphore.wait()
        guard let avAsset = loaded else {
            throw StoryCompilerError.assetUnavailable("A selected video could not be read.")
        }
        return StorySegment(asset: avAsset)
    }

    private func loadPhoto(_ asset: PHAsset, settings: StorySettings, index: Int) throws -> StorySegment {
        let options = PHImageRequestOptions()
        options.isNetworkAccessAllowed = true
        options.deliveryMode = .highQualityFormat
        options.resizeMode = .exact
        options.isSynchronous = false

        let maxEdge = CGFloat(max(settings.width, settings.height))
        let semaphore = DispatchSemaphore(value: 0)
        var loaded: UIImage?
        PHImageManager.default().requestImage(
            for: asset,
            targetSize: CGSize(width: maxEdge, height: maxEdge),
            contentMode: .aspectFit,
            options: options
        ) { image, info in
            let degraded = (info?[PHImageResultIsDegradedKey] as? Bool) ?? false
            if degraded && image != nil { return }
            loaded = image
            semaphore.signal()
        }
        semaphore.wait()
        guard let image = loaded else {
            throw StoryCompilerError.assetUnavailable("A selected photo could not be read.")
        }

        let clipURL = FileManager.default.temporaryDirectory
            .appendingPathComponent("my-story-photo-\(index)-\(UUID().uuidString).mp4")
        temporaryFiles.append(clipURL)
        try StillImageVideoWriter.makeVideo(
            from: image,
            size: settings.renderSize,
            duration: storyPhotoDurationSeconds,
            outputURL: clipURL
        )
        return StorySegment(asset: AVURLAsset(url: clipURL))
    }

    private func export(_ built: StoryComposition.Result, to outputURL: URL) throws {
        guard let session = AVAssetExportSession(
            asset: built.composition,
            presetName: AVAssetExportPresetHighestQuality
        ) else {
            throw StoryCompilerError.exportFailed("This device cannot export the video.")
        }
        session.outputURL = outputURL
        session.outputFileType = .mp4
        session.videoComposition = built.videoComposition
        session.shouldOptimizeForNetworkUse = true

        lock.lock()
        exportSession = session
        let alreadyCancelled = cancelRequested
        lock.unlock()
        if alreadyCancelled { throw StoryCompilerCancelled.cancelled }

        startProgressTimer(for: session)
        let finished = DispatchSemaphore(value: 0)
        session.exportAsynchronously { finished.signal() }
        finished.wait()
        stopProgressTimer()

        switch session.status {
        case .completed:
            return
        case .cancelled:
            try? FileManager.default.removeItem(at: outputURL)
            throw StoryCompilerCancelled.cancelled
        default:
            try? FileManager.default.removeItem(at: outputURL)
            throw StoryCompilerError.exportFailed(
                session.error?.localizedDescription ?? "Exporting the video failed."
            )
        }
    }

    private func throwIfCancelled() throws {
        lock.lock()
        let cancelled = cancelRequested
        lock.unlock()
        if cancelled { throw StoryCompilerCancelled.cancelled }
    }

    // MARK: - Progress and lifecycle

    private func startProgressTimer(for session: AVAssetExportSession) {
        DispatchQueue.main.async { [weak self] in
            self?.progressTimer?.invalidate()
            self?.progressTimer = Timer.scheduledTimer(withTimeInterval: 0.25, repeats: true) { [weak self] _ in
                // Export covers 20-99%; loading the media accounts for the first 20%.
                let value = 20 + Int(Double(session.progress) * 79)
                self?.emit(status: "running", progress: min(99, value))
            }
        }
    }

    private func stopProgressTimer() {
        DispatchQueue.main.async { [weak self] in
            self?.progressTimer?.invalidate()
            self?.progressTimer = nil
        }
    }

    private func finish(status: String, progress: Int, error: String? = nil, filePath: String? = nil) {
        for file in temporaryFiles { try? FileManager.default.removeItem(at: file) }
        temporaryFiles.removeAll()
        lock.lock()
        exportSession = nil
        isRunning = false
        lock.unlock()
        stopProgressTimer()
        endBackgroundTask()
        emit(status: status, progress: progress, error: error, filePath: filePath)
    }

    private func emit(status: String, progress: Int, error: String? = nil, filePath: String? = nil) {
        var payload: [String: Any] = ["status": status, "progress": progress]
        if let error { payload["error"] = error }
        if let filePath { payload["filePath"] = filePath }
        DispatchQueue.main.async { [weak self] in self?.onUpdate?(payload) }
    }

    private func beginBackgroundTask() {
        DispatchQueue.main.async { [weak self] in
            guard let self else { return }
            self.backgroundTask = UIApplication.shared.beginBackgroundTask(withName: "StoryExport") { [weak self] in
                self?.cancel()
                self?.endBackgroundTask()
            }
        }
    }

    private func endBackgroundTask() {
        DispatchQueue.main.async { [weak self] in
            guard let self, self.backgroundTask != .invalid else { return }
            UIApplication.shared.endBackgroundTask(self.backgroundTask)
            self.backgroundTask = .invalid
        }
    }
}

private enum StoryCompilerCancelled: Error {
    case cancelled
}
