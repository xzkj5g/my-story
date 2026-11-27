import Foundation
import CoreGraphics

/// One photo or video picked in Flutter, identified by its `PHAsset.localIdentifier`.
struct StoryItemRequest: Equatable {
    let id: String
    let isVideo: Bool
}

enum StoryCompilerError: LocalizedError {
    case invalidRequest(String)
    case assetUnavailable(String)
    case compositionFailed(String)
    case exportFailed(String)

    var errorDescription: String? {
        switch self {
        case .invalidRequest(let message),
             .assetUnavailable(let message),
             .compositionFailed(let message),
             .exportFailed(let message):
            return message
        }
    }
}

/// Output format chosen in the app: aspect ratio, resolution tier and frame rate.
struct StorySettings: Equatable {
    let width: Int
    let height: Int
    let fps: Int

    var renderSize: CGSize { CGSize(width: width, height: height) }

    /// Mirrors the Android `OutputSettings` presets so both platforms produce the same sizes.
    init(aspectRatio: String, resolution: String, fps: Int) throws {
        let longEdge: Int
        switch resolution {
        case "720p": longEdge = 1280
        case "1080p": longEdge = 1920
        case "2k": longEdge = 2560
        case "4k": longEdge = 3840
        default: throw StoryCompilerError.invalidRequest("Unsupported resolution: \(resolution)")
        }
        let shortEdge = longEdge * 9 / 16
        switch aspectRatio {
        case "16:9":
            width = longEdge
            height = shortEdge
        case "9:16":
            width = shortEdge
            height = longEdge
        case "1:1":
            width = shortEdge
            height = shortEdge
        default:
            throw StoryCompilerError.invalidRequest("Unsupported aspect ratio: \(aspectRatio)")
        }
        guard [24, 30, 60].contains(fps) else {
            throw StoryCompilerError.invalidRequest("Unsupported frame rate: \(fps)")
        }
        self.fps = fps
    }

    static func parseItems(_ raw: [[String: Any]]) throws -> [StoryItemRequest] {
        try raw.map { entry in
            guard let id = entry["id"] as? String, !id.isEmpty else {
                throw StoryCompilerError.invalidRequest("Invalid asset id.")
            }
            guard let isVideo = entry["isVideo"] as? Bool else {
                throw StoryCompilerError.invalidRequest("Missing media type for \(id).")
            }
            return StoryItemRequest(id: id, isVideo: isVideo)
        }
    }
}

/// Seconds each photo stays on screen; matches the Android engine and the Flutter UI.
let storyPhotoDurationSeconds: Double = 3
