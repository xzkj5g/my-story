import 'package:flutter/services.dart';
import 'package:material_ui/material_ui.dart';

import 'app.dart';
import 'services/photo_manager_gallery_service.dart';
import 'services/platform_compiler_service.dart';
import 'services/story_repository.dart';
import 'services/youtube/youtube_auth.dart';
import 'services/youtube/youtube_service.dart';
import 'services/youtube/youtube_uploader.dart';
import 'state/app_services.dart';

void main() {
  WidgetsFlutterBinding.ensureInitialized();
  SystemChrome.setSystemUIOverlayStyle(
    const SystemUiOverlayStyle(
      statusBarColor: Colors.transparent,
      statusBarIconBrightness: Brightness.dark,
      statusBarBrightness: Brightness.light,
    ),
  );
  runApp(
    MyStoryApp(
      services: AppServices(
        gallery: PhotoManagerGalleryService(),
        compiler: PlatformCompilerService(),
        repository: SharedPreferencesStoryRepository(),
        youtube: YouTubeService(auth: GoogleYouTubeAuth(), uploader: YouTubeUploader()),
      ),
    ),
  );
}
