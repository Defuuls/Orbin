from __future__ import annotations

import hashlib
import plistlib
import stat
import struct
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path
from unittest import mock

SCRIPTS_DIR = Path(__file__).resolve().parents[1]
if str(SCRIPTS_DIR) not in sys.path:
    sys.path.insert(0, str(SCRIPTS_DIR))

import prepare_ios_framework_metadata as metadata


def packed_version(major: int, minor: int = 0, patch: int = 0) -> int:
    return (major << 16) | (minor << 8) | patch


def build_version(platform: int = 2, minimum: int = packed_version(13), tools: int = 1) -> bytes:
    return struct.pack("<6I", 0x32, 24 + tools * 8, platform, minimum, packed_version(17, 5), tools) + (
        struct.pack("<2I", 3, packed_version(17)) * tools
    )


def legacy_version(command: int = 0x25, minimum: int = packed_version(13)) -> bytes:
    return struct.pack("<4I", command, 16, minimum, packed_version(17, 5))


def macho(*commands: bytes, cpu: int = metadata.ARM64, file_type: int = 6) -> bytes:
    header = struct.pack("<8I", 0xFEEDFACF, cpu, 0, file_type, len(commands), sum(map(len, commands)), 0, 0)
    return header + b"".join(commands)


class FrameworkMetadataTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        self.archive = self.root / "Orbin archive.xcarchive"
        self.framework = self.archive / metadata.FRAMEWORK_PATH
        self.framework.mkdir(parents=True)
        self.info_path = self.framework / "Info.plist"
        self.executable = self.framework / "ffmpegkit"
        self.binary = macho(build_version()) + b"original executable payload"
        self.executable.write_bytes(self.binary)
        self.info = {
            "CFBundleExecutable": "ffmpegkit",
            "CFBundlePackageType": "FMWK",
            "CFBundleIdentifier": "io.github.akashskypatel.ffmpegkit",
            "CFBundleVersion": "1",
            "Unrelated": {"Values": [True, b"preserve me", 42]},
        }
        self.write_info(self.info)

    def write_info(self, value: object, fmt: plistlib.PlistFormat = plistlib.FMT_XML) -> None:
        self.info_path.write_bytes(plistlib.dumps(value, fmt=fmt, sort_keys=False))

    def assert_rejected_without_writes(self, archive: Path | None = None) -> None:
        before_info = self.info_path.read_bytes()
        before_binary = self.executable.read_bytes()
        with self.assertRaises(metadata.MetadataError):
            metadata.prepare_archive(archive or self.archive)
        self.assertEqual(self.info_path.read_bytes(), before_info)
        self.assertEqual(self.executable.read_bytes(), before_binary)
        self.assertEqual(list(self.framework.glob(".orbin-Info-*")), [])

    def test_fills_missing_metadata_from_device_binary_and_preserves_other_data(self) -> None:
        other = self.framework.parent / "Other.framework"
        other.mkdir()
        untouched = other / "Info.plist"
        untouched.write_bytes(b"unrelated framework metadata")
        self.info_path.chmod(0o640)
        report = metadata.prepare_archive(self.archive)
        expected = self.info | {"MinimumOSVersion": "13.0", "CFBundleSupportedPlatforms": ["iPhoneOS"]}
        self.assertEqual(plistlib.loads(self.info_path.read_bytes()), expected)
        self.assertEqual(self.executable.read_bytes(), self.binary)
        self.assertEqual(stat.S_IMODE(self.info_path.stat().st_mode), 0o640)
        self.assertEqual(untouched.read_bytes(), b"unrelated framework metadata")
        self.assertEqual(report["minimumOSVersion"], "13.0")
        self.assertEqual(report["binarySHA256"], hashlib.sha256(self.binary).hexdigest())
        self.assertTrue(report["changed"])

    def test_binary_plist_format_and_patch_version_are_preserved(self) -> None:
        self.executable.write_bytes(macho(build_version(minimum=packed_version(13, 2, 1))))
        self.write_info(self.info, plistlib.FMT_BINARY)
        report = metadata.prepare_archive(self.archive)
        self.assertTrue(self.info_path.read_bytes().startswith(b"bplist00"))
        self.assertEqual(report["minimumOSVersion"], "13.2.1")

    def test_correct_metadata_is_idempotent_without_rewriting_the_file(self) -> None:
        self.write_info(self.info | {"MinimumOSVersion": "13.0.0", "CFBundleSupportedPlatforms": ["iPhoneOS"]})
        before = self.info_path.read_bytes()
        with mock.patch.object(metadata.os, "replace", side_effect=AssertionError("Unexpected write")):
            report = metadata.prepare_archive(self.archive)
        self.assertFalse(report["changed"])
        self.assertEqual(self.info_path.read_bytes(), before)

    def test_second_run_is_a_noop_after_a_repair(self) -> None:
        metadata.prepare_archive(self.archive)
        before = self.info_path.stat()
        report = metadata.prepare_archive(self.archive)
        self.assertFalse(report["changed"])
        self.assertEqual(self.info_path.stat().st_ino, before.st_ino)
        self.assertEqual(self.info_path.stat().st_mtime_ns, before.st_mtime_ns)

    def test_legacy_ios_device_command_has_the_same_metadata_contract(self) -> None:
        self.executable.write_bytes(macho(legacy_version(minimum=packed_version(12, 4))))
        self.assertEqual(metadata.prepare_archive(self.archive)["minimumOSVersion"], "12.4")

    def test_other_load_commands_can_precede_the_deployment_command(self) -> None:
        self.executable.write_bytes(macho(struct.pack("<2I", 0x1B, 8), build_version()))
        self.assertEqual(metadata.prepare_archive(self.archive)["minimumOSVersion"], "13.0")

    def test_existing_minimum_conflicts_and_invalid_types_never_change_files(self) -> None:
        for minimum in (
            "12.0", "14.0", "", "13.0.0.0", "13.0beta", "013.0", "1\u0663.0", "13" + "0" * 20, 13.0, False
        ):
            with self.subTest(minimum=minimum):
                self.write_info(self.info | {"MinimumOSVersion": minimum})
                self.assert_rejected_without_writes()

    def test_existing_platform_conflicts_never_change_files(self) -> None:
        for platforms in (["iPhoneSimulator"], ["iPhoneOS", "iPhoneSimulator"], [], "iPhoneOS"):
            with self.subTest(platforms=platforms):
                self.write_info(self.info | {"CFBundleSupportedPlatforms": platforms})
                self.assert_rejected_without_writes()

    def test_simulator_catalyst_and_unknown_platforms_are_rejected(self) -> None:
        for platform in (0, 1, 6, 7, 999):
            with self.subTest(platform=platform):
                self.executable.write_bytes(macho(build_version(platform=platform)))
                self.assert_rejected_without_writes()

    def test_unsupported_architectures_file_types_and_fat_binary_are_rejected(self) -> None:
        cases = [
            macho(build_version(), cpu=0x01000007),
            macho(build_version(), file_type=2),
            b"\xca\xfe\xba\xbe" + b"\0" * 60,
        ]
        for binary in cases:
            with self.subTest(binary=binary[:8]):
                self.executable.write_bytes(binary)
                self.assert_rejected_without_writes()

    def test_missing_duplicate_and_conflicting_deployment_commands_are_rejected(self) -> None:
        cases = [
            macho(),
            macho(build_version(), build_version()),
            macho(build_version(), legacy_version(minimum=packed_version(14))),
            macho(build_version(), legacy_version(command=0x24)),
            macho(build_version(), legacy_version(command=0x2F)),
            macho(build_version(), legacy_version(command=0x30)),
        ]
        for binary in cases:
            with self.subTest(binary=binary.hex()):
                self.executable.write_bytes(binary)
                self.assert_rejected_without_writes()

    def test_truncated_or_malformed_commands_are_rejected(self) -> None:
        valid = macho(build_version())
        cases = [
            valid[:20],
            valid[:-1],
            macho(struct.pack("<2I", 0x32, 0)),
            macho(struct.pack("<2I", 0x32, 16) + b"\0" * 8),
            macho(struct.pack("<6I", 0x32, 24, 2, packed_version(13), 0, 1)),
            macho(struct.pack("<2I", 0x25, 8)),
            struct.pack("<8I", 0xFEEDFACF, metadata.ARM64, 0, 6, 1, 16, 0, 0) + build_version(),
            struct.pack("<8I", 0xFEEDFACF, metadata.ARM64, 0, 6, 1, 40, 0, 0) + build_version() + b"\0" * 8,
        ]
        for binary in cases:
            with self.subTest(binary=binary.hex()):
                self.executable.write_bytes(binary)
                self.assert_rejected_without_writes()

    def test_zero_and_pre_ios8_deployment_versions_are_rejected(self) -> None:
        for minimum in (0, packed_version(7, 1)):
            with self.subTest(minimum=minimum):
                self.executable.write_bytes(macho(build_version(minimum=minimum)))
                self.assert_rejected_without_writes()

    def test_invalid_plists_or_other_bundle_executables_are_rejected(self) -> None:
        for value in (
            [],
            self.info | {"CFBundleExecutable": "../ffmpegkit"},
            self.info | {"CFBundlePackageType": "APPL"},
        ):
            self.write_info(value)
            self.assert_rejected_without_writes()
        self.info_path.write_bytes(b"not a plist")
        self.assert_rejected_without_writes()

    def test_symlinked_plist_and_executable_are_rejected(self) -> None:
        for name in ("Info.plist", "ffmpegkit"):
            with self.subTest(name=name):
                path = self.framework / name
                outside = self.root / name
                path.rename(outside)
                path.symlink_to(outside)
                self.assert_rejected_without_writes()
                path.unlink()
                outside.rename(path)

    def test_symlinked_archive_or_framework_ancestor_is_rejected(self) -> None:
        archive_link = self.root / "archive-link"
        archive_link.symlink_to(self.archive, target_is_directory=True)
        self.assert_rejected_without_writes(archive_link)
        products = self.archive / "Products"
        outside = self.root / "outside-products"
        products.rename(outside)
        products.symlink_to(outside, target_is_directory=True)
        self.assert_rejected_without_writes()

    def test_failed_atomic_replace_leaves_the_original_and_no_temporary_file(self) -> None:
        before = self.info_path.read_bytes()
        with mock.patch.object(metadata.os, "replace", side_effect=OSError("disk unavailable")):
            with self.assertRaisesRegex(OSError, "disk unavailable"):
                metadata.prepare_archive(self.archive)
        self.assertEqual(self.info_path.read_bytes(), before)
        self.assertEqual(self.executable.read_bytes(), self.binary)
        self.assertEqual(list(self.framework.glob(".orbin-Info-*")), [])

    def test_cli_prints_a_receipt_and_reports_conflicts_with_failure_status(self) -> None:
        script = SCRIPTS_DIR / "prepare_ios_framework_metadata.py"
        success = subprocess.run([sys.executable, str(script), str(self.archive)], capture_output=True, text=True)
        self.assertEqual(success.returncode, 0, success.stderr)
        self.assertIn('"minimumOSVersion": "13.0"', success.stdout)
        self.write_info(self.info | {"MinimumOSVersion": "12.0"})
        failure = subprocess.run([sys.executable, str(script), str(self.archive)], capture_output=True, text=True)
        self.assertNotEqual(failure.returncode, 0)
        self.assertIn("conflicts with the executable", failure.stderr)
        self.assertEqual(failure.stdout, "")


if __name__ == "__main__":
    unittest.main()
