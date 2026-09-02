#!/usr/bin/env python3
"""Release preparation driven by a manifest.

One release is described by ``release/next.toml``; this script is the single
implementation of what that description means for the repository. It replaces the
per-release ``cut-<number>-<codename>.yml`` workflows, each of which carried its own
copy of the same edits.

Commands
--------
plan     Validate the manifest and emit ``KEY=value`` lines for ``$GITHUB_OUTPUT``.
prepare  Apply the release edits to gradle.properties, CHANGELOG.md, README.md and
         docs/wiki/Home.md. Idempotent: re-running on an already-prepared tree is a
         no-op rather than a second changelog section.
verify   Assert the working tree already carries exactly what the manifest describes.
         Used after the release PR merges, before the signed build is dispatched.

Manifest format::

    number       = 121
    codename     = "Yuki"
    version_code = 139
    summary      = ["what the release PR body should say", ...]

    [changelog]
    Fixed = ["one sentence per entry", ...]

Every field is required except ``summary``, which is derived when omitted.
"""

from __future__ import annotations

import argparse
import os
import re
import sys
import tomllib
from datetime import date
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
MANIFEST = ROOT / "release" / "next.toml"
BASE_URL = "https://github.com/Defuuls/Orbin"

# A codename becomes a git ref, a changelog heading and an APK name, so it is held to
# what all three accept: a letter, then letters, digits, spaces, hyphens or apostrophes
# (v39-Barnards-Star and Van Maanen's Star are both in the release history).
CODENAME = re.compile(r"^[A-Za-z][A-Za-z0-9 '-]*$")


class ManifestError(Exception):
    """The manifest is absent, malformed, or disagrees with the repository."""


class Release:
    """A validated release description, with the names derived from it."""

    def __init__(self, number: int, codename: str, version_code: int, summary: list[str]):
        self.number = number
        self.codename = codename
        self.version_code = version_code
        self.summary = summary

    @property
    def version_name(self) -> str:
        return f"{self.number}-{self.codename.replace(' ', '-')}"

    @property
    def tag(self) -> str:
        return f"v{self.version_name}"

    @property
    def branch(self) -> str:
        return f"release/prep-{self.tag.lower()}"

    @property
    def title(self) -> str:
        return f"Orbin {self.number} - {self.codename}"


def _require(data: dict, key: str, kind: type):
    if key not in data:
        raise ManifestError(f"{MANIFEST.name} is missing required key '{key}'")
    value = data[key]
    # bool is a subclass of int; a `true` where a number belongs is a mistake, not a 1.
    if not isinstance(value, kind) or isinstance(value, bool) != (kind is bool):
        raise ManifestError(f"{MANIFEST.name}: '{key}' must be {kind.__name__}, got {value!r}")
    return value


def load_manifest() -> tuple[Release, dict[str, list[str]]]:
    if not MANIFEST.exists():
        raise ManifestError(f"no manifest at {MANIFEST.relative_to(ROOT)}")
    try:
        data = tomllib.loads(MANIFEST.read_text(encoding="utf-8"))
    except tomllib.TOMLDecodeError as error:
        raise ManifestError(f"{MANIFEST.name} is not valid TOML: {error}") from error

    number = _require(data, "number", int)
    codename = " ".join(_require(data, "codename", str).split())
    version_code = _require(data, "version_code", int)

    if number <= 0:
        raise ManifestError(f"'number' must be positive, got {number}")
    if not CODENAME.match(codename):
        raise ManifestError(f"'codename' must be a readable name, got {codename!r}")

    changelog = _require(data, "changelog", dict)
    sections: dict[str, list[str]] = {}
    for heading, entries in changelog.items():
        if not isinstance(entries, list) or not entries:
            raise ManifestError(f"changelog section '{heading}' must be a non-empty array")
        if not all(isinstance(entry, str) and entry.strip() for entry in entries):
            raise ManifestError(f"changelog section '{heading}' has a non-string or empty entry")
        sections[heading] = [" ".join(entry.split()) for entry in entries]
    if not sections:
        raise ManifestError("'changelog' must describe at least one section")

    summary = data.get("summary", [])
    if not isinstance(summary, list) or not all(isinstance(line, str) for line in summary):
        raise ManifestError("'summary' must be an array of strings")

    release = Release(number, codename, version_code, [line.strip() for line in summary if line.strip()])
    if not release.summary:
        release.summary = [
            f"bump Orbin to {release.version_name} / versionCode {release.version_code}",
            f"close the [Unreleased] changelog section as {release.version_name}",
            "update the current-release pointers in README.md and docs/wiki/Home.md",
        ]
    return release, sections


def current_version() -> tuple[int, str]:
    text = (ROOT / "gradle.properties").read_text(encoding="utf-8")
    code = re.search(r"^orbin\.versionCode=(\d+)$", text, re.M)
    name = re.search(r"^orbin\.versionName=(.+)$", text, re.M)
    if not code or not name:
        raise ManifestError("gradle.properties is missing orbin.versionCode/orbin.versionName")
    return int(code.group(1)), name.group(1).strip()


