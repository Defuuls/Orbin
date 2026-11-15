#!/usr/bin/env python3
"""Check the archived app's privacy purpose strings before distribution signing.

App Store Connect rejects a delivery (ITMS-90683) when any executable in the app
references a protected API whose purpose string is missing from the app's Info.plist,
whether or not the app ever calls it. This scans the app executable and every embedded
framework for those references and requires the matching Info.plist key.

Without --spec nothing is written: a missing string fails the check. With --spec, a
missing string is filled from that XcodeGen spec, which lets current tooling upload a
pinned release whose own spec predates the string. An existing string is never changed.
"""

from __future__ import annotations

import argparse
import json
import plistlib
import re
import sys
from pathlib import Path

from prepare_ios_framework_metadata import (
    MetadataError,
    archived_directory,
    replace_plist,
    require_regular_file,
)

APP_PATH = Path("Products/Applications/Orbin.app")

# Purpose-string key -> symbol references that make App Store Connect require it.
# The class prefix also covers AVCaptureDeviceInput and AVCaptureDeviceDiscoverySession.
REQUIRED_BY = {
    "NSCameraUsageDescription": (
        b"_OBJC_CLASS_$_AVCaptureDevice",
        b"_OBJC_CLASS_$_AVCaptureSession",
        b"_OBJC_CLASS_$_AVCaptureVideoDataOutput",
    ),
}

BUNDLE_EXECUTABLE = re.compile(r"[A-Za-z0-9][A-Za-z0-9._ -]*")
# A YAML scalar this simple reads the same as plain text; anything else is refused.
YAML_SYNTAX_START = tuple("\"'|>&*!%@`[]{},#?-:")


def spec_purpose_string(spec: Path, key: str) -> str | None:
    """Read one purpose string from the XcodeGen spec, or None when it declares none."""
    values = re.findall(rf"^[ \t]+{re.escape(key)}:[ \t]*(.*?)[ \t]*$", spec.read_text("utf-8"), re.MULTILINE)
    if not values:
        return None
    if len(values) > 1:
        raise MetadataError(f"The spec declares {key} more than once")
    value = values[0]
    if not value or value.startswith(YAML_SYNTAX_START) or ": " in value or " #" in value or value.endswith(":"):
        raise MetadataError(f"The spec's {key} must be a plain single-line string")
    return value


def bundle_executable(bundle: Path) -> Path:
    """The regular executable a bundle's Info.plist names."""
    info_path = bundle / "Info.plist"
    require_regular_file(info_path)
    try:
        info = plistlib.loads(info_path.read_bytes())
    except Exception as error:
        raise MetadataError(f"Invalid {bundle.name} Info.plist") from error
    name = info.get("CFBundleExecutable") if isinstance(info, dict) else None
    if not isinstance(name, str) or not BUNDLE_EXECUTABLE.fullmatch(name):
        raise MetadataError(f"Invalid CFBundleExecutable in {bundle.name}")
    executable = bundle / name
    require_regular_file(executable)
    return executable


def archived_executables(app: Path) -> list[Path]:
    executables = [bundle_executable(app)]
    frameworks = app / "Frameworks"
    if frameworks.is_symlink():
        raise MetadataError("Expected a real Frameworks directory")
    if frameworks.is_dir():
        for framework in sorted(frameworks.glob("*.framework")):
            if framework.is_symlink() or not framework.is_dir():
                raise MetadataError(f"Expected a real framework directory: {framework.name}")
            executables.append(bundle_executable(framework))
    return executables


def prepare_archive(archive: Path, spec: Path | None = None) -> dict[str, object]:
    app = archived_directory(archive, APP_PATH)
    binaries = {path: path.read_bytes() for path in archived_executables(app)}
    required = {
        key: sorted(
            str(path.relative_to(app)) for path, data in binaries.items() if any(symbol in data for symbol in symbols)
        )
        for key, symbols in REQUIRED_BY.items()
    }
    required = {key: paths for key, paths in required.items() if paths}

    info_path = app / "Info.plist"
    original = info_path.read_bytes()
    info = plistlib.loads(original)
    if info.get("CFBundlePackageType") != "APPL":
        raise MetadataError("Expected the application Info.plist")
    filled: list[str] = []
    for key, paths in required.items():
        if key in info:
            if not isinstance(info[key], str) or not info[key].strip():
                raise MetadataError(f"{key} must be a non-empty string")
            continue
        value = spec_purpose_string(spec, key) if spec is not None else None
        if value is None:
            raise MetadataError(
                f"{key} is missing from the app Info.plist but required by {', '.join(paths)}; "
                "declare it in iosApp/project.yml"
            )
        info[key] = value
        filled.append(key)
    if filled:
        replace_plist(info_path, info, original)
    return {"app": str(APP_PATH), "requiredBy": required, "filledFromSpec": filled, "changed": bool(filled)}


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("archive", type=Path, help="The unsigned Orbin .xcarchive directory")
    parser.add_argument("--spec", type=Path, help="XcodeGen spec to fill a missing purpose string from")
    args = parser.parse_args()
    try:
        report = prepare_archive(args.archive, args.spec)
    except (MetadataError, OSError, UnicodeDecodeError) as error:
        print(f"Purpose string check failed: {error}", file=sys.stderr)
        return 1
    print(json.dumps(report, sort_keys=True))
    return 0


if __name__ == "__main__":
    sys.exit(main())
