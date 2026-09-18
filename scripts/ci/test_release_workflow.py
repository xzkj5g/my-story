#!/usr/bin/env python3
"""Contract tests for release safety and publication ordering."""

from pathlib import Path
from datetime import datetime
import unittest

WORKFLOW = Path(".github/workflows/android-release.yml")


class ReleaseWorkflowTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.content = WORKFLOW.read_text(encoding="utf-8")

    def test_trigger_input_branch_and_permission(self) -> None:
        self.assertIn("workflow_dispatch:", self.content)
        self.assertIn("release_tag:", self.content)
        self.assertIn("YYYY.MM.DD", self.content)
        self.assertIn('refs/heads/main', self.content)
        self.assertIn("contents: write", self.content)

    def test_safety_order_and_concurrency(self) -> None:
        self.assertIn("cancel-in-progress: false", self.content)
        self.assertLess(self.content.index("Check for duplicate release"), self.content.index("Sign and verify release APK"))
        self.assertLess(self.content.index("Sign and verify release APK"), self.content.index("Publish GitHub Release"))
        self.assertIn("gh api --silent", self.content)
        self.assertIn("gh release view", self.content)

    def test_calendar_date_is_normalized_before_signing(self) -> None:
        self.assertIn("normalized_tag=", self.content)
        self.assertIn('[ "$normalized_tag" != "$RELEASE_TAG" ]', self.content)
        self.assertLess(self.content.index("Validate release tag"), self.content.index("Sign and verify release APK"))
        for invalid_tag in ("2026.02.29", "2026.04.31"):
            with self.assertRaises(ValueError):
                datetime.strptime(invalid_tag, "%Y.%m.%d")


if __name__ == "__main__":
    unittest.main()
