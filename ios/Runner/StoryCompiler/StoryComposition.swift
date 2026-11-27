import AVFoundation
import UIKit

/// A media segment ready to be placed on the timeline.
struct StorySegment {
    let asset: AVAsset
}

/// Pure AVFoundation composition logic: places segments back to back, letterboxes every one into
/// the output frame and sets the output frame rate.
enum StoryComposition {

    struct Result {
        let composition: AVMutableComposition
        let videoComposition: AVMutableVideoComposition
    }

    static func build(segments: [StorySegment], settings: StorySettings) throws -> Result {
        guard !segments.isEmpty else {
            throw StoryCompilerError.compositionFailed("Select at least one photo or video.")
        }
        let composition = AVMutableComposition()
        guard let videoTrack = composition.addMutableTrack(
            withMediaType: .video,
            preferredTrackID: kCMPersistentTrackID_Invalid
        ) else {
            throw StoryCompilerError.compositionFailed("Could not create the video track.")
        }
        let audioTrack = composition.addMutableTrack(
            withMediaType: .audio,
            preferredTrackID: kCMPersistentTrackID_Invalid
        )

        var cursor = CMTime.zero
        var instructions: [AVMutableVideoCompositionInstruction] = []

        for segment in segments {
            guard let sourceVideo = segment.asset.tracks(withMediaType: .video).first else {
                throw StoryCompilerError.compositionFailed("A selected item has no video to use.")
            }
            let duration = sourceVideo.timeRange.duration
            guard duration.isValid, duration > .zero else {
                throw StoryCompilerError.compositionFailed("A selected item is empty.")
            }
            let range = CMTimeRange(start: sourceVideo.timeRange.start, duration: duration)
            try videoTrack.insertTimeRange(range, of: sourceVideo, at: cursor)

            // Clips without audio simply leave a gap in the audio track, which plays as silence.
            if let sourceAudio = segment.asset.tracks(withMediaType: .audio).first, let audioTrack {
                let audioDuration = min(duration, sourceAudio.timeRange.duration)
                if audioDuration > .zero {
                    let audioRange = CMTimeRange(start: sourceAudio.timeRange.start, duration: audioDuration)
                    try audioTrack.insertTimeRange(audioRange, of: sourceAudio, at: cursor)
                }
            }

            let layer = AVMutableVideoCompositionLayerInstruction(assetTrack: videoTrack)
            layer.setTransform(
                fitTransform(
                    naturalSize: sourceVideo.naturalSize,
                    preferredTransform: sourceVideo.preferredTransform,
                    renderSize: settings.renderSize
                ),
                at: cursor
            )
            let instruction = AVMutableVideoCompositionInstruction()
            instruction.timeRange = CMTimeRange(start: cursor, duration: duration)
            instruction.backgroundColor = UIColor.black.cgColor
            instruction.layerInstructions = [layer]
            instructions.append(instruction)

            cursor = cursor + duration
        }

        let videoComposition = AVMutableVideoComposition()
        videoComposition.renderSize = settings.renderSize
        videoComposition.frameDuration = CMTime(value: 1, timescale: CMTimeScale(settings.fps))
        videoComposition.instructions = instructions
        return Result(composition: composition, videoComposition: videoComposition)
    }

    /// Scales the (rotated) source to fit inside [renderSize] and centers it; never crops or stretches.
    static func fitTransform(
        naturalSize: CGSize,
        preferredTransform: CGAffineTransform,
        renderSize: CGSize
    ) -> CGAffineTransform {
        let displayRect = CGRect(origin: .zero, size: naturalSize).applying(preferredTransform)
        let displayWidth = abs(displayRect.width)
        let displayHeight = abs(displayRect.height)
        guard displayWidth > 0, displayHeight > 0 else { return preferredTransform }

        let scale = min(renderSize.width / displayWidth, renderSize.height / displayHeight)
        let translateX = (renderSize.width - displayWidth * scale) / 2
        let translateY = (renderSize.height - displayHeight * scale) / 2

        return preferredTransform
            .concatenating(CGAffineTransform(translationX: -displayRect.minX, y: -displayRect.minY))
            .concatenating(CGAffineTransform(scaleX: scale, y: scale))
            .concatenating(CGAffineTransform(translationX: translateX, y: translateY))
    }
}
