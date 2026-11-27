import 'package:google_sign_in/google_sign_in.dart';

import '../../config/app_config.dart';

/// The signed-in Google account that uploads go to.
class YouTubeAccount {
  const YouTubeAccount({required this.email, this.displayName});

  final String email;
  final String? displayName;
}

class YouTubeAuthException implements Exception {
  const YouTubeAuthException(this.message);

  final String message;

  @override
  String toString() => message;
}

/// Connects the app to a YouTube account and hands out upload access tokens.
abstract class YouTubeAuth {
  /// Whether this build ships OAuth client ids; when false [connect] cannot work.
  bool get isConfigured;

  Future<YouTubeAccount?> restore();

  Future<YouTubeAccount> connect();

  Future<String> accessToken();

  Future<void> disconnect();
}

/// [YouTubeAuth] implemented with the system Google sign-in sheet (no passwords in the app).
class GoogleYouTubeAuth implements YouTubeAuth {
  static const String uploadScope = 'https://www.googleapis.com/auth/youtube.upload';

  bool _initialized = false;
  GoogleSignInAccount? _account;

  @override
  bool get isConfigured => AppConfig.isYouTubeConfigured;

  Future<void> _ensureInitialized() async {
    if (_initialized) return;
    await GoogleSignIn.instance.initialize(
      clientId: AppConfig.googleIosClientId.isEmpty ? null : AppConfig.googleIosClientId,
      serverClientId:
          AppConfig.googleServerClientId.isEmpty ? null : AppConfig.googleServerClientId,
    );
    _initialized = true;
  }

  YouTubeAccount _toAccount(GoogleSignInAccount account) =>
      YouTubeAccount(email: account.email, displayName: account.displayName);

  @override
  Future<YouTubeAccount?> restore() async {
    if (!isConfigured) return null;
    try {
      await _ensureInitialized();
      final account = await GoogleSignIn.instance.attemptLightweightAuthentication();
      _account = account;
      return account == null ? null : _toAccount(account);
    } on Object {
      return null;
    }
  }

  @override
  Future<YouTubeAccount> connect() async {
    if (!isConfigured) {
      throw const YouTubeAuthException(
        'YouTube is not configured in this build. See docs/youtube-setup.md.',
      );
    }
    try {
      await _ensureInitialized();
      final account = await GoogleSignIn.instance.authenticate(scopeHint: const [uploadScope]);
      await account.authorizationClient.authorizeScopes(const [uploadScope]);
      _account = account;
      return _toAccount(account);
    } on GoogleSignInException catch (e) {
      if (e.code == GoogleSignInExceptionCode.canceled) {
        throw const YouTubeAuthException('Sign-in was cancelled.');
      }
      throw YouTubeAuthException(e.description ?? 'Could not sign in to YouTube.');
    }
  }

  @override
  Future<String> accessToken() async {
    final account = _account;
    if (account == null) {
      throw const YouTubeAuthException('Connect your YouTube account first.');
    }
    try {
      final client = account.authorizationClient;
      final authorization = await client.authorizationForScopes(const [uploadScope]) ??
          await client.authorizeScopes(const [uploadScope]);
      return authorization.accessToken;
    } on GoogleSignInException catch (e) {
      throw YouTubeAuthException(e.description ?? 'YouTube access was not granted.');
    }
  }

  @override
  Future<void> disconnect() async {
    _account = null;
    if (!_initialized) return;
    try {
      await GoogleSignIn.instance.disconnect();
    } on Object {
      await GoogleSignIn.instance.signOut();
    }
  }
}
