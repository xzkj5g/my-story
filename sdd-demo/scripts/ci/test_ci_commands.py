#!/usr/bin/env python3
"""Contract tests for CI commands and artifacts."""

from pathlib import Path
import unittest

WORKFLOW = Path(".github/workflows/android-ci.yml")


class CiCommandTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.content = WORKFLOW.read_text(encoding="utf-8")

    def test_required_commands(self) -> None:
        for command in (
            "./gradlew --no-daemon detekt",
            "./gradlew --no-daemon testDebugUnitTest",
            "./gradlew --no-daemon assembleDebug",
        ):
            self.assertIn(command, self.content)

    def test_artifacts_and_retention(self) -> None:
        self.assertIn("actions/upload-artifact@", self.content)
        self.assertIn("app/build/outputs/apk/debug/*.apk", self.content)
        self.assertIn("retention-days: 14", self.content)
        self.assertIn("if: ${{ !cancelled() }}", self.content)


if __name__ == "__main__":
    unittest.main()
