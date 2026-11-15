#!/usr/bin/env python3
"""Repair the archived FFmpeg framework's deployment metadata before distribution signing.

Only Orbin.app/Frameworks/ffmpegkit.framework/Info.plist is changed. The deployment
target comes from the existing device executable; neither executable bytes nor the
downloaded XCFramework are rewritten. Unsupported or contradictory inputs fail closed.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import plistlib
import re
import stat
import struct
import sys
import tempfile
from pathlib import Path

FRAMEWORK_PATH = Path("Products/Applications/Orbin.app/Frameworks/ffmpegkit.framework")
ARM64 = 0x0100000C
MH_DYLIB = 6
LC_BUILD_VERSION = 0x32
LC_VERSION_MIN_IPHONEOS = 0x25
IOS_PLATFORM = 2


class MetadataError(ValueError):
    """The archived framework cannot be repaired without guessing."""


def deployment_target(data: bytes) -> tuple[int, int, int]:
    """Read one device deployment command from the pinned thin ARM64 Mach-O format."""
    if len(data) < 32 or data[:4] != b"\xcf\xfa\xed\xfe":
        raise MetadataError("Expected a thin little-endian ARM64 Mach-O executable")
    _, cpu, _, file_type, count, command_bytes, _, _ = struct.unpack_from("<8I", data)
    if cpu != ARM64 or file_type != MH_DYLIB:
        raise MetadataError("Expected an ARM64 dynamic library")
    end = 32 + command_bytes
    if end > len(data) or count * 8 > command_bytes:
        raise MetadataError("Truncated Mach-O load commands")
    offset = 32
    targets: list[int] = []
    for _ in range(count):
        if offset + 8 > end:
            raise MetadataError("Truncated Mach-O load command")
        command, size = struct.unpack_from("<2I", data, offset)
        if size < 8 or size % 8 or offset + size > end:
            raise MetadataError("Invalid Mach-O load command size")
        if command == LC_BUILD_VERSION:
            if size < 24:
                raise MetadataError("Truncated LC_BUILD_VERSION")
            platform, minimum, _, tools = struct.unpack_from("<4I", data, offset + 8)
            if platform != IOS_PLATFORM or size != 24 + tools * 8:
                raise MetadataError("Expected a complete iOS device LC_BUILD_VERSION")
            targets.append(minimum)
        elif command == LC_VERSION_MIN_IPHONEOS:
            if size != 16:
                raise MetadataError("Invalid LC_VERSION_MIN_IPHONEOS")
            targets.append(struct.unpack_from("<I", data, offset + 8)[0])
        elif command in (0x24, 0x2F, 0x30):
            raise MetadataError("Unexpected macOS, tvOS or watchOS deployment command")
        offset += size
    if offset != end or len(targets) != 1:
        raise MetadataError("Expected exactly one unambiguous iOS deployment command")
    minimum = targets[0]
    version = (minimum >> 16, (minimum >> 8) & 255, minimum & 255)
    if version < (8, 0, 0):
        raise MetadataError("The 64-bit iOS deployment target must be at least 8.0")
    return version


def version_string(version: tuple[int, int, int]) -> str:
    major, minor, patch = version
    return f"{major}.{minor}" + (f".{patch}" if patch else "")


def plist_version(value: object) -> tuple[int, int, int]:
    if (
        not isinstance(value, str)
        or len(value) > 13
        or not re.fullmatch(r"(?:0|[1-9][0-9]*)(?:\.(?:0|[1-9][0-9]*)){0,2}", value)
    ):
        raise MetadataError("Invalid existing MinimumOSVersion")
    parts = [int(part) for part in value.split(".")]
    parts += [0] * (3 - len(parts))
    return parts[0], parts[1], parts[2]


def prepare_archive(archive: Path) -> dict[str, object]:
    if archive.is_symlink() or not archive.is_dir():
        raise MetadataError("Expected an archive directory, not a symlink")
    framework = archive.resolve()
    for part in FRAMEWORK_PATH.parts:
        framework /= part
        if framework.is_symlink() or not framework.is_dir():
            raise MetadataError("The archived framework path must contain only real directories")
    info_path = framework / "Info.plist"
    executable = framework / "ffmpegkit"
    for path in (info_path, executable):
        if path.is_symlink() or not path.is_file():
            raise MetadataError(f"Expected a regular, non-symlink {path.name}")
    original = info_path.read_bytes()
    try:
        info = plistlib.loads(original)
    except Exception as error:
        raise MetadataError("Invalid framework Info.plist") from error
    if (
        not isinstance(info, dict)
        or info.get("CFBundleExecutable") != "ffmpegkit"
        or info.get("CFBundlePackageType") != "FMWK"
    ):
        raise MetadataError("Expected the ffmpegkit framework Info.plist")
    binary = executable.read_bytes()
    minimum = deployment_target(binary)
    if "MinimumOSVersion" in info and plist_version(info["MinimumOSVersion"]) != minimum:
        raise MetadataError("Existing MinimumOSVersion conflicts with the executable")
    if "CFBundleSupportedPlatforms" in info and info["CFBundleSupportedPlatforms"] != ["iPhoneOS"]:
        raise MetadataError("Existing CFBundleSupportedPlatforms conflicts with the iOS executable")
    changed = "MinimumOSVersion" not in info or "CFBundleSupportedPlatforms" not in info
    if changed:
        info.setdefault("MinimumOSVersion", version_string(minimum))
        info.setdefault("CFBundleSupportedPlatforms", ["iPhoneOS"])
        fmt = plistlib.FMT_BINARY if original.startswith(b"bplist00") else plistlib.FMT_XML
        updated = plistlib.dumps(info, fmt=fmt, sort_keys=False)
        temporary: Path | None = None
        try:
            with tempfile.NamedTemporaryFile(
                mode="wb", dir=framework, prefix=".orbin-Info-", delete=False
            ) as output:
                temporary = Path(output.name)
                os.fchmod(output.fileno(), stat.S_IMODE(info_path.stat().st_mode))
                output.write(updated)
                output.flush()
                os.fsync(output.fileno())
            os.replace(temporary, info_path)
        finally:
            if temporary is not None:
                temporary.unlink(missing_ok=True)
    return {
        "framework": str(FRAMEWORK_PATH),
        "minimumOSVersion": version_string(minimum),
        "supportedPlatforms": ["iPhoneOS"],
        "binarySHA256": hashlib.sha256(binary).hexdigest(),
        "changed": changed,
    }


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("archive", type=Path, help="The unsigned Orbin .xcarchive directory")
    args = parser.parse_args()
    try:
        report = prepare_archive(args.archive)
    except (MetadataError, OSError) as error:
        print(f"Framework metadata preparation failed: {error}", file=sys.stderr)
        return 1
    print(json.dumps(report, sort_keys=True))
    return 0


if __name__ == "__main__":
    sys.exit(main())
