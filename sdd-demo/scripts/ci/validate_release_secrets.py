#!/usr/bin/env python3
"""Validate release-secret names and reject tracked signing material."""

from __future__ import annotations

import subprocess
import sys
from pathlib import Path

REQUIRED_SECRETS = (
    "ANDROID_KEYSTORE_BASE64",
    "ANDROID_KEYSTORE_PASSWORD",
    "ANDROID_KEY_ALIAS",
    "ANDROID_KEY_PASSWORD",
)
KEYSTORE_SUFFIXES = (".jks", ".keystore", ".p12")


def main() -> int:
    workflow = Path(".github/workflows/android-release.yml")
    if not workflow.exists():
        print(f"Missing {workflow}", file=sys.stderr)
        return 1
    content = workflow.read_text(encoding="utf-8")
    errors = [
        f"{workflow}: missing protected secret reference {secret}"
        for secret in REQUIRED_SECRETS
        if f"secrets.{secret}" not in content
    ]
    tracked = subprocess.run(
        ["git", "ls-files", "-z"],
        check=True,
        capture_output=True,
        text=False,
    ).stdout.decode().split("\0")
    tracked_keys = [path for path in tracked if path.lower().endswith(KEYSTORE_SUFFIXES)]
    if tracked_keys:
        errors.append(f"tracked signing material found: {', '.join(tracked_keys)}")
    if errors:
        print("\n".join(errors), file=sys.stderr)
        return 1
    print("Release secret references and tracked-key safety checks passed.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
