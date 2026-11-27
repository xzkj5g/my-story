import 'dart:io';

import 'package:flutter/foundation.dart';

import '../services/youtube/youtube_auth.dart';
import '../services/youtube/youtube_service.dart';
import '../services/youtube/youtube_uploader.dart';

enum UploadPhase { idle, uploading, done, failed }

/// The YouTube connection (Settings) and the upload in progress (story viewer).
class YouTubeController extends ChangeNotifier {
  YouTubeController(this._service);

  final YouTubeService _service;

  YouTubeAccount? _account;
  bool _busy = false;
  String? _error;

  UploadPhase _uploadPhase = UploadPhase.idle;
  double _uploadProgress = 0;
  Uri? _uploadedUrl;
  String? _uploadError;

  bool get isConfigured => _service.auth.isConfigured;
  YouTubeAccount? get account => _account;
  bool get isConnected => _account != null;
  bool get isBusy => _busy;
  String? get error => _error;

  UploadPhase get uploadPhase => _uploadPhase;
  double get uploadProgress => _uploadProgress;
  Uri? get uploadedUrl => _uploadedUrl;
  String? get uploadError => _uploadError;

  Future<void> restore() async {
    _account = await _service.auth.restore();
    notifyListeners();
  }

  Future<void> connect() async {
    if (_busy) return;
    _busy = true;
    _error = null;
    notifyListeners();
    try {
      _account = await _service.auth.connect();
    } on YouTubeAuthException catch (e) {
      _error = e.message;
    } on Object {
      _error = 'Could not connect to YouTube. Please try again.';
    } finally {
      _busy = false;
      notifyListeners();
    }
  }

  Future<void> disconnect() async {
    if (_busy) return;
    _busy = true;
    notifyListeners();
    try {
      await _service.auth.disconnect();
      _account = null;
      _error = null;
    } finally {
      _busy = false;
      notifyListeners();
    }
  }

  Future<void> upload({
    required File file,
    required String title,
    required YouTubePrivacy privacy,
  }) async {
    if (_uploadPhase == UploadPhase.uploading) return;
    _uploadPhase = UploadPhase.uploading;
    _uploadProgress = 0;
    _uploadedUrl = null;
    _uploadError = null;
    notifyListeners();
    try {
      final result = await _service.upload(
        file: file,
        title: title,
        privacy: privacy,
        description: 'Created with My Story',
        onProgress: (progress) {
          _uploadProgress = progress;
          notifyListeners();
        },
      );
      _uploadedUrl = result.watchUrl;
      _uploadPhase = UploadPhase.done;
    } on YouTubeUploadException catch (e) {
      _uploadError = e.message;
      _uploadPhase = UploadPhase.failed;
    } on YouTubeAuthException catch (e) {
      _uploadError = e.message;
      _uploadPhase = UploadPhase.failed;
    } on Object {
      _uploadError = 'The upload failed. Check your connection and try again.';
      _uploadPhase = UploadPhase.failed;
    }
    notifyListeners();
  }

  void resetUpload() {
    if (_uploadPhase == UploadPhase.uploading) return;
    _uploadPhase = UploadPhase.idle;
    _uploadProgress = 0;
    _uploadedUrl = null;
    _uploadError = null;
    notifyListeners();
  }
}
