#!/usr/bin/env bash
set -euo pipefail

python3 scripts/ci/validate_workflows.py
python3 scripts/ci/validate_release_secrets.py
python3 -m unittest discover -s scripts/ci -p 'test_*.py'
./gradlew --no-daemon detekt testDebugUnitTest assembleDebug
