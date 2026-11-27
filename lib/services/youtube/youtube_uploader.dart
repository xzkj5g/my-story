import 'dart:convert';
import 'dart:io';

import 'package:http/http.dart' as http;

enum YouTubePrivacy {
  private('private', 'Private'),
  unlisted('unlisted', 'Unlisted'),
  public('public', 'Public');

  const YouTubePrivacy(this.wire, this.label);

  final String wire;
  final String label;
}

class YouTubeUploadResult {
  const YouTubeUploadResult(this.videoId);

  final String videoId;

  Uri get watchUrl => Uri.https('youtu.be', '/$videoId');
}

class YouTubeUploadException implements Exception {
  const YouTubeUploadException(this.message, {this.statusCode});

  final String message;
  final int? statusCode;

  /// The access token was rejected and a fresh one should be requested.
  bool get isUnauthorized => statusCode == 401;

  @override
  String toString() => message;
}

/// Uploads a video with the YouTube Data API v3 resumable upload protocol.
///
/// See https://developers.google.com/youtube/v3/guides/using_resumable_upload_protocol.
class YouTubeUploader {
  YouTubeUploader({
    http.Client? client,
    this.chunkSize = defaultChunkSize,
    this.maxRetries = 3,
  })  : assert(chunkSize % _chunkGranularity == 0, 'chunkSize must be a multiple of 256 KiB'),
        _client = client ?? http.Client();

  static const int _chunkGranularity = 256 * 1024;
  static const int defaultChunkSize = _chunkGranularity * 20;

  static final Uri _initiateUri = Uri.parse(
    'https://www.googleapis.com/upload/youtube/v3/videos'
    '?uploadType=resumable&part=snippet,status',
  );

  final http.Client _client;
  final int chunkSize;
  final int maxRetries;

  Future<YouTubeUploadResult> upload({
    required File file,
    required String accessToken,
    required String title,
    String description = '',
    YouTubePrivacy privacy = YouTubePrivacy.private,
    void Function(double progress)? onProgress,
  }) async {
    final length = await file.length();
    if (length == 0) {
      throw const YouTubeUploadException('The video file is empty.');
    }
    final sessionUri = await _initiate(
      accessToken: accessToken,
      length: length,
      title: title,
      description: description,
      privacy: privacy,
    );

    final handle = await file.open();
    try {
      var offset = 0;
      var failures = 0;
      onProgress?.call(0);
      while (true) {
        final end = (offset + chunkSize < length ? offset + chunkSize : length) - 1;
        await handle.setPosition(offset);
        final bytes = await handle.read(end - offset + 1);
        http.Response response;
        try {
          response = await _client.put(
            sessionUri,
            headers: {
              'Authorization': 'Bearer $accessToken',
              'Content-Type': 'video/mp4',
              'Content-Range': 'bytes $offset-$end/$length',
            },
            body: bytes,
          );
        } on SocketException catch (e) {
          failures++;
          if (failures > maxRetries) {
            throw YouTubeUploadException('Network error during upload: ${e.message}');
          }
          offset = await _resumeOffset(sessionUri, accessToken, length, offset);
          continue;
        } on http.ClientException catch (e) {
          failures++;
          if (failures > maxRetries) {
            throw YouTubeUploadException('Network error during upload: ${e.message}');
          }
          offset = await _resumeOffset(sessionUri, accessToken, length, offset);
          continue;
        }

        if (response.statusCode == 200 || response.statusCode == 201) {
          onProgress?.call(1);
          return YouTubeUploadResult(_parseVideoId(response.body));
        }
        if (response.statusCode == 308) {
          failures = 0;
          offset = _nextOffset(response.headers['range']);
          onProgress?.call(offset / length);
          continue;
        }
        if (response.statusCode >= 500) {
          failures++;
          if (failures > maxRetries) throw _errorFrom(response);
          offset = await _resumeOffset(sessionUri, accessToken, length, offset);
          continue;
        }
        throw _errorFrom(response);
      }
    } finally {
      await handle.close();
    }
  }

  Future<Uri> _initiate({
    required String accessToken,
    required int length,
    required String title,
    required String description,
    required YouTubePrivacy privacy,
  }) async {
    final response = await _client.post(
      _initiateUri,
      headers: {
        'Authorization': 'Bearer $accessToken',
        'Content-Type': 'application/json; charset=UTF-8',
        'X-Upload-Content-Length': '$length',
        'X-Upload-Content-Type': 'video/mp4',
      },
      body: jsonEncode({
        'snippet': {
          'title': title.length > 100 ? title.substring(0, 100) : title,
          'description': description,
          'categoryId': '22',
        },
        'status': {
          'privacyStatus': privacy.wire,
          'selfDeclaredMadeForKids': false,
        },
      }),
    );
    if (response.statusCode != 200) throw _errorFrom(response);
    final location = response.headers['location'];
    if (location == null) {
      throw const YouTubeUploadException('YouTube did not return an upload session.');
    }
    return Uri.parse(location);
  }

  Future<int> _resumeOffset(Uri sessionUri, String accessToken, int length, int current) async {
    try {
      final response = await _client.put(
        sessionUri,
        headers: {
          'Authorization': 'Bearer $accessToken',
          'Content-Range': 'bytes */$length',
          'Content-Length': '0',
        },
      );
      if (response.statusCode == 308) {
        return _nextOffset(response.headers['range']);
      }
    } on SocketException {
      return current;
    } on http.ClientException {
      return current;
    }
    return current;
  }

  /// `Range: bytes=0-42` means 43 bytes were stored; no header means nothing was stored.
  static int _nextOffset(String? rangeHeader) {
    if (rangeHeader == null) return 0;
    final match = RegExp(r'bytes=\d+-(\d+)').firstMatch(rangeHeader);
    if (match == null) return 0;
    return int.parse(match.group(1)!) + 1;
  }

  static String _parseVideoId(String body) {
    try {
      final json = jsonDecode(body) as Map<String, Object?>;
      final id = json['id'];
      if (id is String && id.isNotEmpty) return id;
    } on FormatException {
      // Falls through to the error below.
    }
    throw const YouTubeUploadException('YouTube accepted the upload but returned no video id.');
  }

  static YouTubeUploadException _errorFrom(http.Response response) {
    var message = 'YouTube rejected the upload (HTTP ${response.statusCode}).';
    try {
      final json = jsonDecode(response.body) as Map<String, Object?>;
      final error = json['error'];
      if (error is Map && error['message'] is String) {
        message = error['message']! as String;
      }
    } on FormatException {
      // Keep the generic message.
    }
    return YouTubeUploadException(message, statusCode: response.statusCode);
  }
}
