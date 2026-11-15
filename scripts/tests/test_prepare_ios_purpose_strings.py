from __future__ import annotations

import plistlib
import stat
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
import prepare_ios_purpose_strings as purpose

KEY = "NSCameraUsageDescription"
CAMERA = b"\0_OBJC_CLASS_$_AVCaptureDeviceInput\0"
PLAYER = b"\0_OBJC_CLASS_$_AVPlayer\0_OBJC_CLASS_$_AVAudioSession\0"
TEXT = "Orbin does not use the camera."
PROJECT_SPEC = SCRIPTS_DIR.parent / "iosApp" / "project.yml"


class PurposeStringTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        self.archive = self.root / "Orbin archive.xcarchive"
        self.app = self.archive / purpose.APP_PATH
        self.app.mkdir(parents=True)
        self.info_path = self.app / "Info.plist"
        self.info = {
            "CFBundleExecutable": "Orbin",
            "CFBundlePackageType": "APPL",
            "NSFaceIDUsageDescription": "Unlock",
            "Unrelated": {"Values": [True, b"preserve me", 42]},
        }
        self.write_info(self.info)
        (self.app / "Orbin").write_bytes(PLAYER)
        self.ffmpeg = self.add_framework("ffmpegkit", CAMERA)
        self.spec = self.root / "project.yml"
        self.write_spec(f"        {KEY}: {TEXT}")

    def write_info(self, value: object, fmt: plistlib.PlistFormat = plistlib.FMT_BINARY) -> None:
        self.info_path.write_bytes(plistlib.dumps(value, fmt=fmt, sort_keys=False))

    def write_spec(self, *lines: str) -> None:
        self.spec.write_text("\n".join(("targets:", "  Orbin:", "    info:", "      properties:", *lines, "")), "utf-8")

    def add_framework(self, name: str, binary: bytes) -> Path:
        framework = self.app / "Frameworks" / f"{name}.framework"
        framework.mkdir(parents=True)
        (framework / "Info.plist").write_bytes(
            plistlib.dumps({"CFBundleExecutable": name, "CFBundlePackageType": "FMWK"})
        )
        (framework / name).write_bytes(binary)
        return framework

    def assert_rejected_without_writes(self, message: str, spec: Path | None = None) -> None:
        before = self.info_path.read_bytes()
        with self.assertRaisesRegex(metadata.MetadataError, message):
            purpose.prepare_archive(self.archive, spec)
        self.assertEqual(self.info_path.read_bytes(), before)
        self.assertEqual(list(self.app.glob(".orbin-Info-*")), [])

    def test_missing_string_fails_validation_and_names_the_referencing_framework(self) -> None:
        self.assert_rejected_without_writes(
            f"{KEY} is missing .* required by Frameworks/ffmpegkit.framework/ffmpegkit"
        )

    def test_missing_string_is_filled_from_the_spec_preserving_everything_else(self) -> None:
        self.info_path.chmod(0o640)
        binary = (self.ffmpeg / "ffmpegkit").read_bytes()
        report = purpose.prepare_archive(self.archive, self.spec)
        self.assertEqual(plistlib.loads(self.info_path.read_bytes()), self.info | {KEY: TEXT})
        self.assertTrue(self.info_path.read_bytes().startswith(b"bplist00"))
        self.assertEqual(stat.S_IMODE(self.info_path.stat().st_mode), 0o640)
        self.assertEqual((self.ffmpeg / "ffmpegkit").read_bytes(), binary)
        self.assertEqual(report["filledFromSpec"], [KEY])
        self.assertEqual(report["requiredBy"], {KEY: ["Frameworks/ffmpegkit.framework/ffmpegkit"]})
        self.assertTrue(report["changed"])
        self.assertEqual(list(self.app.glob(".orbin-Info-*")), [])

    def test_xml_plist_format_is_preserved(self) -> None:
        self.write_info(self.info, plistlib.FMT_XML)
        purpose.prepare_archive(self.archive, self.spec)
        self.assertTrue(self.info_path.read_bytes().startswith(b"<?xml"))

    def test_existing_string_is_kept_and_the_file_is_not_rewritten(self) -> None:
        self.write_info(self.info | {KEY: "The archive's own wording."})
        before = self.info_path.read_bytes()
        for spec in (None, self.spec):
            with self.subTest(spec=spec), mock.patch.object(
                metadata.os, "replace", side_effect=AssertionError("Unexpected write")
            ):
                report = purpose.prepare_archive(self.archive, spec)
                self.assertFalse(report["changed"])
                self.assertEqual(report["filledFromSpec"], [])
                self.assertEqual(self.info_path.read_bytes(), before)

    def test_second_run_is_a_noop_after_a_fill(self) -> None:
        purpose.prepare_archive(self.archive, self.spec)
        before = self.info_path.stat()
        self.assertFalse(purpose.prepare_archive(self.archive, self.spec)["changed"])
        self.assertFalse(purpose.prepare_archive(self.archive)["changed"])
        self.assertEqual(self.info_path.stat().st_mtime_ns, before.st_mtime_ns)

    def test_blank_or_non_string_existing_values_are_rejected(self) -> None:
        for value in ("", "   ", 1, True, ["text"]):
            with self.subTest(value=value):
                self.write_info(self.info | {KEY: value})
                self.assert_rejected_without_writes("must be a non-empty string", self.spec)

    def test_nothing_is_required_or_written_without_capture_references(self) -> None:
        (self.ffmpeg / "ffmpegkit").write_bytes(PLAYER)
        before = self.info_path.read_bytes()
        report = purpose.prepare_archive(self.archive, self.spec)
        self.assertEqual(report["requiredBy"], {})
        self.assertFalse(report["changed"])
        self.assertEqual(self.info_path.read_bytes(), before)

    def test_every_capture_symbol_in_any_executable_requires_the_string(self) -> None:
        (self.ffmpeg / "ffmpegkit").write_bytes(PLAYER)
        for symbol in purpose.REQUIRED_BY[KEY]:
            with self.subTest(symbol=symbol, where="app"):
                (self.app / "Orbin").write_bytes(b"\0" + symbol + b"\0")
                self.assert_rejected_without_writes(f"{KEY} is missing .* required by Orbin;")
        (self.app / "Orbin").write_bytes(PLAYER)
        self.add_framework("Another", CAMERA)
        self.assert_rejected_without_writes("required by Frameworks/Another.framework/Another;")

    def test_app_without_frameworks_directory_is_checked(self) -> None:
        (self.ffmpeg / "ffmpegkit").unlink()
        (self.ffmpeg / "Info.plist").unlink()
        self.ffmpeg.rmdir()
        self.ffmpeg.parent.rmdir()
        self.assertEqual(purpose.prepare_archive(self.archive)["requiredBy"], {})
        (self.app / "Orbin").write_bytes(CAMERA)
        self.assert_rejected_without_writes("required by Orbin;")

    def test_spec_without_the_string_cannot_fill_it(self) -> None:
        self.write_spec("        NSFaceIDUsageDescription: Unlock", f"        # {KEY}: commented out")
        self.assert_rejected_without_writes(f"{KEY} is missing", self.spec)

    def test_ambiguous_or_non_plain_spec_values_are_rejected(self) -> None:
        for value in ("", '"quoted"', "'quoted'", "|", ">-", "&anchor text", "*alias", "key: value", "text # note",
                      "[list]", "{map: 1}", "- item", "ends with colon:"):
            with self.subTest(value=value):
                self.write_spec(f"        {KEY}: {value}")
                self.assert_rejected_without_writes("plain single-line string", self.spec)
        self.write_spec(f"        {KEY}: {TEXT}", f"        {KEY}: {TEXT}")
        self.assert_rejected_without_writes("more than once", self.spec)

    def test_missing_spec_file_is_an_error_without_writes(self) -> None:
        before = self.info_path.read_bytes()
        with self.assertRaises(OSError):
            purpose.prepare_archive(self.archive, self.root / "absent.yml")
        self.assertEqual(self.info_path.read_bytes(), before)

    def test_invalid_app_or_framework_bundles_are_rejected(self) -> None:
        for value in (
            [],
            self.info | {"CFBundleExecutable": "../Orbin"},
            self.info | {"CFBundleExecutable": 7},
            self.info | {"CFBundleExecutable": "Missing"},
            self.info | {"CFBundlePackageType": "FMWK"},
        ):
            with self.subTest(value=value):
                self.write_info(value)
                with self.assertRaises(metadata.MetadataError):
                    purpose.prepare_archive(self.archive, self.spec)
        self.info_path.write_bytes(b"not a plist")
        with self.assertRaises(metadata.MetadataError):
            purpose.prepare_archive(self.archive, self.spec)
        self.write_info(self.info)
        (self.ffmpeg / "Info.plist").unlink()
        self.assert_rejected_without_writes("Info.plist", self.spec)

    def test_symlinked_paths_are_rejected(self) -> None:
        outside = self.root / "outside"
        (self.app / "Orbin").rename(outside)
        (self.app / "Orbin").symlink_to(outside)
        self.assert_rejected_without_writes("regular, non-symlink Orbin", self.spec)
        (self.app / "Orbin").unlink()
        outside.rename(self.app / "Orbin")

        moved = self.root / "moved.framework"
        self.ffmpeg.rename(moved)
        self.ffmpeg.symlink_to(moved, target_is_directory=True)
        self.assert_rejected_without_writes("real framework directory", self.spec)
        self.ffmpeg.unlink()
        moved.rename(self.ffmpeg)

        frameworks = self.app / "Frameworks"
        frameworks.rename(self.root / "frameworks")
        frameworks.symlink_to(self.root / "frameworks", target_is_directory=True)
        self.assert_rejected_without_writes("real Frameworks directory", self.spec)

        link = self.root / "archive-link"
        link.symlink_to(self.archive, target_is_directory=True)
        with self.assertRaises(metadata.MetadataError):
            purpose.prepare_archive(link, self.spec)

    def test_failed_atomic_replace_leaves_the_original_and_no_temporary_file(self) -> None:
        before = self.info_path.read_bytes()
        with mock.patch.object(metadata.os, "replace", side_effect=OSError("disk unavailable")):
            with self.assertRaisesRegex(OSError, "disk unavailable"):
                purpose.prepare_archive(self.archive, self.spec)
        self.assertEqual(self.info_path.read_bytes(), before)
        self.assertEqual(list(self.app.glob(".orbin-Info-*")), [])

    def test_project_spec_declares_every_guarded_purpose_string(self) -> None:
        for key in purpose.REQUIRED_BY:
            with self.subTest(key=key):
                value = purpose.spec_purpose_string(PROJECT_SPEC, key)
                self.assertIsNotNone(value)
                self.assertGreater(len(value.strip()), 20)

    def test_project_spec_fills_an_archive_of_a_release_that_predates_the_string(self) -> None:
        report = purpose.prepare_archive(self.archive, PROJECT_SPEC)
        self.assertEqual(report["filledFromSpec"], [KEY])
        self.assertEqual(
            plistlib.loads(self.info_path.read_bytes())[KEY], purpose.spec_purpose_string(PROJECT_SPEC, KEY)
        )

    def test_cli_validates_fills_and_reports_failure_status(self) -> None:
        script = SCRIPTS_DIR / "prepare_ios_purpose_strings.py"
        run = lambda *args: subprocess.run([sys.executable, str(script), *args], capture_output=True, text=True)
        failure = run(str(self.archive))
        self.assertNotEqual(failure.returncode, 0)
        self.assertIn(f"{KEY} is missing", failure.stderr)
        self.assertEqual(failure.stdout, "")
        filled = run("--spec", str(self.spec), str(self.archive))
        self.assertEqual(filled.returncode, 0, filled.stderr)
        self.assertIn(f'"filledFromSpec": ["{KEY}"]', filled.stdout)
        self.assertEqual(run(str(self.archive)).returncode, 0)
        self.assertNotEqual(run("--spec", str(self.root / "absent.yml"), str(self.root / "x")).returncode, 0)


if __name__ == "__main__":
    unittest.main()
