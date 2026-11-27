import 'package:material_ui/material_ui.dart';

enum MediaKind { photo, video }

/// A photo or video from the device library, independent of the plugin that loaded it.
class MediaItem {
  const MediaItem({
    required this.id,
    required this.kind,
    required this.createdAt,
    required this.thumbnailBuilder,
    this.duration = Duration.zero,
    this.width = 0,
    this.height = 0,
  });

  /// Platform asset id: `MediaStore` row id on Android, `PHAsset` local identifier on iOS.
  final String id;
  final MediaKind kind;
  final DateTime createdAt;
  final Duration duration;
  final int width;
  final int height;
  final ImageProvider Function(int pixelSize) thumbnailBuilder;

  bool get isVideo => kind == MediaKind.video;

  /// How long this item contributes to a story. Photos hold for [photoDuration].
  Duration storyDuration({Duration photoDuration = photoDisplayDuration}) =>
      isVideo ? duration : photoDuration;

  ImageProvider thumbnail([int pixelSize = 400]) => thumbnailBuilder(pixelSize);

  Map<String, Object> toWire() => {'id': id, 'isVideo': isVideo};

  @override
  bool operator ==(Object other) => other is MediaItem && other.id == id;

  @override
  int get hashCode => id.hashCode;

  /// Every photo is shown for this long, matching the native compile engines.
  static const Duration photoDisplayDuration = Duration(seconds: 3);
}
