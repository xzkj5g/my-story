import '../services/compiler_service.dart';
import '../services/gallery_service.dart';
import '../services/story_repository.dart';
import '../services/youtube/youtube_service.dart';

/// The platform-facing dependencies of the app, replaceable in tests.
class AppServices {
  const AppServices({
    required this.gallery,
    required this.compiler,
    required this.repository,
    required this.youtube,
  });

  final GalleryService gallery;
  final CompilerService compiler;
  final StoryRepository repository;
  final YouTubeService youtube;
}
