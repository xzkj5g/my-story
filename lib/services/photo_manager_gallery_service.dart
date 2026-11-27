import 'dart:io';

import 'package:photo_manager/photo_manager.dart';
import 'package:photo_manager_image_provider/photo_manager_image_provider.dart';

import '../models/media_item.dart';
import 'gallery_service.dart';

/// [GalleryService] backed by the `photo_manager` plugin (MediaStore / PhotoKit).
class PhotoManagerGalleryService implements GalleryService {
  AssetPathEntity? _recent;

  final Map<String, AssetEntity> _entities = {};

  @override
  Future<GalleryAccess> requestAccess() async {
    final state = await PhotoManager.requestPermissionExtend();
    _recent = null;
    if (state == PermissionState.limited) return GalleryAccess.limited;
    return state.hasAccess ? GalleryAccess.granted : GalleryAccess.denied;
  }

  Future<AssetPathEntity?> _recentPath() async {
    if (_recent != null) return _recent;
    final paths = await PhotoManager.getAssetPathList(
      onlyAll: true,
      type: RequestType.common,
      filterOption: FilterOptionGroup(
        orders: [const OrderOption(type: OrderOptionType.createDate)],
      ),
    );
    _recent = paths.isEmpty ? null : paths.first;
    return _recent;
  }

  @override
  Future<List<MediaItem>> loadPage(int page, int pageSize) async {
    final path = await _recentPath();
    if (path == null) return const [];
    final assets = await path.getAssetListPaged(page: page, size: pageSize);
    return assets.map(_toItem).toList();
  }

  @override
  Future<MediaItem?> findById(String id) async {
    final entity = await AssetEntity.fromId(id);
    return entity == null ? null : _toItem(entity);
  }

  @override
  Future<MediaItem> saveVideo(File file, String title) async {
    final entity = await PhotoManager.editor.saveVideo(
      file,
      title: title.endsWith('.mp4') ? title : '$title.mp4',
      relativePath: 'Movies/MyStory',
    );
    return _toItem(entity);
  }

  @override
  Future<List<String>> deleteByIds(List<String> ids) =>
      PhotoManager.editor.deleteWithIds(ids);

  @override
  Future<File?> fileFor(String id) async {
    final entity = _entities[id] ?? await AssetEntity.fromId(id);
    return entity?.originFile;
  }

  @override
  Future<void> openSystemSettings() => PhotoManager.openSetting();

  @override
  Future<void> manageLimitedAccess() => PhotoManager.presentLimited();

  MediaItem _toItem(AssetEntity entity) {
    _entities[entity.id] = entity;
    return MediaItem(
      id: entity.id,
      kind: entity.type == AssetType.video ? MediaKind.video : MediaKind.photo,
      createdAt: entity.createDateTime,
      duration: Duration(seconds: entity.duration),
      width: entity.width,
      height: entity.height,
      thumbnailBuilder: (size) => AssetEntityImageProvider(
        entity,
        isOriginal: false,
        thumbnailSize: ThumbnailSize.square(size),
      ),
    );
  }
}
