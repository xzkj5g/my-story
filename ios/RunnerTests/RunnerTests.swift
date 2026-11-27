import AVFoundation
import UIKit
import XCTest

@testable import Runner

final class StorySettingsTests: XCTestCase {

  func testSizesMatchAndroidPresets() throws {
    let landscape = try StorySettings(aspectRatio: "16:9", resolution: "1080p", fps: 30)
    XCTAssertEqual(landscape.width, 1920)
    XCTAssertEqual(landscape.height, 1080)

    let portrait = try StorySettings(aspectRatio: "9:16", resolution: "1080p", fps: 60)
    XCTAssertEqual(portrait.width, 1080)
    XCTAssertEqual(portrait.height, 1920)

    let square = try StorySettings(aspectRatio: "1:1", resolution: "720p", fps: 24)
    XCTAssertEqual(square.width, 720)
    XCTAssertEqual(square.height, 720)
  }

  func testRejectsUnsupportedValues() {
    XCTAssertThrowsError(try StorySettings(aspectRatio: "4:3", resolution: "1080p", fps: 30))
    XCTAssertThrowsError(try StorySettings(aspectRatio: "16:9", resolution: "8k", fps: 30))
    XCTAssertThrowsError(try StorySettings(aspectRatio: "16:9", resolution: "1080p", fps: 25))
  }

  func testParsesItemsInOrder() throws {
    let items = try StorySettings.parseItems([
      ["id": "A", "isVideo": true],
      ["id": "B", "isVideo": false],
    ])
    XCTAssertEqual(items, [
      StoryItemRequest(id: "A", isVideo: true),
      StoryItemRequest(id: "B", isVideo: false),
    ])
  }
}

final class StoryCompositionTests: XCTestCase {

  func testLandscapeSourceIsLetterboxedIntoPortraitFrame() {
    let transform = StoryComposition.fitTransform(
      naturalSize: CGSize(width: 1920, height: 1080),
      preferredTransform: .identity,
      renderSize: CGSize(width: 1080, height: 1920)
    )
    let frame = CGRect(x: 0, y: 0, width: 1920, height: 1080).applying(transform)
    XCTAssertEqual(frame.width, 1080, accuracy: 0.5)
    XCTAssertEqual(frame.height, 607.5, accuracy: 0.5)
    XCTAssertEqual(frame.midX, 540, accuracy: 0.5)
    XCTAssertEqual(frame.midY, 960, accuracy: 0.5)
  }

  func testRotatedPortraitSourceFitsWithoutCropping() {
    // A portrait phone video is stored 1920x1080 with a 90 degree rotation.
    let rotate = CGAffineTransform(a: 0, b: 1, c: -1, d: 0, tx: 1080, ty: 0)
    let transform = StoryComposition.fitTransform(
      naturalSize: CGSize(width: 1920, height: 1080),
      preferredTransform: rotate,
      renderSize: CGSize(width: 1920, height: 1080)
    )
    let frame = CGRect(x: 0, y: 0, width: 1920, height: 1080).applying(transform)
    XCTAssertEqual(frame.height, 1080, accuracy: 0.5)
    XCTAssertEqual(frame.width, 607.5, accuracy: 0.5)
    XCTAssertEqual(frame.midX, 960, accuracy: 0.5)
    XCTAssertEqual(frame.minY, 0, accuracy: 0.5)
  }
}

final class StillImageVideoWriterTests: XCTestCase {

  private func makeImage(color: UIColor, size: CGSize) -> UIImage {
    UIGraphicsImageRenderer(size: size).image { context in
      color.setFill()
      context.fill(CGRect(origin: .zero, size: size))
    }
  }

  func testPhotoClipHasTheRequestedSizeAndDuration() throws {
    let url = FileManager.default.temporaryDirectory.appendingPathComponent("still-\(UUID().uuidString).mp4")
    defer { try? FileManager.default.removeItem(at: url) }
    try StillImageVideoWriter.makeVideo(
      from: makeImage(color: .red, size: CGSize(width: 400, height: 300)),
      size: CGSize(width: 640, height: 360),
      duration: 3,
      outputURL: url
    )
    let asset = AVURLAsset(url: url)
    XCTAssertEqual(asset.duration.seconds, 3, accuracy: 0.3)
    let track = try XCTUnwrap(asset.tracks(withMediaType: .video).first)
    XCTAssertEqual(track.naturalSize.width, 640, accuracy: 1)
    XCTAssertEqual(track.naturalSize.height, 360, accuracy: 1)
  }

  func testTwoPhotosExportToOneVideoWithTheChosenFrameRate() throws {
    let directory = FileManager.default.temporaryDirectory
    let first = directory.appendingPathComponent("a-\(UUID().uuidString).mp4")
    let second = directory.appendingPathComponent("b-\(UUID().uuidString).mp4")
    let output = directory.appendingPathComponent("out-\(UUID().uuidString).mp4")
    defer {
      [first, second, output].forEach { try? FileManager.default.removeItem(at: $0) }
    }
    let size = CGSize(width: 640, height: 360)
    try StillImageVideoWriter.makeVideo(from: makeImage(color: .blue, size: size), size: size, duration: 3, outputURL: first)
    try StillImageVideoWriter.makeVideo(from: makeImage(color: .green, size: size), size: size, duration: 3, outputURL: second)

    let settings = try StorySettings(aspectRatio: "16:9", resolution: "720p", fps: 30)
    let built = try StoryComposition.build(
      segments: [StorySegment(asset: AVURLAsset(url: first)), StorySegment(asset: AVURLAsset(url: second))],
      settings: settings
    )
    XCTAssertEqual(built.composition.duration.seconds, 6, accuracy: 0.5)
    XCTAssertEqual(built.videoComposition.frameDuration.seconds, 1.0 / 30.0, accuracy: 0.0001)

    let session = try XCTUnwrap(
      AVAssetExportSession(asset: built.composition, presetName: AVAssetExportPresetMediumQuality)
    )
    session.outputURL = output
    session.outputFileType = .mp4
    session.videoComposition = built.videoComposition
    let done = expectation(description: "export")
    session.exportAsynchronously { done.fulfill() }
    wait(for: [done], timeout: 120)
    if session.status != .completed {
      print("EXPORT FAILED: status=\(session.status.rawValue) error=\(String(describing: session.error))")
    }
    XCTAssertEqual(session.status, .completed, session.error?.localizedDescription ?? "")

    let exported = AVURLAsset(url: output)
    XCTAssertEqual(exported.duration.seconds, 6, accuracy: 0.6)
  }
}
