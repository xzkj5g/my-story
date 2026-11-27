import '../models/media_item.dart';
import '../models/output_settings.dart';

enum CompileStatus { running, succeeded, failed, cancelled }

/// A progress or terminal update from the native video compiler.
class CompileUpdate {
  const CompileUpdate({
    required this.status,
    this.progress = 0,
    this.error,
    this.assetId,
    this.filePath,
  });

  final CompileStatus status;

  /// 0-100.
  final int progress;
  final String? error;

  /// Android: the gallery asset the engine already saved into `Movies/MyStory`.
  final String? assetId;

  /// iOS: a temporary file that still has to be saved into the photo library.
  final String? filePath;

  factory CompileUpdate.fromWire(Map<Object?, Object?> map) {
    final status = switch (map['status']) {
      'running' => CompileStatus.running,
      'succeeded' => CompileStatus.succeeded,
      'cancelled' => CompileStatus.cancelled,
      _ => CompileStatus.failed,
    };
    return CompileUpdate(
      status: status,
      progress: (map['progress'] as num?)?.round().clamp(0, 100) ?? 0,
      error: map['error'] as String?,
      assetId: map['assetId'] as String?,
      filePath: map['filePath'] as String?,
    );
  }

  bool get isTerminal => status != CompileStatus.running;
}

/// Compiles an ordered list of photos and videos into a single video natively.
abstract class CompilerService {
  /// Updates for the job currently running (or about to start).
  Stream<CompileUpdate> get updates;

  /// Starts a job; throws [CompileRequestException] when the request is rejected up front.
  Future<void> start(List<MediaItem> items, OutputSettings settings);

  Future<void> cancel();

  /// A low storage / memory warning to show before compiling, if any.
  Future<String?> resourceWarning();
}

class CompileRequestException implements Exception {
  const CompileRequestException(this.message);

  final String message;

  @override
  String toString() => message;
}
