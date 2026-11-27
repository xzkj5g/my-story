import 'package:flutter_test/flutter_test.dart';
import 'package:my_story/models/media_item.dart';
import 'package:my_story/models/output_settings.dart';
import 'package:my_story/models/story.dart';
import 'package:my_story/state/library_controller.dart';

import 'fakes.dart';

Story story(String id, DateTime createdAt) => Story(
      id: id,
      title: 'Story $id',
      createdAt: createdAt,
      itemCount: 2,
      duration: const Duration(seconds: 6),
      settings: const OutputSettings(),
    );

void main() {
  late FakeGalleryService gallery;
  late MemoryStoryRepository repository;
  late LibraryController library;

  setUp(() {
    gallery = FakeGalleryService(items: [
      fakeMedia('1', kind: MediaKind.video),
      fakeMedia('2', kind: MediaKind.video),
    ]);
    repository = MemoryStoryRepository()
      ..stories.addAll([
        story('1', DateTime(2026, 1, 1)),
        story('2', DateTime(2026, 2, 1)),
        story('3', DateTime(2026, 3, 1)),
      ]);
    library = LibraryController(repository: repository, gallery: gallery);
  });

  test('lists newest first and forgets stories whose video left the gallery', () async {
    await library.refresh();
    expect(library.hasLoaded, isTrue);
    expect(library.entries.map((e) => e.story.id), ['2', '1']);
    expect(repository.stories.map((s) => s.id), isNot(contains('3')));
  });

  test('delete removes the video and the story', () async {
    await library.refresh();
    final outcome = await library.delete(library.entries.first);
    expect(outcome, DeleteOutcome.deleted);
    expect(library.entries.map((e) => e.story.id), ['1']);
    expect(gallery.items.map((i) => i.id), ['1']);
    expect(repository.stories.map((s) => s.id), ['1']);
  });

  test('keeps the story when the user declines the system delete prompt', () async {
    gallery.declineDeletes.add('2');
    await library.refresh();
    final outcome = await library.delete(library.entries.first);
    expect(outcome, DeleteOutcome.declined);
    expect(library.entries, hasLength(2));
    expect(repository.stories.map((s) => s.id), contains('2'));
  });
}
