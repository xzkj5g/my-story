#!/usr/bin/env python3
"""Contract tests for CI commands and artifacts."""

from pathlib import Path
import unittest

WORKFLOW = Path(".github/workflows/ci.yml")


class CiCommandTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.content = WORKFLOW.read_text(encoding="utf-8")

    def test_required_commands(self) -> None:
        for command in (
            "flutter analyze",
            "flutter test",
            "./gradlew --no-daemon testDebugUnitTest",
            "flutter build apk --release --dart-define-from-file=config/oauth.json",
            "flutter build ios --simulator --debug --dart-define-from-file=config/oauth.json",
            "xcodebuild test",
            "flutter build ios --release --no-codesign --dart-define-from-file=config/oauth.json",
        ):
            self.assertIn(command, self.content)

    def test_builds_run_on_macos(self) -> None:
        self.assertIn("runs-on: macos-latest", self.content)

    def test_artifacts_and_retention(self) -> None:
        self.assertIn("actions/upload-artifact@", self.content)
        self.assertIn("build/app/outputs/flutter-apk/app-release.apk", self.content)
        self.assertIn("MyStory-ios-unsigned.zip", self.content)
        self.assertIn("retention-days: 14", self.content)
        self.assertIn("if: ${{ !cancelled() }}", self.content)


if __name__ == "__main__":
    unittest.main()
