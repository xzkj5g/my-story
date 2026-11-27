import 'package:flutter/foundation.dart';

import '../models/media_item.dart';
import '../services/gallery_service.dart';

/// Loads the device's photos and videos for the Home tab, a page at a time.
class GalleryController extends ChangeNotifier {
  GalleryController(this._gallery, {this.pageSize = 60});

  final GalleryService _gallery;
  final int pageSize;

  GalleryAccess? _access;
  final List<MediaItem> _items = [];
  bool _loading = false;
  bool _hasMore = true;
  int _page = 0;
  Object? _error;

  GalleryAccess? get access => _access;
  List<MediaItem> get items => List.unmodifiable(_items);
  bool get isLoading => _loading;
  bool get hasMore => _hasMore;
  Object? get error => _error;
  bool get needsPermission => _access == GalleryAccess.denied;
  bool get isLimited => _access == GalleryAccess.limited;

  /// Asks for photo library access (if needed) and loads the first page.
  Future<void> initialize() async {
    _access = await _gallery.requestAccess();
    notifyListeners();
    if (_access != GalleryAccess.denied) {
      await refresh();
    }
  }

  Future<void> refresh() async {
    _items.clear();
    _page = 0;
    _hasMore = true;
    _error = null;
    notifyListeners();
    await loadMore();
  }

  Future<void> loadMore() async {
    if (_loading || !_hasMore || _access == GalleryAccess.denied) return;
    _loading = true;
    _error = null;
    notifyListeners();
    try {
      final page = await _gallery.loadPage(_page, pageSize);
      _items.addAll(page);
      _page++;
      _hasMore = page.length >= pageSize;
    } on Object catch (e) {
      _error = e;
    } finally {
      _loading = false;
      notifyListeners();
    }
  }

  Future<void> openSettings() => _gallery.openSystemSettings();

  Future<void> manageLimitedAccess() async {
    await _gallery.manageLimitedAccess();
    await refresh();
  }
}
