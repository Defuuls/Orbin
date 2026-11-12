#!/usr/bin/env python3
"""Validate isolated collector evidence, restore raw inputs, and audit official profile outputs."""

from __future__ import annotations

import argparse
from datetime import datetime, timezone
import hashlib
import json
import math
from pathlib import Path, PurePosixPath
import re
import sys
import time
import xml.etree.ElementTree as ET

CASES = ("startup", "catalogList", "catalogGrid", "thread", "files", "twoPane")
CLASS = "com.orbin.benchmark.BaselineProfileGenerator"
FIXTURE = "Lcom/orbin/app/benchmark/"
READERS = (
    "Lcom/orbin/uinext/BoardCatalogKt;",
    "Lcom/orbin/uinext/ThreadKt;",
    "Lcom/orbin/uinext/ReaderTwoPaneKt;",
    "Lcom/orbin/core/reader/ThreadPresentationKt;",
)
MAIN = "Lcom/orbin/app/MainActivity;"
RAW_NAME = re.compile(
    r"BaselineProfileGenerator_(startup|catalogList|catalogGrid|thread|files|twoPane)"
    r"-(startup|baseline)-prof-(\d{4}-\d\d-\d\d-\d\d-\d\d-\d\d)\.txt"
)
RULE = re.compile(r"([HSP]*)(L[^;\s\[\]().]+;)(?:->([^\s();]+)\(([^)]*)\)(.+))?")
SCHEMA = 1


class ProfileError(ValueError):
    """Collector evidence or an official generated output failed validation."""


def require(condition: bool, message: str) -> None:
    if not condition:
        raise ProfileError(message)


def plain(path: Path, *, directory: bool = False) -> Path:
    require(not path.is_symlink(), f"Symlink is not allowed: {path}")
    require(path.is_dir() if directory else path.is_file(), f"Missing {'directory' if directory else 'file'}: {path}")
    return path


def files(root: Path) -> list[Path]:
    plain(root, directory=True)
    result = []
    for path in sorted(root.rglob("*")):
        require(not path.is_symlink(), f"Symlink is not allowed: {path}")
        if path.is_file():
            result.append(path)
    return result


