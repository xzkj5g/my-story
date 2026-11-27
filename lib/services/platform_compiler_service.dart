import 'package:flutter/services.dart';

import '../models/media_item.dart';
import '../models/output_settings.dart';
import 'compiler_service.dart';

/// Talks to the Android (Media3) and iOS (AVFoundation) compile engines over platform channels.
class PlatformCompilerService implements CompilerService {
  PlatformCompilerService({
    MethodChannel? methodChannel,
    EventChannel? eventChannel,
  })  : _method = methodChannel ?? const MethodChannel('my_story/compiler'),
        _events = eventChannel ?? const EventChannel('my_story/compiler_events');

  final MethodChannel _method;
  final EventChannel _events;

  @override
  Stream<CompileUpdate> get updates => _events
      .receiveBroadcastStream()
      .where((event) => event is Map)
      .map((event) => CompileUpdate.fromWire(event as Map<Object?, Object?>));

  @override
  Future<void> start(List<MediaItem> items, OutputSettings settings) async {
    try {
      await _method.invokeMethod<void>('startCompile', {
        'items': [for (final item in items) item.toWire()],
        ...settings.toWire(),
      });
    } on PlatformException catch (e) {
      throw CompileRequestException(e.message ?? 'Could not start creating your story.');
    }
  }

  @override
  Future<void> cancel() => _method.invokeMethod<void>('cancelCompile');

  @override
  Future<String?> resourceWarning() async {
    try {
      return await _method.invokeMethod<String>('resourceWarning');
    } on MissingPluginException {
      return null;
    }
  }
}
