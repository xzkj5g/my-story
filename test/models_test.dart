import 'package:flutter_test/flutter_test.dart';
import 'package:my_story/models/media_item.dart';
import 'package:my_story/models/output_settings.dart';
import 'package:my_story/models/story.dart';
import 'package:my_story/services/compiler_service.dart';

import 'fakes.dart';

void main() {
  group('OutputSettings', () {
    test('defaults to 16:9 at 30 fps and 1080p', () {
      expect(const OutputSettings().toWire(), {
        'aspectRatio': '16:9',
        'fps': 30,
        'resolution': '1080p',
      });
    });

    test('copyWith keeps the untouched fields', () {
      final settings = const OutputSettings().copyWith(aspectRatio: StoryAspectRatio.portrait);
      expect(settings.aspectRatio, StoryAspectRatio.portrait);
      expect(settings.frameRate, StoryFrameRate.fps30);
    });
  });

  group('Story', () {
    test('survives a JSON round trip', () {
      final story = Story(
        id: '42',
        title: 'Summer',
        createdAt: DateTime.fromMillisecondsSinceEpoch(1700000000000),
        itemCount: 5,
        duration: const Duration(seconds: 17),
        settings: const OutputSettings(
          aspectRatio: StoryAspectRatio.square,
          frameRate: StoryFrameRate.fps60,
        ),
      );
      final decoded = Story.decodeList(Story.encodeList([story])).single;
      expect(decoded.id, '42');
      expect(decoded.title, 'Summer');
      expect(decoded.createdAt, story.createdAt);
      expect(decoded.itemCount, 5);
      expect(decoded.duration, const Duration(seconds: 17));
      expect(decoded.settings, story.settings);
    });

    test('falls back to defaults for unknown settings', () {
      final story = Story.fromJson({
        'id': '1',
        'title': 't',
        'createdAt': 0,
        'itemCount': 1,
        'durationMs': 1000,
        'aspectRatio': '3:2',
        'fps': 12,
      });
      expect(story.settings, const OutputSettings());
    });
  });

  group('MediaItem', () {
    test('photos last three seconds and videos keep their own length', () {
      expect(fakeMedia('p').storyDuration(), const Duration(seconds: 3));
      expect(
        fakeMedia('v', kind: MediaKind.video, duration: const Duration(seconds: 8)).storyDuration(),
        const Duration(seconds: 8),
      );
    });

    test('serialises to the native wire format', () {
      expect(fakeMedia('7', kind: MediaKind.video).toWire(), {'id': '7', 'isVideo': true});
    });
  });

  group('CompileUpdate', () {
    test('parses progress, asset ids and file paths', () {
      final running = CompileUpdate.fromWire({'status': 'running', 'progress': 41.6});
      expect(running.status, CompileStatus.running);
      expect(running.progress, 42);
      expect(running.isTerminal, isFalse);

      final android = CompileUpdate.fromWire({'status': 'succeeded', 'assetId': '99'});
      expect(android.assetId, '99');
      expect(android.isTerminal, isTrue);

      final ios = CompileUpdate.fromWire({'status': 'succeeded', 'filePath': '/tmp/a.mp4'});
      expect(ios.filePath, '/tmp/a.mp4');
    });

    test('treats unknown statuses as failures and clamps progress', () {
      expect(CompileUpdate.fromWire({'status': 'boom'}).status, CompileStatus.failed);
      expect(CompileUpdate.fromWire({'status': 'running', 'progress': 400}).progress, 100);
    });
  });
}
