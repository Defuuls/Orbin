#!/usr/bin/env python3
"""Design-token literal check (redesign review, decision 3.1 A).

Colours, corner radii, text sizes and opacity tweaks belong in the token files under
`ui-next/src/commonMain/kotlin/com/orbin/uinext/tokens/`. This counts the literals written out
anywhere else in the shared UI and compares each file with `scripts/token_literal_baseline.json`,
today's violations. A file may only go down. Run with `--update` after removing literals to
lower the baseline; never raise it to make the check pass, use a token instead.
"""

from __future__ import annotations

import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
BASELINE = ROOT / "scripts" / "token_literal_baseline.json"
SCANNED = [ROOT / "ui-next" / "src" / "commonMain" / "kotlin"]
TOKEN_DIR = "ui-next/src/commonMain/kotlin/com/orbin/uinext/tokens/"
# Artwork, not UI chrome: the brand mark's gradient stops are fixed by docs/brand.md.
ALLOWLIST = {
    "ui-next/src/commonMain/kotlin/com/orbin/uinext/components/OrbinLogoMark.kt",
}

PATTERNS = {
    "color": re.compile(r"\bColor\(0x[0-9A-Fa-f]{6,8}\)"),
    "radius": re.compile(r"\bRoundedCornerShape\(\s*\d+(?:\.\d+)?\.dp\s*\)"),
    "textSize": re.compile(r"\b(?:fontSize|lineHeight)\s*=\s*\d+(?:\.\d+)?\.sp\b"),
    "opacity": re.compile(r"\.copy\(\s*alpha\s*=\s*\d*\.?\d+f\s*\)"),
}


def count_literals() -> dict[str, dict[str, int]]:
    result: dict[str, dict[str, int]] = {}
    for base in SCANNED:
        for path in sorted(base.rglob("*.kt")):
            rel = path.relative_to(ROOT).as_posix()
            if rel.startswith(TOKEN_DIR) or rel in ALLOWLIST:
                continue
            text = path.read_text(encoding="utf-8")
            counts = {kind: len(p.findall(text)) for kind, p in PATTERNS.items()}
            counts = {k: v for k, v in counts.items() if v}
            if counts:
                result[rel] = counts
    return result


def violations(current: dict[str, dict[str, int]], baseline: dict[str, dict[str, int]]) -> list[str]:
    problems = []
    for rel, counts in sorted(current.items()):
        allowed = baseline.get(rel, {})
        for kind, n in sorted(counts.items()):
            if n > allowed.get(kind, 0):
                problems.append(f"{rel}: {n} {kind} literal(s), baseline allows {allowed.get(kind, 0)}")
    return problems


def main(argv: list[str]) -> int:
    current = count_literals()
    if "--update" in argv:
        BASELINE.write_text(json.dumps(current, indent=2, sort_keys=True) + "\n", encoding="utf-8")
        print(f"Wrote {BASELINE.relative_to(ROOT)}")
        return 0
    baseline = json.loads(BASELINE.read_text(encoding="utf-8"))
    problems = violations(current, baseline)
    for problem in problems:
        print(problem)
    total = sum(sum(c.values()) for c in current.values())
    print(f"{total} token literals outside the token files; {len(problems)} over baseline")
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