def _previous_released_version(changelog: str) -> str:
    match = re.search(r"^## \[([^\]]+)\] - ", changelog, flags=re.M)
    if not match:
        raise ManifestError("CHANGELOG.md has no previously released version to compare against")
    return match.group(1).replace(" ", "-")


def _write(rel: str, text: str) -> None:
    (ROOT / rel).write_text(text, encoding="utf-8")


def _read(rel: str) -> str:
    return (ROOT / rel).read_text(encoding="utf-8")


def apply(release: Release, sections: dict[str, list[str]]) -> None:
    """Apply every edit a release needs. Safe to re-run on a prepared tree."""
    today = date.today().isoformat()

    text = _read("gradle.properties")
    text = re.sub(r"^orbin\.versionCode=\d+$", f"orbin.versionCode={release.version_code}", text, count=1, flags=re.M)
    text = re.sub(r"^orbin\.versionName=.+$", f"orbin.versionName={release.version_name}", text, count=1, flags=re.M)
    _write("gradle.properties", text)

    changelog = _read("CHANGELOG.md")
    if f"## [{release.version_name}]" not in changelog:
        previous = _previous_released_version(changelog)
        body = "".join(
            f"### {heading}\n" + "".join(f"- {entry}\n" for entry in entries) + "\n"
            for heading, entries in sections.items()
        )
        changelog = changelog.replace(
            "## [Unreleased]\n",
            f"## [Unreleased]\n\n## [{release.version_name}] - {today}\n\n{body}",
            1,
        )
        changelog = re.sub(
            r"^\[Unreleased\]: .*$",
            f"[Unreleased]: {BASE_URL}/compare/{release.tag}...HEAD\n"
            f"[{release.version_name}]: {BASE_URL}/compare/v{previous}...{release.tag}",
            changelog,
            count=1,
            flags=re.M,
        )
        _write("CHANGELOG.md", changelog)

    _write("README.md", re.sub(
        r"\*\*Current release:\*\* \[[^\]]+\]\([^)]+\)",
        f"**Current release:** [{release.number} — {release.codename}]({BASE_URL}/releases/tag/{release.tag})",
        _read("README.md"),
        count=1,
    ))

    _write("docs/wiki/Home.md", re.sub(
        r"\| Current release \| \*\*[^*]+\*\* \([0-9-]+\) \|",
        f"| Current release | **v{release.number} — {release.codename}** ({today}) |",
        _read("docs/wiki/Home.md"),
        count=1,
    ))


def command_plan(release: Release, _sections: dict[str, list[str]]) -> int:
    _, version_name = current_version()
    outputs = {
        "number": str(release.number),
        "codename": release.codename,
        "version_name": release.version_name,
        "version_code": str(release.version_code),
        "tag": release.tag,
        "branch": release.branch,
        "title": release.title,
        "prepared": "true" if version_name == release.version_name else "false",
    }
    destination = os.environ.get("GITHUB_OUTPUT")
    stream = open(destination, "a", encoding="utf-8") if destination else sys.stdout
    try:
        for key, value in outputs.items():
            print(f"{key}={value}", file=stream)
    finally:
        if destination:
            stream.close()
    for key, value in outputs.items():
        print(f"{key}={value}", file=sys.stderr)
    return 0


def command_prepare(release: Release, sections: dict[str, list[str]]) -> int:
    version_code, version_name = current_version()
    if version_name != release.version_name and release.version_code <= version_code:
        raise ManifestError(
            f"'version_code' must be greater than the current {version_code}, got {release.version_code}"
        )
    apply(release, sections)
    print(f"prepared {release.tag} (versionCode {release.version_code})")
    return 0


def command_verify(release: Release, _sections: dict[str, list[str]]) -> int:
    version_code, version_name = current_version()
    problems = []
    if version_name != release.version_name:
        problems.append(f"gradle.properties says versionName={version_name}, manifest says {release.version_name}")
    if version_code != release.version_code:
        problems.append(f"gradle.properties says versionCode={version_code}, manifest says {release.version_code}")
    if not re.search(rf"^## \[{re.escape(release.version_name)}\] - ", _read("CHANGELOG.md"), flags=re.M):
        problems.append(f"CHANGELOG.md has no dated '## [{release.version_name}]' heading")
    if problems:
        raise ManifestError("; ".join(problems))
    print(f"verified {release.tag} is fully prepared")
    return 0


def command_summary(release: Release, _sections: dict[str, list[str]]) -> int:
    print("## Summary")
    for line in release.summary:
        print(f"- {line}")
    print()
    print(f"After this PR merges, the release cutter dispatches the signed {release.tag} build.")
    return 0


COMMANDS = {
    "plan": command_plan,
    "prepare": command_prepare,
    "verify": command_verify,
    "summary": command_summary,
}


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("command", choices=sorted(COMMANDS))
    args = parser.parse_args()
    try:
        release, sections = load_manifest()
        return COMMANDS[args.command](release, sections)
    except ManifestError as error:
        print(f"::error::{error}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())
