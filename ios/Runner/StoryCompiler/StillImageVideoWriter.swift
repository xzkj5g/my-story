import AVFoundation
import UIKit

/// Turns a photo into a short silent H.264 clip sized exactly like the output video, so the
/// composition can treat photos and videos the same way.
enum StillImageVideoWriter {

    static func makeVideo(
        from image: UIImage,
        size: CGSize,
        duration: Double,
        outputURL: URL
    ) throws {
        try? FileManager.default.removeItem(at: outputURL)
        let writer = try AVAssetWriter(outputURL: outputURL, fileType: .mp4)
        let input = AVAssetWriterInput(
            mediaType: .video,
            outputSettings: [
                AVVideoCodecKey: AVVideoCodecType.h264,
                AVVideoWidthKey: Int(size.width),
                AVVideoHeightKey: Int(size.height),
            ]
        )
        input.expectsMediaDataInRealTime = false
        let adaptor = AVAssetWriterInputPixelBufferAdaptor(
            assetWriterInput: input,
            sourcePixelBufferAttributes: [
                kCVPixelBufferPixelFormatTypeKey as String: kCVPixelFormatType_32ARGB,
                kCVPixelBufferWidthKey as String: Int(size.width),
                kCVPixelBufferHeightKey as String: Int(size.height),
            ]
        )
        guard writer.canAdd(input) else {
            throw StoryCompilerError.compositionFailed("Could not prepare a photo for the video.")
        }
        writer.add(input)
        guard writer.startWriting() else {
            throw StoryCompilerError.compositionFailed(
                writer.error?.localizedDescription ?? "Could not start writing a photo clip."
            )
        }
        writer.startSession(atSourceTime: .zero)

        let buffer = try renderBuffer(image: image, size: size)
        // A frame every half second keeps the clip's duration exact without storing every frame.
        let step = 0.5
        var time = 0.0
        while time < duration {
            while !input.isReadyForMoreMediaData {
                Thread.sleep(forTimeInterval: 0.005)
            }
            let presentation = CMTime(seconds: time, preferredTimescale: 600)
            if !adaptor.append(buffer, withPresentationTime: presentation) {
                throw StoryCompilerError.compositionFailed(
                    writer.error?.localizedDescription ?? "Could not write a photo frame."
                )
            }
            time += step
        }
        input.markAsFinished()
        writer.endSession(atSourceTime: CMTime(seconds: duration, preferredTimescale: 600))

        let finished = DispatchSemaphore(value: 0)
        writer.finishWriting { finished.signal() }
        finished.wait()
        if writer.status != .completed {
            throw StoryCompilerError.compositionFailed(
                writer.error?.localizedDescription ?? "Could not finish writing a photo clip."
            )
        }
    }

    /// Draws [image] centered on black, scaled to fit without cropping or stretching.
    static func renderBuffer(image: UIImage, size: CGSize) throws -> CVPixelBuffer {
        let width = Int(size.width)
        let height = Int(size.height)
        var pixelBuffer: CVPixelBuffer?
        let attributes: [String: Any] = [
            kCVPixelBufferCGImageCompatibilityKey as String: true,
            kCVPixelBufferCGBitmapContextCompatibilityKey as String: true,
        ]
        let status = CVPixelBufferCreate(
            kCFAllocatorDefault,
            width,
            height,
            kCVPixelFormatType_32ARGB,
            attributes as CFDictionary,
            &pixelBuffer
        )
        guard status == kCVReturnSuccess, let buffer = pixelBuffer else {
            throw StoryCompilerError.compositionFailed("Could not allocate a photo frame.")
        }
        CVPixelBufferLockBaseAddress(buffer, [])
        defer { CVPixelBufferUnlockBaseAddress(buffer, []) }

        guard let context = CGContext(
            data: CVPixelBufferGetBaseAddress(buffer),
            width: width,
            height: height,
            bitsPerComponent: 8,
            bytesPerRow: CVPixelBufferGetBytesPerRow(buffer),
            space: CGColorSpaceCreateDeviceRGB(),
            bitmapInfo: CGImageAlphaInfo.noneSkipFirst.rawValue
        ) else {
            throw StoryCompilerError.compositionFailed("Could not draw a photo frame.")
        }
        context.setFillColor(UIColor.black.cgColor)
        context.fill(CGRect(origin: .zero, size: size))

        let target = fittedRect(contentSize: image.size, canvas: size)
        context.translateBy(x: 0, y: CGFloat(height))
        context.scaleBy(x: 1, y: -1)
        UIGraphicsPushContext(context)
        image.draw(in: target)
        UIGraphicsPopContext()
        return buffer
    }

    static func fittedRect(contentSize: CGSize, canvas: CGSize) -> CGRect {
        guard contentSize.width > 0, contentSize.height > 0 else {
            return CGRect(origin: .zero, size: canvas)
        }
        let scale = min(canvas.width / contentSize.width, canvas.height / contentSize.height)
        let width = contentSize.width * scale
        let height = contentSize.height * scale
        return CGRect(
            x: (canvas.width - width) / 2,
            y: (canvas.height - height) / 2,
            width: width,
            height: height
        )
    }
}
