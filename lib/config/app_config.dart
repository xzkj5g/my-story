/// Build-time configuration injected with `--dart-define`.
///
/// OAuth client ids are created in Google Cloud Console; see `docs/youtube-setup.md`.
abstract final class AppConfig {
  static const String googleServerClientId =
      String.fromEnvironment('GOOGLE_SERVER_CLIENT_ID');
  static const String googleIosClientId =
      String.fromEnvironment('GOOGLE_IOS_CLIENT_ID');

  static bool get isYouTubeConfigured =>
      googleServerClientId.isNotEmpty || googleIosClientId.isNotEmpty;
}
