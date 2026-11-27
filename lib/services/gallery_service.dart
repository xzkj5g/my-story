import 'dart:io';

import '../models/media_item.dart';

enum GalleryAccess { granted, limited, denied }

/// Reads the device photo library and stores finished stories back into it.
abstract class GalleryService {
  Future<GalleryAccess> requestAccess();

  /// Loads one page of photos and videos, newest first.
  Future<List<MediaItem>> loadPage(int page, int pageSize);

  Future<MediaItem?> findById(String id);

  /// Saves a finished video into the device library.
  Future<MediaItem> saveVideo(File file, String title);

  /// Returns the ids that were actually deleted (the user can decline the system prompt).
  Future<List<String>> deleteByIds(List<String> ids);

  Future<File?> fileFor(String id);

  Future<void> openSystemSettings();

  Future<void> manageLimitedAccess();
}