def sha(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def entry(path: str, data: bytes, **extra: object) -> dict:
    return {"path": path, "bytes": len(data), "sha256": sha(data), **extra}


def number(value: object, label: str) -> int:
    require(not isinstance(value, bool) and str(value).isdigit() and int(value) > 0, f"Invalid {label}: {value}")
    return int(value)


def source(head_sha: str, checkout_sha: str, tree_sha: str) -> dict:
    result = {"head_sha": head_sha, "checkout_sha": checkout_sha, "tree_sha": tree_sha}
    for key, value in result.items():
        require(isinstance(value, str) and re.fullmatch(r"[0-9a-f]{40}", value) is not None, f"Invalid {key}")
    return result


def config(api_level: int, emulator_build: str) -> dict:
    require(api_level == 35 and str(emulator_build) == "16428233", "Expected API 35 and emulator build 16428233")
    return {"api_level": api_level, "emulator_build": str(emulator_build), "target": "google_apis", "arch": "x86_64", "gpu": "swiftshader"}


def _type_end(value: str, start: int, *, void: bool = False) -> int:
    require(start < len(value), "Missing descriptor type")
    array = False
    while start < len(value) and value[start] == "[":
        array = True
        start += 1
    require(start < len(value), "Missing array element descriptor")
    char = value[start]
    if char in "ZBSCIJFD" or (void and not array and char == "V"):
        return start + 1
    require(char == "L", f"Invalid descriptor type: {value}")
    end = value.find(";", start)
    require(end > start + 1 and re.fullmatch(r"L[^;\s\[\]().]+;", value[start:end + 1]) is not None, f"Invalid object descriptor: {value}")
    return end + 1


def rules(data: bytes) -> dict[tuple[str, str], list[str]]:
    try:
        lines = data.decode("utf-8").splitlines()
    except UnicodeError as error:
        raise ProfileError("Profile is not UTF-8") from error
    require(bool(lines), "Empty profile")
    result: dict[tuple[str, str], list[str]] = {}
    for line in lines:
        require(FIXTURE not in line, "Benchmark fixture rule is forbidden")
        match = RULE.fullmatch(line)
        require(match is not None, f"Malformed profile rule: {line[:140]}")
        flags, owner, method, params, returns = match.groups()
        require(flags == "".join(flag for flag in "HSP" if flag in flags), f"Invalid profile flags: {line[:140]}")
        if method is None:
            require(not flags, "Class-only profile rule must not have method flags")
            identity = (owner, "")
        else:
            index = 0
            while index < len(params):
                index = _type_end(params, index)
            require(_type_end(returns, 0, void=True) == len(returns), "Invalid method return descriptor")
            identity = (owner, f"{method}({params}){returns}")
        result.setdefault(identity, []).append(line)
    return result


def raw_name(name: str, case: str, started_at: float) -> None:
    match = RAW_NAME.fullmatch(name)
    require(match is not None and match.group(1) == case, f"Wrong or stale producer filename: {name}")
    require(match.group(2) == ("startup" if case == "startup" else "baseline"), f"Wrong profile kind: {name}")
    try:
        stamp = datetime.strptime(match.group(3), "%Y-%m-%d-%H-%M-%S").replace(tzinfo=timezone.utc).timestamp()
    except ValueError as error:
        raise ProfileError(f"Invalid producer timestamp: {name}") from error
    require(stamp >= math.floor(started_at), f"Stale producer timestamp: {name}")
    require(stamp <= time.time() + 60, f"Future producer timestamp: {name}")


def validate_xml(data: bytes, case: str, started_at: float) -> None:
    root = ET.fromstring(data)
    cases = list(root.iter("testcase"))
    require(len(cases) == 1, "Expected exactly one selected generator testcase; extra/missing executions")
    for suite in root.iter("testsuite"):
        for key in ("failures", "errors", "skipped"):
            require(int(suite.get(key, "0")) == 0, f"Instrumentation {key}")
        require(int(suite.get("tests", "1")) == 1, "Unexpected instrumentation testcase count")
        stamp = suite.get("timestamp")
        require(stamp is not None, "Missing instrumentation timestamp")
        parsed = datetime.fromisoformat(stamp.replace("Z", "+00:00"))
        if parsed.tzinfo is None:
            parsed = parsed.replace(tzinfo=timezone.utc)
        require(parsed.timestamp() >= math.floor(started_at), "Stale instrumentation XML")
    require(bool(list(root.iter("testsuite"))), "Missing instrumentation suite")
    actual = cases[0]
    require(actual.get("classname") == CLASS and actual.get("name") == case, "Wrong selected generator testcase")
    require(not any(actual.find(tag) is not None for tag in ("failure", "error", "skipped")), "Generator failed or skipped")


def empty_output(path: Path) -> None:
    require(not path.is_symlink(), f"Symlink output is forbidden: {path}")
    require(not path.exists() or (path.is_dir() and not list(path.iterdir())), f"Output must be empty: {path}")


def write_json(path: Path, document: dict) -> None:
    require(not path.is_symlink(), f"Symlink manifest is forbidden: {path}")
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(document, indent=2) + "\n")


def export_case(*, case: str, producer_dir: Path, xml_dir: Path, output_dir: Path, started_at: float,
                head_sha: str, checkout_sha: str, tree_sha: str, run_id: str, attempt: int,
                api_level: int = 35, emulator_build: str = "16428233") -> dict:
    require(case in CASES, "Unknown generator case")
    require(math.isfinite(started_at) and 0 < started_at <= time.time(), "Invalid collection start")
    metadata = {"source": source(head_sha, checkout_sha, tree_sha), "run_id": str(number(run_id, "run ID")),
                "attempt": number(attempt, "attempt"), "config": config(api_level, emulator_build)}
    profiles = files(producer_dir)
    require(len(profiles) == 1, "Expected exactly one fresh raw producer profile")
    profile = profiles[0]
    raw_name(profile.name, case, started_at)
    require(profile.parent == producer_dir, "Producer profile must be directly in the producer directory")
    require(profile.stat().st_mtime >= started_at, "Stale producer file mtime")
    data = profile.read_bytes()
    rules(data)
    xmls = [path for path in files(xml_dir) if path.suffix == ".xml"]
    require(len(xmls) == 1, "Expected exactly one instrumentation XML report")
    report = xmls[0]
    require(report.stat().st_mtime >= started_at, "Stale XML mtime")
    xml_data = report.read_bytes()
    validate_xml(xml_data, case, started_at)
    empty_output(output_dir)
    document = {"schema": SCHEMA, "kind": "profile-case", "case": case, **metadata, "started_at": started_at,
                "profile": entry(f"profiles/{profile.name}", data, producer_mtime=profile.stat().st_mtime),
                "xml": entry(f"reports/{report.name}", xml_data, producer_mtime=report.stat().st_mtime)}
    (output_dir / "profiles").mkdir(parents=True)
    (output_dir / "reports").mkdir()
    (output_dir / document["profile"]["path"]).write_bytes(data)
    (output_dir / document["xml"]["path"]).write_bytes(xml_data)
    write_json(output_dir / "manifest.json", document)
    return document


