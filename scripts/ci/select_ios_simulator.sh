#!/usr/bin/env bash
# Prints the UDID of an available iPhone simulator (newest iOS runtime first).
set -euo pipefail

xcrun simctl list devices available --json | python3 -c '
import json, sys

data = json.load(sys.stdin)["devices"]
candidates = []
for runtime, devices in data.items():
    if "iOS" not in runtime:
        continue
    version = tuple(int(part) for part in runtime.rsplit("iOS-", 1)[-1].split("-") if part.isdigit())
    for device in devices:
        if device["name"].startswith("iPhone") and device.get("isAvailable", True):
            candidates.append((version, device["name"], device["udid"]))
if not candidates:
    sys.exit("No available iPhone simulator found")
candidates.sort(reverse=True)
print(candidates[0][2])
'
