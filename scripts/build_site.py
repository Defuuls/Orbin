#!/usr/bin/env python3
"""Build the GitHub Pages site from site/ into an output folder, filled from the repository.

The page in site/index.html is a template. This fills it from CHANGELOG.md, so the release it
names, its date and the "What's new" list are always the last releases actually shipped, and it
can't fall behind the app again:

    build_site.py --out _site

{{RELEASE_*}} name the newest dated changelog section; {{CHANGELOG}} lists the newest few as HTML.
The Pages workflow runs this on every change to the site, the changelog or a release.
"""

from __future__ import annotations

import argparse
import html
import re
import shutil
import sys
from datetime import datetime, timezone
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SITE = ROOT / "site"
CHANGELOG = ROOT / "CHANGELOG.md"
# The screenshots the release cut keeps current (prepare_release.py stamps the hero with the
# release); the site uses these rather than copies of its own that would go stale.
SCREENSHOTS = ROOT / "docs" / "assets"
RELEASES_SHOWN = 4

HEADING = re.compile(r"^## \[(?P<name>\d+)-(?P<codename>[^\]]+)\] - (?P<date>\d{4}-\d{2}-\d{2})\s*$")
LINK = re.compile(r"\[([^\]]+)\]\([^)]+\)")
CODE = re.compile(r"`([^`]+)`")
BOLD = re.compile(r"\*\*([^*]+)\*\*")


def releases(changelog: str) -> list[dict]:
    """The dated release sections, newest first: number, codename, date and their entries."""
    found: list[dict] = []
    current: dict | None = None
    heading: str | None = None
    for line in changelog.splitlines():
        match = HEADING.match(line)
        if match:
            current = {
                "number": match["name"],
                "codename": match["codename"].replace("-", " "),
                "tag": f"v{match['name']}-{match['codename']}",
                "date": match["date"],
                "sections": {},
            }
            found.append(current)
            heading = None
            continue
        if line.startswith("## "):
            current = None
            continue
        if current is None:
            continue
        if line.startswith("### "):
            heading = line[4:].strip()
            current["sections"].setdefault(heading, [])
        elif line.startswith("- ") and heading:
            current["sections"][heading].append(line[2:].strip())
        elif line.startswith("  ") and heading and current["sections"][heading]:
            current["sections"][heading][-1] += " " + line.strip()
    return found


def inline(markdown: str) -> str:
    """One changelog entry as safe HTML: links kept as text, `code` and **bold** kept."""
    text = html.escape(LINK.sub(r"\1", markdown), quote=False)
    text = CODE.sub(r"<code>\1</code>", text)
    return BOLD.sub(r"<strong>\1</strong>", text)


def changelog_html(found: list[dict]) -> str:
    parts = []
    for release in found[:RELEASES_SHOWN]:
        url = f"https://github.com/Defuuls/Orbin/releases/tag/{html.escape(release['tag'])}"
        body = "".join(
            f"<h4>{html.escape(name)}</h4><ul>" + "".join(f"<li>{inline(e)}</li>" for e in entries) + "</ul>"
            for name, entries in release["sections"].items()
            if entries
        )
        parts.append(
            f'<article class="release"><h3><a href="{url}">{html.escape(release["number"])} — '
            f'{html.escape(release["codename"])}</a><time datetime="{release["date"]}">{release["date"]}</time></h3>'
            f"{body}</article>"
        )
    return "\n    ".join(parts)


def build(out: Path) -> None:
    found = releases(CHANGELOG.read_text(encoding="utf-8"))
    if not found:
        sys.exit("CHANGELOG.md has no dated release section")
    latest = found[0]
    first = next((entries[0] for entries in latest["sections"].values() if entries), "")
    values = {
        "RELEASE_TAG": html.escape(latest["tag"]),
        "RELEASE_NUMBER": html.escape(latest["number"]),
        "RELEASE_CODENAME": html.escape(latest["codename"]),
        "RELEASE_CODE": html.escape(latest["number"]),
        "RELEASE_DATE": f" ({latest['date']})",
        "RELEASE_HIGHLIGHT": (
            f'<p class="note">New in {html.escape(latest["number"])}: <em>{inline(first)}</em></p>' if first else ""
        ),
        "CHANGELOG": changelog_html(found),
        "BUILT": datetime.now(timezone.utc).strftime("%Y-%m-%d"),
    }
    if out.exists():
        shutil.rmtree(out)
    shutil.copytree(SITE, out)
    (out / "assets").mkdir(exist_ok=True)
    for shot in SCREENSHOTS.glob("orbin-*-screenshot.svg"):
        shutil.copy2(shot, out / "assets" / shot.name)
    page = (SITE / "index.html").read_text(encoding="utf-8")
    for key, value in values.items():
        page = page.replace("{{" + key + "}}", value)
    left = re.findall(r"\{\{[A-Z_]+\}\}", page)
    if left:
        sys.exit(f"unfilled placeholders: {sorted(set(left))}")
    (out / "index.html").write_text(page, encoding="utf-8")
    print(f"built {out} for {latest['number']}-{latest['codename']} ({len(found[:RELEASES_SHOWN])} releases listed)")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--out", type=Path, default=ROOT / "_site")
    build(parser.parse_args().out)


if __name__ == "__main__":
    main()
