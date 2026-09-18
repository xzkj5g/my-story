#!/usr/bin/env python3
"""Static validation for the repository's GitHub Actions workflows."""

from __future__ import annotations

import re
import sys
from pathlib import Path

import yaml

WORKFLOW_DIR = Path(".github/workflows")
ACTION_PATTERN = re.compile(r"uses:\s*([^@\s]+)@([^\s]+)")
SECRET_TERMS = ("GITHUB_TOKEN", "KEYSTORE_PASSWORD", "KEY_PASSWORD", "KEYSTORE_BASE64")


def load_workflow(path: Path) -> tuple[dict, str]:
    content = path.read_text(encoding="utf-8")
    parsed = yaml.load(content, Loader=yaml.BaseLoader)
    if not isinstance(parsed, dict):
        raise ValueError("top-level YAML value must be a mapping")
    return parsed, content


def validate_workflow(path: Path) -> list[str]:
    errors: list[str] = []
    try:
        workflow, content = load_workflow(path)
    except (OSError, yaml.YAMLError, ValueError) as error:
        return [f"{path}: invalid YAML: {error}"]

    if "name" not in workflow:
        errors.append(f"{path}: missing name")
    if "jobs" not in workflow or not isinstance(workflow["jobs"], dict):
        errors.append(f"{path}: missing jobs mapping")
    if "on" not in workflow:
        errors.append(f"{path}: missing on trigger")
    if "permissions" not in workflow:
        errors.append(f"{path}: missing top-level permissions")
    if "contents: write" in content and "softprops/action-gh-release@" in content:
        if "permissions:\n  contents: write" not in content:
            errors.append(f"{path}: release publication must declare contents: write")
    for action, reference in ACTION_PATTERN.findall(content):
        if not re.fullmatch(r"v\d+", reference):
            errors.append(f"{path}: action {action} must use a stable major tag (found {reference})")
    return errors


def main() -> int:
    workflows = sorted(WORKFLOW_DIR.glob("*.yml")) + sorted(WORKFLOW_DIR.glob("*.yaml"))
    if not workflows:
        print(f"No workflows found under {WORKFLOW_DIR}", file=sys.stderr)
        return 1

    errors = [error for workflow in workflows for error in validate_workflow(workflow)]
    if errors:
        print("\n".join(errors), file=sys.stderr)
        return 1
    print(f"Validated {len(workflows)} workflow file(s).")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
