"""The report gate must reject missing/empty/nonfinite measurements, not just parse JSON."""

import copy
import json
from pathlib import Path
import sys
import tempfile
import unittest
import xml.etree.ElementTree as ET

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from validate_reports import BENCHMARK_CLASS, FRAME_CASES, REQUIRED_CASES, ReportError, validate_reports


def record(name):
    result = {"name": name, "className": BENCHMARK_CLASS}
    if name in FRAME_CASES:
        result.update({
            "repeatIterations": 5,
            "sampledMetrics": {
                "frameDurationCpuMs": {"P50": 1.0, "runs": [[1.0, 2.0] for _ in range(5)]},
                "frameOverrunMs": {"P50": -1.0, "runs": [[-1.0, 1.0] for _ in range(5)]},
            },
        })
    else:
        result.update({
            "repeatIterations": 10,
            "metrics": {"timeToInitialDisplayMs": {"median": 10.0, "runs": [10.0] * 10}},
        })
    return result


class ValidateReportsTest(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        self.root = Path(self.directory.name)
        self.records = [record(name) for name in sorted(REQUIRED_CASES)]
        self.write_reports()

    def write_reports(self, *, failed_case=None, skipped_case=None):
        (self.root / "app-benchmarkData.json").write_text(json.dumps({"benchmarks": self.records}))
        suite = ET.Element("testsuite", tests=str(len(REQUIRED_CASES)), failures="0", errors="0")
        for name in sorted(REQUIRED_CASES):
            case = ET.SubElement(suite, "testcase", name=name, classname=BENCHMARK_CLASS)
            if name == failed_case:
                ET.SubElement(case, "failure")
            if name == skipped_case:
                ET.SubElement(case, "skipped")
        ET.ElementTree(suite).write(self.root / "TEST-reader.xml")

    def test_accepts_all_seven_measured_cases_and_signed_frame_overruns(self):
        self.assertEqual(7, validate_reports(self.root)["required_cases"])

    def test_missing_case_fails(self):
        self.records.pop()
        self.write_reports()
        with self.assertRaisesRegex(ReportError, "Missing benchmark metrics"):
            validate_reports(self.root)

    def test_empty_frame_iteration_fails(self):
        frame = next(item for item in self.records if item["name"] in FRAME_CASES)
        frame["sampledMetrics"]["frameDurationCpuMs"]["runs"][0] = []
        self.write_reports()
        with self.assertRaisesRegex(ReportError, "no measured frames"):
            validate_reports(self.root)

    def test_nonfinite_and_nonnumeric_frame_samples_fail(self):
        original = copy.deepcopy(self.records)
        for value in (float("nan"), float("inf"), "NaN", True):
            with self.subTest(value=value):
                self.records = copy.deepcopy(original)
                frame = next(item for item in self.records if item["name"] in FRAME_CASES)
                frame["sampledMetrics"]["frameDurationCpuMs"]["runs"][0][0] = value
                self.write_reports()
                with self.assertRaises(ReportError):
                    validate_reports(self.root)

    def test_zero_duration_and_incomplete_iteration_counts_fail(self):
        frame = next(item for item in self.records if item["name"] in FRAME_CASES)
        frame["sampledMetrics"]["frameDurationCpuMs"]["runs"][0][0] = 0.0
        self.write_reports()
        with self.assertRaisesRegex(ReportError, "must be positive"):
            validate_reports(self.root)
        frame["repeatIterations"] = 1
        self.write_reports()
        with self.assertRaisesRegex(ReportError, "expected 5 measured iterations"):
            validate_reports(self.root)

    def test_missing_json_or_xml_results_fail(self):
        (self.root / "TEST-reader.xml").unlink()
        with self.assertRaisesRegex(ReportError, "No instrumentation XML"):
            validate_reports(self.root)
        self.write_reports()
        (self.root / "app-benchmarkData.json").unlink()
        with self.assertRaisesRegex(ReportError, "No benchmark JSON"):
            validate_reports(self.root)

    def test_failed_or_skipped_required_instrumentation_cases_fail(self):
        self.write_reports(failed_case="threadScrollFrames")
        with self.assertRaisesRegex(ReportError, "failed or was skipped"):
            validate_reports(self.root)
        self.write_reports(skipped_case="threadScrollFrames")
        with self.assertRaisesRegex(ReportError, "failed or was skipped"):
            validate_reports(self.root)


if __name__ == "__main__":
    unittest.main()
