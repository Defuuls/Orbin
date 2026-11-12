"""Bind six real collector results to immutable source before official profile merging."""

import copy
from contextlib import redirect_stderr, redirect_stdout
import io
from datetime import datetime, timezone
import json
import os
from pathlib import Path
import sys
import tempfile
import time
import unittest
import xml.etree.ElementTree as ET

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from profile_artifacts import CASES, CLASS, ProfileError, aggregate, export_case, main, verify_generated

STARTUP = (
    "Lcom/orbin/app/MainActivity;\n"
    "SPLcom/orbin/app/MainActivity;->onCreate()V\n"
    "Lcom/orbin/startup/Only;\n"
)
PROFILES = {
    "startup": STARTUP,
    "catalogList": "Lcom/orbin/uinext/BoardCatalogKt;\nSPLcom/orbin/uinext/BoardCatalogKt;->render()V\n",
    "catalogGrid": "HPLcom/orbin/uinext/BoardCatalogKt;->render()V\n",
    "thread": "Lcom/orbin/uinext/ThreadKt;\nSPLcom/orbin/uinext/ThreadKt;->render([ILjava/lang/Object;)V\n",
    "files": "Lcom/orbin/core/reader/ThreadPresentationKt;\n",
    "twoPane": "Lcom/orbin/uinext/ReaderTwoPaneKt;\n",
}
# Explicitly expected official selection: HP is lexically first; flags are not unioned into HSP.
BASELINE = STARTUP + (
    "Lcom/orbin/uinext/BoardCatalogKt;\n"
    "HPLcom/orbin/uinext/BoardCatalogKt;->render()V\n"
    "Lcom/orbin/uinext/ThreadKt;\n"
    "SPLcom/orbin/uinext/ThreadKt;->render([ILjava/lang/Object;)V\n"
    "Lcom/orbin/core/reader/ThreadPresentationKt;\n"
    "Lcom/orbin/uinext/ReaderTwoPaneKt;\n"
)


