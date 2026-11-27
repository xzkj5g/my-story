import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:my_story/models/media_item.dart';
import 'package:my_story/models/output_settings.dart';
import 'package:my_story/services/compiler_service.dart';
import 'package:my_story/state/story_creation_controller.dart';

import 'fakes.dart';

void main() {
  late FakeCompilerService compiler;
  late FakeGalleryService gallery;
  late MemoryStoryRepository repository;
  late StoryCreationController controller;
  late int created;
  final items = [
    fakeMedia('1'),
    fakeMedia('2', kind: MediaKind.video, duration: const Duration(seconds: 5)),
  ];

  setUp(() {
    compiler = FakeCompilerService();
    gallery = FakeGalleryService();
    repository = MemoryStoryRepository();
    created = 0;
    controller = StoryCreationController(
      compiler: compiler,
      gallery: gallery,
      repository: repository,
      onStoryCreated: () => created++,
      clock: () => DateTime(2026, 11, 26, 9),
    );
  });

  tearDown(() => controller.dispose());

  Future<void> start() => controller.start(
        title: 'Trip',
        items: items,
        settings: const OutputSettings(frameRate: StoryFrameRate.fps60),
      );

  Future<void> pump() => Future<void>.delayed(Duration.zero);

  Future<void> settled() async {
    for (var i = 0; i < 100 && controller.isRunning; i++) {
      await Future<void>.delayed(const Duration(milliseconds: 10));
    }
  }

  test('hands the items and settings to the compiler and tracks progress', () async {
    await start();
    expect(controller.phase, CreationPhase.running);
    expect(compiler.startedItems, items);
    expect(compiler.startedSettings!.frameRate, StoryFrameRate.fps60);

    compiler.emit(const CompileUpdate(status: CompileStatus.running, progress: 30));
    await pump();
    compiler.emit(const CompileUpdate(status: CompileStatus.running, progress: 10));
    await pump();
    expect(controller.progress, 30);
  });

  test('records the story when the engine saved the video itself (Android)', () async {
    gallery.items.add(fakeMedia('88', kind: MediaKind.video));
    await start();
    compiler.emit(const CompileUpdate(status: CompileStatus.succeeded, assetId: '88'));
    await pump();
    await pump();

    expect(controller.phase, CreationPhase.succeeded);
    expect(created, 1);
    final story = (await repository.load()).single;
    expect(story.id, '88');
    expect(story.title, 'Trip');
    expect(story.itemCount, 2);
    expect(story.duration, const Duration(seconds: 8));
    expect(story.createdAt, DateTime(2026, 11, 26, 9));
  });

  test('saves the temporary file into the gallery and removes it (iOS)', () async {
    final temp = File('${Directory.systemTemp.path}/story-${DateTime.now().microsecondsSinceEpoch}.mp4')
      ..writeAsBytesSync([0, 1, 2]);
    await start();
    compiler.emit(CompileUpdate(status: CompileStatus.succeeded, filePath: temp.path));
    await settled();

    expect(controller.phase, CreationPhase.succeeded);
    expect(gallery.savedFiles.single.path, temp.path);
    expect(temp.existsSync(), isFalse);
    expect((await repository.load()).single.id, 'saved-1');
  });

  test('fails when the compiler reports an error', () async {
    await start();
    compiler.emit(const CompileUpdate(status: CompileStatus.failed, error: 'Disk full'));
    await pump();
    expect(controller.phase, CreationPhase.failed);
    expect(controller.errorMessage, 'Disk full');
    expect(created, 0);
  });

  test('fails when the request is rejected up front', () async {
    compiler.rejectWith = const CompileRequestException('A selected file is missing.');
    await start();
    expect(controller.phase, CreationPhase.failed);
    expect(controller.errorMessage, 'A selected file is missing.');
  });

  test('fails when the finished video is missing from the library', () async {
    await start();
    compiler.emit(const CompileUpdate(status: CompileStatus.succeeded, assetId: 'gone'));
    await pump();
    await pump();
    expect(controller.phase, CreationPhase.failed);
    expect(await repository.load(), isEmpty);
  });

  test('cancel stops the job and reset returns to idle', () async {
    await start();
    await controller.cancel();
    await pump();
    expect(compiler.cancelled, isTrue);
    expect(controller.phase, CreationPhase.cancelled);
    controller.reset();
    expect(controller.phase, CreationPhase.idle);
  });

  test('ignores a second start while running and an empty selection', () async {
    await controller.start(title: 'x', items: const [], settings: const OutputSettings());
    expect(controller.phase, CreationPhase.idle);
    await start();
    compiler.startedItems = null;
    await start();
    expect(compiler.startedItems, isNull);
  });
}
