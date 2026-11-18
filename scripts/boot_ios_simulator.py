#!/usr/bin/env python3
"""Choose the newest available iPhone simulator at or above a minimum iOS version, and boot it.

Used by the iOS workflows. Kotlin's standalone simulator mode omits system services (Keychain
among them), so the tests need a fully booted device. Booting starts here and the caller waits
on `xcrun simctl bootstatus` later, so the boot overlaps JDK and Gradle setup.

Writes ORBIN_IOS_TEST_DEVICE (the UDID) and ORBIN_IOS_TEST_RUNTIME to the file named by
GITHUB_ENV, so later steps can address the device.

    python3 scripts/boot_ios_simulator.py --min-version 17.4
"""

from __future__ import annotations

import argparse
import json
import os
import re
import subprocess
import sys

RUNTIME_VERSION = re.compile(r"\.iOS-(\d+)(?:-(\d+))?(?:-(\d+))?$")


def parse_version(text: str) -> tuple[int, int, int]:
    """'17.4' -> (17, 4, 0). Rejects anything that is not one to three dot-separated numbers."""
    parts = text.split(".")
    if not 1 <= len(parts) <= 3 or not all(part.isdigit() for part in parts):
        raise ValueError(f"not a version: {text!r}")
    numbers = [int(part) for part in parts] + [0] * (3 - len(parts))
    return numbers[0], numbers[1], numbers[2]


def runtime_version(runtime: str) -> tuple[int, int, int] | None:
    """The iOS version of a simctl runtime identifier, or None for a non-iOS runtime."""
    match = RUNTIME_VERSION.search(runtime)
    if match is None:
        return None
    return tuple(int(part or "0") for part in match.groups())  # type: ignore[return-value]


def choose_device(listing: dict, minimum: tuple[int, int, int]) -> tuple[tuple[int, int, int], str, dict]:
    """The available iPhone on the newest iOS runtime at or above [minimum].

    Raises SystemExit naming the minimum when there is none, so the job fails with a reason
    rather than running the suites on an older runtime than they require.
    """
    candidates = []
    for runtime, devices in listing["devices"].items():
        version = runtime_version(runtime)
        if version is None or version < minimum:
            continue
        candidates.extend(
            (version, runtime, device)
            for device in devices
            if device.get("isAvailable") and device["name"].startswith("iPhone")
        )
    if not candidates:
        shown = ".".join(str(part) for part in minimum)
        raise SystemExit(f"No available iPhone simulator on iOS {shown} or later")
    return max(candidates, key=lambda candidate: candidate[0])


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--min-version", required=True, type=parse_version, help="lowest iOS version, e.g. 17.4")
    args = parser.parse_args(argv)

    listing = json.loads(subprocess.check_output(["xcrun", "simctl", "list", "devices", "available", "--json"]))
    version, runtime, device = choose_device(listing, args.min_version)
    print(
        f"Native test simulator: {device['name']} | runtime={runtime} | "
        f"OS={'.'.join(map(str, version))} | UDID={device['udid']}"
    )
    if device["state"] != "Booted":
        subprocess.run(["xcrun", "simctl", "boot", device["udid"]], check=True)
    with open(os.environ["GITHUB_ENV"], "a", encoding="utf-8") as env:
        env.write("ORBIN_IOS_TEST_DEVICE=" + device["udid"] + "\n")
        env.write("ORBIN_IOS_TEST_RUNTIME=" + runtime + "\n")
    return 0


if __name__ == "__main__":
    sys.exit(main())
