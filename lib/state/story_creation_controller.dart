import 'dart:async';
import 'dart:io';

import 'package:flutter/foundation.dart';

import '../models/media_item.dart';
import '../models/output_settings.dart';
import '../models/story.dart';
import '../services/compiler_service.dart';
import '../services/gallery_service.dart';
import '../services/story_repository.dart';

enum CreationPhase { idle, running, succeeded, failed, cancelled }

/// Runs one story creation job: starts the native compiler, tracks progress, and saves the
/// finished video into the gallery and the app's library.
class StoryCreationController extends ChangeNotifier {
  StoryCreationController({
    required this._compiler,
    required this._gallery,
    required this._repository,
    this.onStoryCreated,
    DateTime Function()? clock,
  }) : _clock = clock ?? DateTime.now;

  final CompilerService _compiler;
  final GalleryService _gallery;
  final StoryRepository _repository;
  final DateTime Function() _clock;
  final VoidCallback? onStoryCreated;

  CreationPhase _phase = CreationPhase.idle;
  int _progress = 0;
  String? _error;
  String? _warning;
  Story? _story;
  StreamSubscription<CompileUpdate>? _subscription;

  CreationPhase get phase => _phase;
  int get progress => _progress;
  String? get errorMessage => _error;
  String? get warning => _warning;
  Story? get createdStory => _story;
  bool get isRunning => _phase == CreationPhase.running;

  Future<void> start({
    required String title,
    required List<MediaItem> items,
    required OutputSettings settings,
  }) async {
    if (isRunning || items.isEmpty) return;
    _phase = CreationPhase.running;
    _progress = 0;
    _error = null;
    _story = null;
    notifyListeners();

    _warning = await _compiler.resourceWarning();
    await _subscription?.cancel();
    _subscription = _compiler.updates.listen(
      (update) => unawaited(_onUpdate(update, title, items, settings)),
      onError: (Object e) => _fail('Creating your story failed unexpectedly.'),
    );
    try {
      await _compiler.start(items, settings);
    } on CompileRequestException catch (e) {
      await _fail(e.message);
    } on Object {
      await _fail('Could not start creating your story.');
    }
  }

  Future<void> cancel() async {
    if (!isRunning) return;
    await _compiler.cancel();
  }

  void reset() {
    if (isRunning) return;
    _phase = CreationPhase.idle;
    _progress = 0;
    _error = null;
    _story = null;
    notifyListeners();
  }

  Future<void> _onUpdate(
    CompileUpdate update,
    String title,
    List<MediaItem> items,
    OutputSettings settings,
  ) async {
    if (!isRunning) return;
    switch (update.status) {
      case CompileStatus.running:
        if (update.progress > _progress) {
          _progress = update.progress;
          notifyListeners();
        }
      case CompileStatus.failed:
        await _fail(update.error ?? 'Creating your story failed.');
      case CompileStatus.cancelled:
        await _subscription?.cancel();
        _phase = CreationPhase.cancelled;
        notifyListeners();
      case CompileStatus.succeeded:
        await _finish(update, title, items, settings);
    }
  }

  Future<void> _finish(
    CompileUpdate update,
    String title,
    List<MediaItem> items,
    OutputSettings settings,
  ) async {
    await _subscription?.cancel();
    _subscription = null;
    try {
      final MediaItem saved;
      if (update.assetId != null) {
        final found = await _gallery.findById(update.assetId!);
        if (found == null) {
          throw StateError('Saved video ${update.assetId} is missing from the library.');
        }
        saved = found;
      } else if (update.filePath != null) {
        final file = File(update.filePath!);
        saved = await _gallery.saveVideo(file, title);
        if (await file.exists()) await file.delete();
      } else {
        throw StateError('The compiler finished without an output.');
      }
      final story = Story(
        id: saved.id,
        title: title,
        createdAt: _clock(),
        itemCount: items.length,
        duration: items.fold(Duration.zero, (sum, i) => sum + i.storyDuration()),
        settings: settings,
      );
      await _repository.add(story);
      _story = story;
      _progress = 100;
      _phase = CreationPhase.succeeded;
      notifyListeners();
      onStoryCreated?.call();
    } on Object {
      await _fail('Your story was created but could not be saved to your library.');
    }
  }

  Future<void> _fail(String message) async {
    await _subscription?.cancel();
    _subscription = null;
    _error = message;
    _phase = CreationPhase.failed;
    notifyListeners();
  }

  @override
  void dispose() {
    unawaited(_subscription?.cancel());
    super.dispose();
  }
}
