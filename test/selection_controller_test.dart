import 'package:flutter_test/flutter_test.dart';
import 'package:my_story/models/media_item.dart';
import 'package:my_story/state/selection_controller.dart';

import 'fakes.dart';

void main() {
  late SelectionController selection;
  late List<MediaItem> items;

  setUp(() {
    selection = SelectionController();
    items = [for (final id in ['a', 'b', 'c', 'd']) fakeMedia(id)];
  });

  List<String> ids() => selection.selected.map((i) => i.id).toList();

  test('toggle adds in tap order and numbers positions from one', () {
    selection
      ..toggle(items[2])
      ..toggle(items[0]);
    expect(ids(), ['c', 'a']);
    expect(selection.positionOf(items[2]), 1);
    expect(selection.positionOf(items[0]), 2);
    expect(selection.positionOf(items[1]), isNull);
  });

  test('toggle twice removes the item', () {
    selection
      ..toggle(items[0])
      ..toggle(items[0]);
    expect(selection.isEmpty, isTrue);
  });

  test('reorder uses the final index after removal', () {
    items.forEach(selection.toggle);
    selection.reorder(0, 2);
    expect(ids(), ['b', 'c', 'a', 'd']);
    selection.reorder(3, 0);
    expect(ids(), ['d', 'b', 'c', 'a']);
  });

  test('reorder ignores invalid or no-op moves', () {
    items.forEach(selection.toggle);
    selection
      ..reorder(5, 0)
      ..reorder(1, 1);
    expect(ids(), ['a', 'b', 'c', 'd']);
  });

  test('total duration adds three seconds per photo and the clip length per video', () {
    selection
      ..toggle(fakeMedia('p'))
      ..toggle(fakeMedia('v', kind: MediaKind.video, duration: const Duration(seconds: 10)));
    expect(selection.totalDuration, const Duration(seconds: 13));
  });

  test('notifies listeners only on real changes', () {
    var notifications = 0;
    selection.addListener(() => notifications++);
    selection.clear();
    selection.remove(items[0]);
    expect(notifications, 0);
    selection.toggle(items[0]);
    selection.clear();
    expect(notifications, 2);
  });
}
