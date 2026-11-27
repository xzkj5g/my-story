import 'package:flutter/foundation.dart';

import '../models/media_item.dart';

/// The ordered list of photos and videos the user has picked for a story.
class SelectionController extends ChangeNotifier {
  final List<MediaItem> _selected = [];

  List<MediaItem> get selected => List.unmodifiable(_selected);
  int get count => _selected.length;
  bool get isEmpty => _selected.isEmpty;

  bool contains(MediaItem item) => _selected.contains(item);

  /// 1-based position in the story, or null when not selected.
  int? positionOf(MediaItem item) {
    final index = _selected.indexOf(item);
    return index < 0 ? null : index + 1;
  }

  void toggle(MediaItem item) {
    if (!_selected.remove(item)) {
      _selected.add(item);
    }
    notifyListeners();
  }

  void remove(MediaItem item) {
    if (_selected.remove(item)) notifyListeners();
  }

  /// Moves an item the way `ReorderableListView.onReorderItem` reports it: [newIndex] is the
  /// final position after the item has been removed from [oldIndex].
  void reorder(int oldIndex, int newIndex) {
    if (oldIndex < 0 || oldIndex >= _selected.length) return;
    final target = newIndex.clamp(0, _selected.length - 1);
    if (target == oldIndex) return;
    _selected.insert(target, _selected.removeAt(oldIndex));
    notifyListeners();
  }

  void clear() {
    if (_selected.isEmpty) return;
    _selected.clear();
    notifyListeners();
  }

  Duration get totalDuration =>
      _selected.fold(Duration.zero, (sum, item) => sum + item.storyDuration());
}
