import 'dart:async';
import 'dart:convert';
import 'dart:io';

import 'package:flutter/painting.dart';
import 'package:my_story/models/media_item.dart';
import 'package:my_story/models/output_settings.dart';
import 'package:my_story/models/story.dart';
import 'package:my_story/services/compiler_service.dart';
import 'package:my_story/services/gallery_service.dart';
import 'package:my_story/services/story_repository.dart';
import 'package:my_story/services/youtube/youtube_auth.dart';

final _transparentPixel = base64Decode(
  'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNkYPhfDwAChwGA60e6kgAAAABJRU5ErkJggg==',
);

MediaItem fakeMedia(
  String id, {
  MediaKind kind = MediaKind.photo,
  Duration duration = Duration.zero,
  DateTime? createdAt,
}) {
  return MediaItem(
    id: id,
    kind: kind,
    createdAt: createdAt ?? DateTime(2026, 11, 26),
    duration: duration,
    thumbnailBuilder: (_) => MemoryImage(_transparentPixel),
  );
}

class FakeGalleryService implements GalleryService {
  FakeGalleryService({List<MediaItem>? items, this.access = GalleryAccess.granted})
      : items = items ?? [];

  final List<MediaItem> items;
  GalleryAccess access;
  final List<String> declineDeletes = [];
  final List<File> savedFiles = [];

  @override
  Future<GalleryAccess> requestAccess() async => access;

  @override
  Future<List<MediaItem>> loadPage(int page, int pageSize) async {
    final start = page * pageSize;
    if (start >= items.length) return const [];
    return items.skip(start).take(pageSize).toList();
  }

  @override
  Future<MediaItem?> findById(String id) async {
    for (final item in items) {
      if (item.id == id) return item;
    }
    return null;
  }

  @override
  Future<MediaItem> saveVideo(File file, String title) async {
    savedFiles.add(file);
    final saved = fakeMedia('saved-${savedFiles.length}', kind: MediaKind.video);
    items.add(saved);
    return saved;
  }

  @override
  Future<List<String>> deleteByIds(List<String> ids) async {
    final deleted = ids.where((id) => !declineDeletes.contains(id)).toList();
    items.removeWhere((item) => deleted.contains(item.id));
    return deleted;
  }

  @override
  Future<File?> fileFor(String id) async => null;

  @override
  Future<void> openSystemSettings() async {}

  @override
  Future<void> manageLimitedAccess() async {}
}

class FakeCompilerService implements CompilerService {
  final StreamController<CompileUpdate> _controller = StreamController<CompileUpdate>.broadcast();
  List<MediaItem>? startedItems;
  OutputSettings? startedSettings;
  bool cancelled = false;
  CompileRequestException? rejectWith;
  String? warning;

  @override
  Stream<CompileUpdate> get updates => _controller.stream;

  @override
  Future<void> start(List<MediaItem> items, OutputSettings settings) async {
    if (rejectWith != null) throw rejectWith!;
    startedItems = items;
    startedSettings = settings;
  }

  @override
  Future<void> cancel() async {
    cancelled = true;
    emit(const CompileUpdate(status: CompileStatus.cancelled));
  }

  @override
  Future<String?> resourceWarning() async => warning;

  void emit(CompileUpdate update) => _controller.add(update);
}

class MemoryStoryRepository implements StoryRepository {
  final List<Story> stories = [];

  @override
  Future<List<Story>> load() async =>
      [...stories]..sort((a, b) => b.createdAt.compareTo(a.createdAt));

  @override
  Future<void> add(Story story) async {
    stories
      ..removeWhere((s) => s.id == story.id)
      ..add(story);
  }

  @override
  Future<void> remove(String id) async => stories.removeWhere((s) => s.id == id);
}

class FakeYouTubeAuth implements YouTubeAuth {
  FakeYouTubeAuth({this.configured = true});

  final bool configured;
  YouTubeAccount? account;
  int tokenRequests = 0;

  @override
  bool get isConfigured => configured;

  @override
  Future<YouTubeAccount?> restore() async => account;

  @override
  Future<YouTubeAccount> connect() async =>
      account = const YouTubeAccount(email: 'me@example.com', displayName: 'Me');

  @override
  Future<String> accessToken() async => 'token-${++tokenRequests}';

  @override
  Future<void> disconnect() async => account = null;
}
