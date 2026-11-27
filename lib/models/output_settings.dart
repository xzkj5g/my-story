/// Video aspect ratio ("viewport ratio") choices offered to the user.
enum StoryAspectRatio {
  landscape('16:9', 'Landscape', 16, 9),
  portrait('9:16', 'Portrait', 9, 16),
  square('1:1', 'Square', 1, 1);

  const StoryAspectRatio(this.wire, this.label, this.width, this.height);

  final String wire;
  final String label;
  final int width;
  final int height;

  double get ratio => width / height;
}

/// Output frame rate choices offered to the user.
enum StoryFrameRate {
  fps24(24),
  fps30(30),
  fps60(60);

  const StoryFrameRate(this.fps);

  final int fps;

  String get label => '$fps fps';
}

/// The two settings a user picks before creating a story.
class OutputSettings {
  const OutputSettings({
    this.aspectRatio = StoryAspectRatio.landscape,
    this.frameRate = StoryFrameRate.fps30,
    this.resolution = '1080p',
  });

  final StoryAspectRatio aspectRatio;
  final StoryFrameRate frameRate;
  final String resolution;

  OutputSettings copyWith({
    StoryAspectRatio? aspectRatio,
    StoryFrameRate? frameRate,
  }) {
    return OutputSettings(
      aspectRatio: aspectRatio ?? this.aspectRatio,
      frameRate: frameRate ?? this.frameRate,
      resolution: resolution,
    );
  }

  Map<String, Object> toWire() => {
        'aspectRatio': aspectRatio.wire,
        'fps': frameRate.fps,
        'resolution': resolution,
      };

  @override
  bool operator ==(Object other) =>
      other is OutputSettings &&
      other.aspectRatio == aspectRatio &&
      other.frameRate == frameRate &&
      other.resolution == resolution;

  @override
  int get hashCode => Object.hash(aspectRatio, frameRate, resolution);
}
