# YouTube upload setup

My Story uploads with the YouTube Data API v3 resumable upload endpoint and the
`https://www.googleapis.com/auth/youtube.upload` scope. Users sign in with Google's standard
in-app flow (Credential Manager on Android, the system web authentication session on iOS), so you
only need OAuth client ids from one Google Cloud project. Client ids are identifiers, not secrets.

## 1. Google Cloud project

1. Create a project in the Google Cloud console and enable **YouTube Data API v3**.
2. Configure the **OAuth consent screen**. While the app is in *Testing*, add every account that will
   upload as a test user. To let anyone upload, publish the app and complete Google's verification for
   the sensitive `youtube.upload` scope.
3. Add the scope `.../auth/youtube.upload` to the consent screen.

Uploads from unverified API projects are private-only on YouTube until the project passes an API
compliance audit; the app defaults to **Private** and lets the user choose Unlisted or Public.

## 2. OAuth clients

Create these under *APIs & Services > Credentials*:

| Client type | Used for | Settings |
|---|---|---|
| Web application | `GOOGLE_SERVER_CLIENT_ID` (required on Android) | no redirect URI needed |
| Android | identifies the app to Google | package `com.mystory.app` and the SHA-1 of every signing key (debug, release and Play app signing) |
| iOS | `GOOGLE_IOS_CLIENT_ID` | bundle id `com.mystory.app` |

## 3. Provide the ids

Local runs: create `config/oauth.json` (git-ignored):

```json
{
  "GOOGLE_SERVER_CLIENT_ID": "1234-web.apps.googleusercontent.com",
  "GOOGLE_IOS_CLIENT_ID": "1234-ios.apps.googleusercontent.com"
}
```

```sh
flutter run --dart-define-from-file=config/oauth.json
```

For iOS also create `ios/Flutter/OAuth.xcconfig` (git-ignored) so Xcode can fill `GIDClientID` and the
sign-in callback URL scheme:

```text
GOOGLE_IOS_CLIENT_ID = 1234-ios.apps.googleusercontent.com
GOOGLE_REVERSED_CLIENT_ID = com.googleusercontent.apps.1234-ios
```

CI: define the repository (or `release` environment) **variables** `GOOGLE_SERVER_CLIENT_ID` and
`GOOGLE_IOS_CLIENT_ID`. `scripts/ci/configure_google_sign_in.sh` writes both files above before building.
Without them the build still works and Settings explains that YouTube sign-in is not configured.
