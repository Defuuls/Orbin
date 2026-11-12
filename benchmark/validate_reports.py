#!/usr/bin/env python3
"""Require successful CI journeys and actual finite samples, without a timing threshold."""

from __future__ import annotations

import argparse
import json
import math
from pathlib import Path
import sys
import xml.etree.ElementTree as ET


BENCHMARK_CLASS = "com.orbin.benchmark.AppPerformanceBenchmark"
STARTUP_CASES = {"coldStartup", "warmStartup"}
FRAME_CASES = {
    "catalogListScrollFrames",
    "catalogGridScrollFrames",
    "threadScrollFrames",
    "filesScrollFrames",
    "twoPaneScrollRetentionFrames",
}
REQUIRED_CASES = STARTUP_CASES | FRAME_CASES
STARTUP_ITERATIONS = 10
FRAME_ITERATIONS = 5


class ReportError(ValueError):
    """A required journey, result, or frame sample is absent or invalid."""


def _require(condition: bool, message: str) -> None:
    if not condition:
        raise ReportError(message)


def _finite_sample(value: object, context: str, *, positive: bool) -> None:
    _require(
        isinstance(value, (int, float)) and not isinstance(value, bool),
        f"{context}: sample is not numeric: {value!r}",
    )
    _require(math.isfinite(value), f"{context}: sample is not finite: {value!r}")
    if positive:
        _require(value > 0, f"{context}: sample must be positive: {value!r}")


def _metric(record: dict, group: str, name: str) -> dict:
    value = record.get(group, {}).get(name)
    _require(isinstance(value, dict), f"{record['name']}: missing {group}.{name}")
    for key, summary in value.items():
        if key != "runs" and isinstance(summary, (int, float)):
            _finite_sample(summary, f"{record['name']}.{name}.{key}", positive=False)
    return value


def _validate_record(record: dict) -> None:
    name = record["name"]
    iterations = STARTUP_ITERATIONS if name in STARTUP_CASES else FRAME_ITERATIONS
    _require(record.get("repeatIterations") == iterations, f"{name}: expected {iterations} measured iterations")
    if name in STARTUP_CASES:
        runs = _metric(record, "metrics", "timeToInitialDisplayMs").get("runs")
        _require(isinstance(runs, list) and len(runs) == iterations, f"{name}: missing startup samples")
        for value in runs:
            _finite_sample(value, name, positive=True)
        return

    # API 35 supplies CPU duration and deadline overrun for every measured frame. Overrun can be
    # negative for an on-time frame; CPU duration must represent actual nonzero rendering work.
    for metric_name in ("frameDurationCpuMs", "frameOverrunMs"):
        runs = _metric(record, "sampledMetrics", metric_name).get("runs")
        _require(isinstance(runs, list) and len(runs) == iterations, f"{name}.{metric_name}: missing iteration samples")
        for iteration, samples in enumerate(runs):
            context = f"{name}.{metric_name}[{iteration}]"
            _require(isinstance(samples, list) and bool(samples), f"{context}: no measured frames")
            for value in samples:
                _finite_sample(value, context, positive=metric_name == "frameDurationCpuMs")


def validate_reports(result_dir: Path) -> dict[str, int]:
    reports = sorted(result_dir.rglob("*-benchmarkData.json"))
    _require(bool(reports), f"No benchmark JSON reports under {result_dir}")
    recorded = set()
    for report in reports:
        document = json.loads(report.read_text())
        records = document.get("benchmarks")
        _require(isinstance(records, list) and bool(records), f"{report}: no benchmark results")
        for record in records:
            if record.get("className") == BENCHMARK_CLASS and record.get("name") in REQUIRED_CASES:
                _validate_record(record)
                recorded.add(record["name"])
    _require(REQUIRED_CASES <= recorded, f"Missing benchmark metrics: {sorted(REQUIRED_CASES - recorded)}")

    xml_reports = sorted(result_dir.rglob("TEST-*.xml"))
    _require(bool(xml_reports), f"No instrumentation XML results under {result_dir}")
    passed = set()
    for report in xml_reports:
        root = ET.parse(report).getroot()
        for suite in root.iter("testsuite"):
            _require(int(suite.get("failures", "0")) == 0, f"{report}: instrumentation failures")
            _require(int(suite.get("errors", "0")) == 0, f"{report}: instrumentation errors")
        for case in root.iter("testcase"):
            if case.get("classname") == BENCHMARK_CLASS and case.get("name") in REQUIRED_CASES:
                _require(
                    not any(case.find(tag) is not None for tag in ("failure", "error", "skipped")),
                    f"{report}: {case.get('name')} failed or was skipped",
                )
                passed.add(case.get("name"))
    _require(REQUIRED_CASES <= passed, f"Missing successful instrumentation cases: {sorted(REQUIRED_CASES - passed)}")
    return {"required_cases": len(REQUIRED_CASES), "json_reports": len(reports), "xml_reports": len(xml_reports)}


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("result_dir", type=Path, nargs="?", default=Path("benchmark/build"))
    args = parser.parse_args()
    try:
        summary = validate_reports(args.result_dir)
    except (ValueError, OSError, ET.ParseError) as error:
        print(f"Benchmark report validation failed: {error}", file=sys.stderr)
        return 1
    print(f"Validated {summary['required_cases']} successful journeys with nonempty finite samples "
          f"in {summary['json_reports']} JSON / {summary['xml_reports']} XML reports.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
