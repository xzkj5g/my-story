#!/usr/bin/env python3
"""Contract tests for release signing and public verification metadata."""

from pathlib import Path
import unittest

WORKFLOW = Path(".github/workflows/android-release.yml")
SECRETS = (
    "ANDROID_KEYSTORE_BASE64",
    "ANDROID_KEYSTORE_PASSWORD",
    "ANDROID_KEY_ALIAS",
    "ANDROID_KEY_PASSWORD",
)


class ReleaseSigningTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.content = WORKFLOW.read_text(encoding="utf-8")

    def test_protected_secrets_and_temporary_storage(self) -> None:
        for secret in SECRETS:
            self.assertIn(f"secrets.{secret}", self.content)
        self.assertIn("$RUNNER_TEMP/android-release.keystore", self.content)
        self.assertIn("Remove temporary signing material", self.content)
        self.assertIn("if: ${{ always() }}", self.content)

    def test_signature_and_integrity_metadata(self) -> None:
        self.assertIn("apksigner", self.content)
        self.assertIn("verify --verbose --print-certs", self.content)
        self.assertIn("sha256sum", self.content)
        self.assertIn("certificate.txt", self.content)
        self.assertIn("app-release.apk.sha256", self.content)


if __name__ == "__main__":
    unittest.main()
