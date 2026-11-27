#!/usr/bin/env bash
# Writes the Google OAuth client ids (public identifiers, not secrets) that the app needs for YouTube
# sign-in. Missing ids are written as empty values, which leaves YouTube sign-in disabled in the build.
set -euo pipefail

server_id="${GOOGLE_SERVER_CLIENT_ID:-}"
ios_id="${GOOGLE_IOS_CLIENT_ID:-}"

mkdir -p config
python3 - "$server_id" "$ios_id" <<'PY'
import json
import sys

server_id, ios_id = sys.argv[1:3]
with open("config/oauth.json", "w", encoding="utf-8") as handle:
    json.dump({"GOOGLE_SERVER_CLIENT_ID": server_id, "GOOGLE_IOS_CLIENT_ID": ios_id}, handle)
PY

if [ -n "$ios_id" ]; then
  reversed="com.googleusercontent.apps.${ios_id%.apps.googleusercontent.com}"
  {
    echo "GOOGLE_IOS_CLIENT_ID = $ios_id"
    echo "GOOGLE_REVERSED_CLIENT_ID = $reversed"
  } > ios/Flutter/OAuth.xcconfig
fi
