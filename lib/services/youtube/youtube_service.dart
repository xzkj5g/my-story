import 'dart:io';

import 'youtube_auth.dart';
import 'youtube_uploader.dart';

/// Connects a YouTube account and uploads videos to it.
class YouTubeService {
  YouTubeService({required this.auth, required this._uploader});

  final YouTubeAuth auth;
  final YouTubeUploader _uploader;

  Future<YouTubeUploadResult> upload({
    required File file,
    required String title,
    String description = '',
    YouTubePrivacy privacy = YouTubePrivacy.private,
    void Function(double progress)? onProgress,
  }) async {
    var token = await auth.accessToken();
    for (var attempt = 0;; attempt++) {
      try {
        return await _uploader.upload(
          file: file,
          accessToken: token,
          title: title,
          description: description,
          privacy: privacy,
          onProgress: onProgress,
        );
      } on YouTubeUploadException catch (e) {
        if (!e.isUnauthorized || attempt > 0) rethrow;
        token = await auth.accessToken();
      }
    }
  }
}
