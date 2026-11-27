import Flutter
import UIKit

/// Exposes [StoryCompiler] to Dart using the same channels as the Android bridge.
///
/// Methods (`my_story/compiler`): `startCompile`, `cancelCompile`, `resourceWarning`.
/// Events (`my_story/compiler_events`): one map per progress or terminal update.
final class StoryCompilerPlugin: NSObject, FlutterPlugin, FlutterStreamHandler {

    private let compiler = StoryCompiler()
    private var eventSink: FlutterEventSink?

    static func register(with registrar: FlutterPluginRegistrar) {
        let instance = StoryCompilerPlugin()
        let methods = FlutterMethodChannel(name: "my_story/compiler", binaryMessenger: registrar.messenger())
        registrar.addMethodCallDelegate(instance, channel: methods)
        let events = FlutterEventChannel(name: "my_story/compiler_events", binaryMessenger: registrar.messenger())
        events.setStreamHandler(instance)
        instance.compiler.onUpdate = { [weak instance] payload in
            instance?.eventSink?(payload)
        }
    }

    func handle(_ call: FlutterMethodCall, result: @escaping FlutterResult) {
        switch call.method {
        case "startCompile":
            startCompile(call, result: result)
        case "cancelCompile":
            compiler.cancel()
            result(nil)
        case "resourceWarning":
            result(StoryCompilerPlugin.resourceWarning())
        default:
            result(FlutterMethodNotImplemented)
        }
    }

    private func startCompile(_ call: FlutterMethodCall, result: @escaping FlutterResult) {
        do {
            guard let arguments = call.arguments as? [String: Any],
                  let rawItems = arguments["items"] as? [[String: Any]] else {
                throw StoryCompilerError.invalidRequest("Select at least one photo or video.")
            }
            let items = try StorySettings.parseItems(rawItems)
            guard !items.isEmpty else {
                throw StoryCompilerError.invalidRequest("Select at least one photo or video.")
            }
            let settings = try StorySettings(
                aspectRatio: arguments["aspectRatio"] as? String ?? "",
                resolution: arguments["resolution"] as? String ?? "1080p",
                fps: arguments["fps"] as? Int ?? 0
            )
            try compiler.start(items: items, settings: settings)
            result(nil)
        } catch {
            result(FlutterError(code: "invalid_request", message: error.localizedDescription, details: nil))
        }
    }

    func onListen(withArguments arguments: Any?, eventSink events: @escaping FlutterEventSink) -> FlutterError? {
        eventSink = events
        return nil
    }

    func onCancel(withArguments arguments: Any?) -> FlutterError? {
        eventSink = nil
        return nil
    }

    private static func resourceWarning() -> String? {
        let lowStorageBytes: Int64 = 512 * 1024 * 1024
        let url = URL(fileURLWithPath: NSTemporaryDirectory())
        guard let values = try? url.resourceValues(forKeys: [.volumeAvailableCapacityForImportantUsageKey]),
              let available = values.volumeAvailableCapacityForImportantUsage,
              available < lowStorageBytes else {
            return nil
        }
        return "Warning: storage is running low. Creating the story may fail."
    }
}