def read_entry(root: Path, record: dict, folder: str) -> tuple[str, bytes]:
    require(isinstance(record, dict), "Missing file record")
    relative = record.get("path")
    require(isinstance(relative, str) and "\\" not in relative, "Invalid artifact path")
    parts = PurePosixPath(relative).parts
    require(len(parts) == 2 and parts[0] == folder and parts[1] not in (".", "..")
            and relative == str(PurePosixPath(relative)), "Unsafe artifact path")
    plain(root / folder, directory=True)
    data = plain(root / relative).read_bytes()
    require(record.get("bytes") == len(data) and record.get("sha256") == sha(data), f"Tampered artifact file: {relative}")
    return parts[1], data


def aggregate(*, artifacts_dir: Path, producer_dir: Path, manifest: Path, head_sha: str,
              checkout_sha: str, tree_sha: str, run_id: str, api_level: int = 35,
              emulator_build: str = "16428233") -> dict:
    expected = {"source": source(head_sha, checkout_sha, tree_sha), "run_id": str(number(run_id, "run ID")),
                "config": config(api_level, emulator_build)}
    plain(artifacts_dir, directory=True)
    directories = sorted(artifacts_dir.iterdir())
    require({path.name for path in directories} == {f"baseline-profile-case-{case}" for case in CASES}, "Missing, duplicate or unknown case artifacts")
    result = {"schema": SCHEMA, "kind": "profile-aggregate", **expected, "cases": []}
    copies = {}
    for directory in directories:
        plain(directory, directory=True)
        manifest_data = plain(directory / "manifest.json").read_bytes()
        record = json.loads(manifest_data)
        case = record.get("case")
        require(case in CASES and directory.name == f"baseline-profile-case-{case}", "Wrong or duplicate case manifest")
        require(record.get("schema") == SCHEMA and record.get("kind") == "profile-case", "Invalid case manifest")
        require(all(record.get(key) == value for key, value in expected.items()), "Mismatched source, run or configuration")
        attempt = number(record.get("attempt"), "attempt")
        started = record.get("started_at")
        require(isinstance(started, (int, float)) and not isinstance(started, bool) and math.isfinite(started) and 0 < started <= time.time(), "Invalid case start")
        name, data = read_entry(directory, record.get("profile"), "profiles")
        raw_name(name, case, started)
        rules(data)
        _, xml_data = read_entry(directory, record.get("xml"), "reports")
        validate_xml(xml_data, case, started)
        for field in ("profile", "xml"):
            require(record[field].get("producer_mtime", 0) >= started, "Stale producer evidence")
        allowed = {directory / "manifest.json", directory / record["profile"]["path"], directory / record["xml"]["path"]}
        require(set(files(directory)) == allowed, "Unexpected or duplicate case artifact files")
        require(name not in copies, "Duplicate producer profile filename")
        copies[name] = data
        result["cases"].append({"case": case, "attempt": attempt, "started_at": started,
                                "case_manifest_sha256": sha(manifest_data), "xml": record["xml"],
                                "profile": entry(name, data)})
    empty_output(producer_dir)
    require(not manifest.resolve().is_relative_to(producer_dir.resolve()), "Manifest must remain outside producer directory")
    producer_dir.mkdir(parents=True, exist_ok=True)
    for name, data in copies.items():
        (producer_dir / name).write_bytes(data)
    write_json(manifest, result)
    return result


