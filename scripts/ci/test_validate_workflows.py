#!/usr/bin/env python3
"""Contract tests for the validation workflow."""

from pathlib import Path
import unittest

import yaml

WORKFLOW = Path(".github/workflows/android-ci.yml")
EXPECTED_ACTIONS = (
    "actions/checkout@v6",
    "actions/setup-java@v6",
    "gradle/actions/setup-gradle@v6",
    "android-actions/setup-android@v4",
    "actions/upload-artifact@v7",
)
RELEASE_ACTIONS = (
    "actions/checkout@v6",
    "actions/setup-java@v6",
    "gradle/actions/setup-gradle@v6",
    "android-actions/setup-android@v4",
    "softprops/action-gh-release@v3",
)


class ValidationWorkflowTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.content = WORKFLOW.read_text(encoding="utf-8")
        cls.workflow = yaml.load(cls.content, Loader=yaml.BaseLoader)

    def test_triggers_and_permissions(self) -> None:
        self.assertIn("on:", self.content)
        self.assertIn("pull_request:", self.content)
        self.assertIn("push:", self.content)
        self.assertIn("branches: [main]", self.content)
        self.assertIn("contents: read", self.content)

    def test_toolchain_and_action_tags(self) -> None:
        self.assertIn("java-version: '17'", self.content)
        self.assertIn("platforms;android-36", self.content)
        for action in EXPECTED_ACTIONS:
            self.assertIn(action, self.content)

    def test_gradle_wrapper(self) -> None:
        self.assertIn("./gradlew", self.content)

    def test_release_workflow_pins_every_planned_action(self) -> None:
        release_content = Path(".github/workflows/android-release.yml").read_text(encoding="utf-8")
        for action in RELEASE_ACTIONS:
            self.assertIn(action, release_content)
        self.assertEqual(release_content.count("actions/upload-artifact@"), 0)
        self.assertNotRegex(release_content, r"uses:\s*[^@\s]+@[0-9a-f]{40}")


if __name__ == "__main__":
    unittest.main()
