import 'dart:convert';

import 'output_settings.dart';

/// A finished story video saved to the device gallery by this app.
class Story {
  const Story({
    required this.id,
    required this.title,
    required this.createdAt,
    required this.itemCount,
    required this.duration,
    required this.settings,
  });

  /// Gallery asset id of the exported video.
  final String id;
  final String title;
  final DateTime createdAt;
  final int itemCount;
  final Duration duration;
  final OutputSettings settings;

  Map<String, Object> toJson() => {
        'id': id,
        'title': title,
        'createdAt': createdAt.millisecondsSinceEpoch,
        'itemCount': itemCount,
        'durationMs': duration.inMilliseconds,
        'aspectRatio': settings.aspectRatio.wire,
        'fps': settings.frameRate.fps,
      };

  factory Story.fromJson(Map<String, Object?> json) {
    return Story(
      id: json['id']! as String,
      title: json['title']! as String,
      createdAt: DateTime.fromMillisecondsSinceEpoch(json['createdAt']! as int),
      itemCount: json['itemCount']! as int,
      duration: Duration(milliseconds: json['durationMs']! as int),
      settings: OutputSettings(
        aspectRatio: StoryAspectRatio.values.firstWhere(
          (r) => r.wire == json['aspectRatio'],
          orElse: () => StoryAspectRatio.landscape,
        ),
        frameRate: StoryFrameRate.values.firstWhere(
          (f) => f.fps == json['fps'],
          orElse: () => StoryFrameRate.fps30,
        ),
      ),
    );
  }

  static String encodeList(List<Story> stories) =>
      jsonEncode(stories.map((s) => s.toJson()).toList());

  static List<Story> decodeList(String source) {
    final decoded = jsonDecode(source) as List<Object?>;
    return [
      for (final entry in decoded)
        Story.fromJson((entry! as Map).cast<String, Object?>()),
    ];
  }
}
