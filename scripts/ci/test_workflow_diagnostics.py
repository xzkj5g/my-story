#!/usr/bin/env python3
"""Contract tests for workflow diagnostics and release-readiness evidence."""

from pathlib import Path
import unittest

CI_WORKFLOW = Path(".github/workflows/ci.yml")
RELEASE_WORKFLOW = Path(".github/workflows/release.yml")
QUICKSTART = Path("specs/002-github-actions-android-cicd/quickstart.md")


class WorkflowDiagnosticsTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.ci = CI_WORKFLOW.read_text(encoding="utf-8")
        cls.release = RELEASE_WORKFLOW.read_text(encoding="utf-8")
        cls.quickstart = QUICKSTART.read_text(encoding="utf-8")

    def test_named_stages(self) -> None:
        for stage in ("Set up Java 17", "Set up Android SDK API 36", "Set up Flutter", "Run flutter analyze", "Run Android unit tests"):
            self.assertIn(stage, self.ci)
        for stage in ("Configure Android signing", "Build and verify signed Android release", "Publish GitHub Release"):
            self.assertIn(stage, self.release)

    def test_artifacts_and_readiness_evidence(self) -> None:
        self.assertIn("retention-days: 14", self.ci)
        self.assertIn("GITHUB_STEP_SUMMARY", self.ci)
        self.assertIn("GITHUB_STEP_SUMMARY", self.release)
        self.assertIn("GITHUB_SHA", self.release)
        self.assertIn("certificate.txt", self.release)
        self.assertIn("app-release.apk.sha256", self.release)
        self.assertIn("95%", self.quickstart)
        self.assertIn("5 minutes", self.quickstart)
        self.assertIn("2 minutes", self.quickstart)


if __name__ == "__main__":
    unittest.main()
