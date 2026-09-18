#!/usr/bin/env python3
"""Contract tests for the validation workflow."""

from pathlib import Path
import unittest

import yaml

WORKFLOW = Path(".github/workflows/android-ci.yml")
EXPECTED_ACTIONS = (
    "actions/checkout@de7274f081f381c8f8158605e0321c36c376e2e6",
    "actions/setup-java@043fb46d1a93c77aae656e7c1c64a875d1fc6a0a",
    "gradle/actions/setup-gradle@9c971963bec38e04b3d30dcc455b5382be2fdbfb",
    "android-actions/setup-android@be39fa834029ff78f1a44aa3bb0819b8fc2bd8fd",
    "actions/upload-artifact@3e5f45b2cfb9172054b4087a40e8e0b5a5461e7c",
)
RELEASE_ACTIONS = (
    "actions/checkout@de7274f081f381c8f8158605e0321c36c376e2e6",
    "actions/setup-java@043fb46d1a93c77aae656e7c1c64a875d1fc6a0a",
    "gradle/actions/setup-gradle@9c971963bec38e04b3d30dcc455b5382be2fdbfb",
    "android-actions/setup-android@be39fa834029ff78f1a44aa3bb0819b8fc2bd8fd",
    "softprops/action-gh-release@efb35369e0ad2afab669f228072c1b0d510eae64",
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

    def test_toolchain_and_pins(self) -> None:
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


if __name__ == "__main__":
    unittest.main()