def selected_rules(inputs: list[bytes]) -> dict[tuple[str, str], str]:
    """Audit plugin 1.5.0 semantics; never write or combine flags into generated rules."""
    selected = {}
    for line in sorted(line for data in inputs for group in rules(data).values() for line in group):
        identity = next(iter(rules(line.encode("utf-8"))))
        selected.setdefault(identity, line)
    return selected


def verify_generated(*, aggregate_manifest: Path, producer_dir: Path, generated_dir: Path, manifest: Path) -> dict:
    record = json.loads(plain(aggregate_manifest).read_text())
    require(record.get("schema") == SCHEMA and record.get("kind") == "profile-aggregate", "Invalid aggregate manifest")
    source(**record["source"])
    require(record.get("config") == config(35, "16428233"), "Invalid aggregate configuration")
    number(record.get("run_id"), "run ID")
    cases = record.get("cases", [])
    require(len(cases) == 6 and {item.get("case") for item in cases} == set(CASES), "Missing or duplicate aggregate cases")
    inputs = []
    startup_inputs = []
    names = set()
    for item in cases:
        case = item["case"]
        metadata = item["profile"]
        name = metadata["path"]
        raw_name(name, case, item["started_at"])
        require(name not in names, "Duplicate raw profile")
        names.add(name)
        data = plain(producer_dir / name).read_bytes()
        require(metadata["sha256"] == sha(data) and metadata["bytes"] == len(data), "Producer inputs changed after aggregation")
        rules(data)
        inputs.append(data)
        if case == "startup":
            startup_inputs.append(data)
    require({path.name for path in files(producer_dir)} == names, "Stale or unexpected producer input")
    outputs = []
    counts = {}
    for filename, expected in (("baseline-prof.txt", selected_rules(inputs)),
                               ("startup-prof.txt", selected_rules(startup_inputs))):
        data = plain(generated_dir / filename).read_bytes()
        parsed = rules(data)
        require(all(len(group) == 1 for group in parsed.values()), "Duplicate generated rule identity")
        actual = {identity: group[0] for identity, group in parsed.items()}
        require(actual == expected, f"Official {filename} differs from sorted full-rule/first-identity inputs")
        require(any(owner == MAIN for owner, _ in actual), f"Missing MainActivity in {filename}")
        if filename == "baseline-prof.txt":
            counts = {owner: sum(identity[0] == owner for identity in actual) for owner in READERS}
            require(all(counts.values()), "Missing production reader rules")
        outputs.append(entry(filename, data, rules=len(actual)))
    result = {**record, "kind": "verified-profile", "aggregate_manifest_sha256": sha(aggregate_manifest.read_bytes()),
              "merge_validation": "sorted-full-rules-first-identity; startup-only-subset", "generated_files": outputs,
              "reader_rule_counts": counts, "fixture_rules": 0}
    write_json(manifest, result)
    return result


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest="command", required=True)
    export = commands.add_parser("export")
    export.add_argument("--case", choices=CASES, required=True)
    for key in ("producer-dir", "xml-dir", "output-dir"):
        export.add_argument("--" + key, type=Path, required=True)
    export.add_argument("--started-at", type=float, required=True)
    export.add_argument("--attempt", type=int, required=True)
    collect = commands.add_parser("aggregate")
    for key in ("artifacts-dir", "producer-dir", "manifest"):
        collect.add_argument("--" + key, type=Path, required=True)
    for command in (export, collect):
        for key in ("head-sha", "checkout-sha", "tree-sha", "run-id"):
            command.add_argument("--" + key, required=True)
        command.add_argument("--api-level", type=int, default=35)
        command.add_argument("--emulator-build", default="16428233")
    verify = commands.add_parser("verify-generated")
    for key in ("aggregate-manifest", "producer-dir", "generated-dir", "manifest"):
        verify.add_argument("--" + key, type=Path, required=True)
    arguments = vars(parser.parse_args(argv))
    command = arguments.pop("command")
    try:
        result = {"export": export_case, "aggregate": aggregate, "verify-generated": verify_generated}[command](**arguments)
    except (ValueError, OSError, KeyError, TypeError, ET.ParseError) as error:
        print(f"Profile artifact validation failed: {error}", file=sys.stderr)
        return 1
    print(f"Validated {result['kind']}: {result.get('case', 'all six generator cases')}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
