import 'package:flutter/foundation.dart';

import '../models/media_item.dart';
import '../models/story.dart';
import '../services/gallery_service.dart';
import '../services/story_repository.dart';

class LibraryEntry {
  const LibraryEntry(this.story, this.media);

  final Story story;
  final MediaItem media;
}

enum DeleteOutcome { deleted, declined, failed }

/// The stories created by this app that still exist in the device gallery.
class LibraryController extends ChangeNotifier {
  LibraryController({required this._repository, required this._gallery});

  final StoryRepository _repository;
  final GalleryService _gallery;

  List<LibraryEntry> _entries = const [];
  bool _loading = false;
  bool _loaded = false;

  List<LibraryEntry> get entries => _entries;
  bool get isLoading => _loading;
  bool get hasLoaded => _loaded;

  Future<void> refresh() async {
    if (_loading) return;
    _loading = true;
    notifyListeners();
    try {
      final stories = await _repository.load();
      final entries = <LibraryEntry>[];
      for (final story in stories) {
        final media = await _gallery.findById(story.id);
        if (media == null) {
          await _repository.remove(story.id);
        } else {
          entries.add(LibraryEntry(story, media));
        }
      }
      _entries = entries;
    } finally {
      _loading = false;
      _loaded = true;
      notifyListeners();
    }
  }

  Future<DeleteOutcome> delete(LibraryEntry entry) async {
    try {
      final deleted = await _gallery.deleteByIds([entry.story.id]);
      if (!deleted.contains(entry.story.id)) return DeleteOutcome.declined;
      await _repository.remove(entry.story.id);
      _entries = _entries.where((e) => e.story.id != entry.story.id).toList();
      notifyListeners();
      return DeleteOutcome.deleted;
    } on Object {
      return DeleteOutcome.failed;
    }
  }

  Future<String?> pathFor(LibraryEntry entry) async =>
      (await _gallery.fileFor(entry.story.id))?.path;
}