class ProfileArtifactsTest(unittest.TestCase):
    def setUp(self):
        directory = tempfile.TemporaryDirectory()
        self.addCleanup(directory.cleanup)
        self.root = Path(directory.name)
        self.artifacts = self.root / "artifacts"
        self.artifacts.mkdir()
        self.started = time.time() - 10
        self.stamp = datetime.fromtimestamp(self.started + 5, timezone.utc).strftime("%Y-%m-%d-%H-%M-%S")
        self.profile_values = dict(PROFILES)
        self.metadata = {"head_sha": "a" * 40, "checkout_sha": "b" * 40, "tree_sha": "c" * 40, "run_id": "123"}
        self.producer = self.root / "aggregate-producer"
        self.aggregate_manifest = self.root / "aggregate.json"
        self.generated = self.root / "generated"
        self.generated.mkdir()
        self.final_manifest = self.root / "verified.json"

    def case_dir(self, case):
        return self.artifacts / f"baseline-profile-case-{case}"

    def prepare(self, case):
        raw = self.root / "inputs" / case / "producer"
        xml = self.root / "inputs" / case / "reports"
        raw.mkdir(parents=True)
        xml.mkdir()
        kind = "startup" if case == "startup" else "baseline"
        profile = raw / f"BaselineProfileGenerator_{case}-{kind}-prof-{self.stamp}.txt"
        profile.write_text(self.profile_values[case])
        self.write_xml(xml / "TEST-reader.xml", case)
        return raw, xml, profile

    def write_xml(self, path, case, *, failure=None, skipped=False, classname=CLASS, extra=None):
        suite = ET.Element("testsuite", tests="2" if extra else "1", failures="0", errors="0", skipped="0",
                           timestamp=datetime.now(timezone.utc).isoformat())
        actual = ET.SubElement(suite, "testcase", name=case, classname=classname)
        if failure:
            ET.SubElement(actual, failure)
        if skipped:
            ET.SubElement(actual, "skipped")
        if extra:
            ET.SubElement(suite, "testcase", name=extra, classname=CLASS)
        ET.ElementTree(suite).write(path)

    def export(self, case, *, attempt=1):
        raw, xml, _ = self.prepare(case)
        return export_case(case=case, producer_dir=raw, xml_dir=xml, output_dir=self.case_dir(case),
                           started_at=self.started, attempt=attempt, **self.metadata)

    def export_all(self):
        for case in CASES:
            self.export(case, attempt=2 if case == "files" else 1)

    def aggregate(self, **changes):
        return aggregate(artifacts_dir=self.artifacts, producer_dir=self.producer,
                         manifest=self.aggregate_manifest, **{**self.metadata, **changes})

    def generate(self):
        (self.generated / "baseline-prof.txt").write_text(BASELINE)
        (self.generated / "startup-prof.txt").write_text(STARTUP)

    def verify(self):
        return verify_generated(aggregate_manifest=self.aggregate_manifest, producer_dir=self.producer,
                                generated_dir=self.generated, manifest=self.final_manifest)

    def edit_case(self, case, edit):
        path = self.case_dir(case) / "manifest.json"
        record = json.loads(path.read_text())
        edit(record)
        path.write_text(json.dumps(record))

    def test_six_original_profiles_are_copied_verbatim_and_official_outputs_verified(self):
        self.export_all()
        result = self.aggregate()
        self.assertEqual(set(CASES), {item["case"] for item in result["cases"]})
        self.assertEqual(2, next(item for item in result["cases"] if item["case"] == "files")["attempt"])
        for case in CASES:
            raw = next((self.case_dir(case) / "profiles").iterdir())
            self.assertEqual(raw.read_bytes(), (self.producer / raw.name).read_bytes())
        self.generate()
        verified = self.verify()
        self.assertEqual(2, len(verified["generated_files"]))
        self.assertTrue(all(verified["reader_rule_counts"].values()))
        self.assertEqual(0, verified["fixture_rules"])

    def test_missing_and_duplicate_or_unknown_case_artifacts_fail_before_copy(self):
        self.export_all()
        (self.case_dir("files") / "manifest.json").unlink()
        with self.assertRaises(ProfileError):
            self.aggregate()
        self.assertFalse(self.producer.exists())
        (self.artifacts / "baseline-profile-case-files-copy").mkdir()
        with self.assertRaisesRegex(ProfileError, "Missing, duplicate or unknown"):
            self.aggregate()

    def test_case_manifest_cannot_impersonate_another_fixed_artifact(self):
        self.export_all()
        self.edit_case("files", lambda m: m.update(case="thread"))
        with self.assertRaisesRegex(ProfileError, "Wrong or duplicate"):
            self.aggregate()

    def test_source_checkout_tree_run_and_configuration_must_match(self):
        self.export_all()
        for change in ({"head_sha": "d" * 40}, {"checkout_sha": "e" * 40},
                       {"tree_sha": "f" * 40}, {"run_id": "999"}):
            with self.subTest(change=change), self.assertRaisesRegex(ProfileError, "Mismatched source"):
                self.aggregate(**change)
        for field, value in (("api_level", 34), ("emulator_build", "999"), ("gpu", "other")):
            original = json.loads((self.case_dir("thread") / "manifest.json").read_text())
            self.edit_case("thread", lambda m: m["config"].update({field: value}))
            with self.subTest(field=field), self.assertRaisesRegex(ProfileError, "Mismatched source"):
                self.aggregate()
            (self.case_dir("thread") / "manifest.json").write_text(json.dumps(original))

    def test_tampered_raw_or_xml_hash_is_rejected(self):
        self.export_all()
        for folder in ("profiles", "reports"):
            path = next((self.case_dir("files") / folder).iterdir())
            original = path.read_bytes()
            path.write_bytes(original + b"\n")
            with self.subTest(folder=folder), self.assertRaisesRegex(ProfileError, "Tampered"):
                self.aggregate()
            path.write_bytes(original)

    def test_failed_errored_skipped_wrong_class_wrong_case_and_extra_xml_rejected(self):
        raw, xml, _ = self.prepare("files")
        path = xml / "TEST-reader.xml"
        variants = ({"failure": "failure"}, {"failure": "error"}, {"skipped": True},
                    {"classname": "android-test"}, {"extra": "thread"})
        for variant in variants:
            self.write_xml(path, "files", **variant)
            with self.subTest(variant=variant), self.assertRaises(ProfileError):
                export_case(case="files", producer_dir=raw, xml_dir=xml, output_dir=self.case_dir("files"),
                            started_at=self.started, attempt=1, **self.metadata)
        self.write_xml(path, "thread")
        with self.assertRaisesRegex(ProfileError, "Wrong selected"):
            export_case(case="files", producer_dir=raw, xml_dir=xml, output_dir=self.case_dir("files"),
                        started_at=self.started, attempt=1, **self.metadata)

    def test_missing_and_duplicate_xml_reports_are_not_accepted(self):
        raw, xml, _ = self.prepare("startup")
        actual = xml / "TEST-reader.xml"
        data = actual.read_bytes()
        actual.unlink()
        for duplicate in (False, True):
            if duplicate:
                actual.write_bytes(data)
                (xml / "TEST-other.xml").write_bytes(data)
            with self.subTest(duplicate=duplicate), self.assertRaisesRegex(ProfileError, "exactly one instrumentation"):
                export_case(case="startup", producer_dir=raw, xml_dir=xml, output_dir=self.case_dir("startup"),
                            started_at=self.started, attempt=1, **self.metadata)

    def test_wrong_case_kind_or_stale_raw_filename_is_rejected(self):
        raw, xml, profile = self.prepare("files")
        original = profile.name
        variants = (original.replace("files", "thread"), original.replace("baseline-prof", "startup-prof"),
                    original.replace(self.stamp, "2000-01-01-00-00-00"),
                    original.replace(self.stamp, "2099-01-01-00-00-00"), "baseline-prof.txt")
        for name in variants:
            moved = profile.rename(raw / name)
            with self.subTest(name=name), self.assertRaises(ProfileError):
                export_case(case="files", producer_dir=raw, xml_dir=xml, output_dir=self.case_dir("files"),
                            started_at=self.started, attempt=1, **self.metadata)
            moved.rename(profile)

    def test_stale_mtime_and_stale_xml_timestamp_are_rejected(self):
        raw, xml, profile = self.prepare("files")
        os.utime(profile, (self.started - 10, self.started - 10))
        with self.assertRaisesRegex(ProfileError, "Stale producer"):
            export_case(case="files", producer_dir=raw, xml_dir=xml, output_dir=self.case_dir("files"),
                        started_at=self.started, attempt=1, **self.metadata)
        os.utime(profile, None)
        report = xml / "TEST-reader.xml"
        tree = ET.parse(report)
        tree.getroot().set("timestamp", "2000-01-01T00:00:00")
        tree.write(report)
        with self.assertRaisesRegex(ProfileError, "Stale instrumentation"):
            export_case(case="files", producer_dir=raw, xml_dir=xml, output_dir=self.case_dir("files"),
                        started_at=self.started, attempt=1, **self.metadata)

    def test_empty_fixture_and_malformed_rules_are_rejected(self):
        raw, xml, profile = self.prepare("files")
        for value in ("", "\n", "Lcom/orbin/app/benchmark/ReaderBenchmarkActivity;\n", "HLbad;->method(V)V\n",
                      "Lbad;->method()INVALID\n", "HHPLbad;->method()V\n", "not-a-profile\n"):
            profile.write_text(value)
            with self.subTest(value=value), self.assertRaises(ProfileError):
                export_case(case="files", producer_dir=raw, xml_dir=xml, output_dir=self.case_dir("files"),
                            started_at=self.started, attempt=1, **self.metadata)

    def test_path_traversal_symlinks_and_unlisted_files_are_rejected(self):
        self.export_all()
        original = json.loads((self.case_dir("files") / "manifest.json").read_text())
        self.edit_case("files", lambda m: m["profile"].update(path="../outside.txt"))
        with self.assertRaisesRegex(ProfileError, "Unsafe"):
            self.aggregate()
        (self.case_dir("files") / "manifest.json").write_text(json.dumps(original))
        path = next((self.case_dir("files") / "profiles").iterdir())
        outside = self.root / "outside.txt"
        path.rename(outside)
        path.symlink_to(outside)
        with self.assertRaisesRegex(ProfileError, "Symlink"):
            self.aggregate()
        path.unlink()
        path.write_bytes(outside.read_bytes())
        (self.case_dir("files") / "extra.txt").write_text("unexpected")
        with self.assertRaisesRegex(ProfileError, "Unexpected"):
            self.aggregate()

    def test_existing_stale_producer_output_is_not_silently_overwritten(self):
        self.export_all()
        self.producer.mkdir()
        (self.producer / "old.txt").write_text("old")
        with self.assertRaisesRegex(ProfileError, "Output must be empty"):
            self.aggregate()
        self.assertEqual("old", (self.producer / "old.txt").read_text())

    def test_startup_input_must_also_appear_in_baseline_output(self):
        self.export_all()
        self.aggregate()
        self.generate()
        path = self.generated / "baseline-prof.txt"
        path.write_text(BASELINE.replace("Lcom/orbin/startup/Only;\n", ""))
        with self.assertRaisesRegex(ProfileError, "Official baseline-prof"):
            self.verify()

    def test_reader_case_cannot_be_promoted_to_startup_output(self):
        self.export_all()
        self.aggregate()
        self.generate()
        (self.generated / "startup-prof.txt").write_text(STARTUP + "Lcom/orbin/uinext/ThreadKt;\n")
        with self.assertRaisesRegex(ProfileError, "Official startup-prof"):
            self.verify()

    def test_custom_flag_union_is_not_accepted_as_official_merge(self):
        self.export_all()
        self.aggregate()
        self.generate()
        path = self.generated / "baseline-prof.txt"
        path.write_text(BASELINE.replace("HPLcom/orbin/uinext/BoardCatalogKt;", "HSPLcom/orbin/uinext/BoardCatalogKt;"))
        with self.assertRaisesRegex(ProfileError, "Official baseline-prof"):
            self.verify()

    def test_changed_inputs_or_duplicate_aggregate_cases_are_rejected_after_merge(self):
        self.export_all()
        self.aggregate()
        self.generate()
        path = next(self.producer.iterdir())
        original = path.read_bytes()
        path.write_bytes(original + b"\n")
        with self.assertRaisesRegex(ProfileError, "inputs changed"):
            self.verify()
        path.write_bytes(original)
        record = json.loads(self.aggregate_manifest.read_text())
        record["cases"][0] = copy.deepcopy(record["cases"][1])
        self.aggregate_manifest.write_text(json.dumps(record))
        with self.assertRaisesRegex(ProfileError, "Missing or duplicate"):
            self.verify()

    def test_empty_generated_profiles_and_fixture_outputs_are_rejected(self):
        self.export_all()
        self.aggregate()
        self.generate()
        (self.generated / "startup-prof.txt").write_text("")
        with self.assertRaisesRegex(ProfileError, "Empty profile"):
            self.verify()
        self.generate()
        (self.generated / "baseline-prof.txt").write_text(BASELINE + "Lcom/orbin/app/benchmark/Fixture;\n")
        with self.assertRaisesRegex(ProfileError, "fixture"):
            self.verify()


    def test_nonempty_profiles_without_exercised_production_reader_are_rejected(self):
        self.profile_values["files"] = "Ljava/lang/Object;\n"
        self.export_all()
        self.aggregate()
        self.generate()
        (self.generated / "baseline-prof.txt").write_text(
            BASELINE.replace("Lcom/orbin/core/reader/ThreadPresentationKt;", "Ljava/lang/Object;"))
        with self.assertRaisesRegex(ProfileError, "Missing production reader"):
            self.verify()

    def test_profiles_without_main_activity_are_rejected(self):
        self.profile_values["startup"] = "Ljava/lang/Object;\n"
        self.export_all()
        self.aggregate()
        self.generate()
        (self.generated / "baseline-prof.txt").write_text(BASELINE.replace(STARTUP, "Ljava/lang/Object;\n"))
        (self.generated / "startup-prof.txt").write_text("Ljava/lang/Object;\n")
        with self.assertRaisesRegex(ProfileError, "Missing MainActivity"):
            self.verify()

    def test_workflow_cli_arguments_run_export_aggregate_and_verify_without_rewriting_inputs(self):
        common = [argument for key, value in self.metadata.items() for argument in ("--" + key.replace("_", "-"), value)]
        with redirect_stdout(io.StringIO()):
            for case in CASES:
                raw, xml, _ = self.prepare(case)
                self.assertEqual(0, main(["export", "--case", case, "--producer-dir", str(raw),
                                         "--xml-dir", str(xml), "--output-dir", str(self.case_dir(case)),
                                         "--started-at", str(self.started), "--attempt", "1", *common]))
            self.assertEqual(0, main(["aggregate", "--artifacts-dir", str(self.artifacts),
                                     "--producer-dir", str(self.producer), "--manifest", str(self.aggregate_manifest), *common]))
            self.generate()
            verify = ["verify-generated", "--aggregate-manifest", str(self.aggregate_manifest),
                      "--producer-dir", str(self.producer), "--generated-dir", str(self.generated),
                      "--manifest", str(self.final_manifest)]
            self.assertEqual(0, main(verify))
        (self.generated / "startup-prof.txt").write_text("changed")
        with redirect_stderr(io.StringIO()) as errors:
            self.assertEqual(1, main(verify))
        self.assertIn("validation failed", errors.getvalue())


if __name__ == "__main__":
    unittest.main()
