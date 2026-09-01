#!/usr/bin/env python3
"""Repository consistency checks.

Run: python3 scripts/validate_repo.py [--fix-links]
"""

from __future__ import annotations

import argparse
import re
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
BASE_URL = "https://github.com/Defuuls/Orbin"

failures: list[str] = []
notes: list[str] = []


def fail(check: str, message: str) -> None:
    failures.append(f"[{check}] {message}")


def git_tags() -> set[str]:
    try:
        out = subprocess.check_output(["git", "tag"], cwd=ROOT, text=True)
    except subprocess.CalledProcessError:
        return set()
    return set(out.split())


def read(rel: str) -> str:
    return (ROOT / rel).read_text(encoding="utf-8")


def gradle_version() -> tuple[str, str]:
    text = read("gradle.properties")
    code = re.search(r"^orbin\.versionCode=(\d+)$", text, re.M)
    name = re.search(r"^orbin\.versionName=(.+)$", text, re.M)
    if not code or not name:
        fail("version", "gradle.properties is missing orbin.versionCode/orbin.versionName")
        return ("", "")
    return (code.group(1), name.group(1).strip())


def tag_for(version: str, tags: set[str]) -> str | None:
    for candidate in (f"v{version}", "v" + version.replace(" ", "-")):
        if candidate in tags:
            return candidate
    return None


def check_version_consistency(version_name: str) -> None:
    number = version_name.split("-", 1)[0]
    codename = version_name.split("-", 1)[1] if "-" in version_name else ""

    readme = read("README.md")
    if f"releases/tag/v{version_name}" not in readme:
        fail("version", f"README.md current-release link does not point at v{version_name}")
    if codename and f"[{number} — {codename}]" not in readme:
        fail("version", f"README.md current-release label is not '{number} — {codename}'")

    home = read("docs/wiki/Home.md")
    if codename and f"**v{number} — {codename}**" not in home:
        fail("version", f"docs/wiki/Home.md current-release row is not 'v{number} — {codename}'")

    changelog = read("CHANGELOG.md")
    if not re.search(rf"^## \[{re.escape(version_name)}\] - \d{{4}}-\d{{2}}-\d{{2}}$", changelog, re.M):
        fail("version", f"CHANGELOG.md has no dated '## [{version_name}]' heading")
        return

    section = re.split(
        r"^## \[", changelog.split(f"## [{version_name}] - ", 1)[1], maxsplit=1, flags=re.M
    )[0]
    if not re.search(r"^- ", section, re.M):
        fail("version", f"CHANGELOG.md's '{version_name}' section has no entries")


def changelog_parts() -> tuple[list[str], dict[str, str], str]:
    text = read("CHANGELOG.md")
    headings = [h for h in re.findall(r"^## \[([^\]]+)\]", text, re.M) if h != "Unreleased"]
    refs = dict(re.findall(r"^\[([^\]]+)\]:\s*(.+?)\s*$", text, re.M))
    return headings, refs, text


def check_changelog_links(tags: set[str], fix: bool) -> None:
    headings, refs, text = changelog_parts()

    duplicates = {h for h in headings if headings.count(h) > 1}
    if duplicates:
        fail("changelog", f"duplicate version headings: {sorted(duplicates)}")

    missing = [h for h in headings if h not in refs]
    if missing and not fix:
        fail("changelog", f"{len(missing)} heading(s) with no link ref: {missing[:8]}")

    if not tags:
        notes.append("skipping tag-existence checks — no tags in this checkout")
        return

    pending = {f"v{headings[0]}", "v" + headings[0].replace(" ", "-")} if headings else set()
    unknown: list[str] = []
    for name, url in refs.items():
        compare = re.search(r"/compare/(.+?)\.\.\.(.+)$", url)
        release = re.search(r"/releases/tag/(.+)$", url)
        parts = list(compare.groups()) if compare else ([release.group(1)] if release else [])
        for part in parts:
            if part == "HEAD" or part in tags:
                continue
            if part in pending:
                notes.append(f"[{name}] points at {part}, not tagged yet (the release being prepared)")
                continue
            unknown.append(f"[{name}] -> {part}")
    if unknown:
        fail("changelog", f"link ref(s) pointing at non-existent tags: {unknown}")

    if fix and missing:
        _backfill_refs(headings, refs, text, tags)


def _backfill_refs(headings: list[str], refs: dict[str, str], text: str, tags: set[str]) -> None:
    lines = [f"[Unreleased]: {refs['Unreleased']}"] if "Unreleased" in refs else []
    for index, heading in enumerate(headings):
        if heading in refs:
            lines.append(f"[{heading}]: {refs[heading]}")
            continue
        current = tag_for(heading, tags)
        previous = tag_for(headings[index + 1], tags) if index + 1 < len(headings) else None
        if current and previous:
            url = f"{BASE_URL}/compare/{previous}...{current}"
        elif current:
            url = f"{BASE_URL}/releases/tag/{current}"
        else:
            continue
        lines.append(f"[{heading}]: {url}")

    body = text.rstrip("\n").split("\n")
    cut = len(body)
    ref_re = re.compile(r"^\[([^\]]+)\]:\s*(.+?)\s*$")
    while cut > 0 and (ref_re.match(body[cut - 1]) or body[cut - 1].strip() == ""):
        cut -= 1
    (ROOT / "CHANGELOG.md").write_text("\n".join(body[:cut] + [""] + lines) + "\n", encoding="utf-8")
    print(f"fixed: rewrote {len(lines)} CHANGELOG link refs")


def check_relative_links() -> None:
    link_re = re.compile(r"\[([^\]]*)\]\(([^)\s]+)(?:\s+\"[^\"]*\")?\)")
    tracked = subprocess.check_output(["git", "ls-files", "*.md"], cwd=ROOT, text=True).split()
    for rel in tracked:
        path = ROOT / rel
        for number, line in enumerate(path.read_text(encoding="utf-8", errors="replace").splitlines(), 1):
            for _, target in link_re.findall(line):
                if target.startswith(("http://", "https://", "mailto:", "#")):
                    continue
                target = target.split("#")[0]
                if target and not (path.parent / target).resolve().exists():
                    fail("links", f"{rel}:{number} -> {target} does not exist")


def check_release_tooling() -> None:
    workflow = read(".github/workflows/new-version.yml")
    code = "\n".join(line for line in workflow.splitlines() if not line.lstrip().startswith("#"))
    if "app/build.gradle.kts" in code:
        fail("release-tooling", "new-version.yml still acts on app/build.gradle.kts; the version lives in gradle.properties")
    for expected in ("orbin.versionCode", "orbin.versionName", "gradle.properties"):
        if expected not in code:
            fail("release-tooling", f"new-version.yml never references {expected}")
    for doc in ("README.md", "docs/wiki/Home.md"):
        if doc not in code:
            fail("release-tooling", f"new-version.yml does not update {doc}")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--fix-links", action="store_true", help="backfill missing CHANGELOG link refs")
    args = parser.parse_args()

    tags = git_tags()
    if not tags:
        notes.append("no git tags visible — run with full history for tag checks to mean anything")

    _, version_name = gradle_version()
    if version_name:
        check_version_consistency(version_name)
    check_changelog_links(tags, args.fix_links)
    check_relative_links()
    check_release_tooling()

    for note in notes:
        print(f"note: {note}")
    if failures:
        print(f"\n{len(failures)} problem(s):\n")
        for failure in failures:
            print(f"  {failure}")
        return 1
    print("\nall repository consistency checks passed")
    return 0


if __name__ == "__main__":
    sys.exit(main())
