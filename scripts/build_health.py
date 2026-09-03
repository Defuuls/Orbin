#!/usr/bin/env python3
"""Print a compact module fan-out/source-size report for build-health reviews."""
from __future__ import annotations

import pathlib
import re

ROOT = pathlib.Path(__file__).resolve().parents[1]
PROJECT_DEP = re.compile(r'project\(\s*"(:[^"]+)"\s*\)')


def modules() -> list[str]:
    settings = (ROOT / "settings.gradle.kts").read_text(encoding="utf-8")
    return re.findall(r'include\("(:[^"]+)"\)', settings)


def module_dir(module: str) -> pathlib.Path:
    return ROOT.joinpath(*module.lstrip(":").split(":"))


def dependencies(module: str) -> set[str]:
    build = module_dir(module) / "build.gradle.kts"
    if not build.exists():
        return set()
    return set(PROJECT_DEP.findall(build.read_text(encoding="utf-8")))


def source_count(module: str) -> int:
    root = module_dir(module) / "src"
    return sum(1 for _ in root.rglob("*.kt")) if root.exists() else 0


def main() -> None:
    rows = [(module, len(dependencies(module)), source_count(module)) for module in modules()]
    rows.sort(key=lambda row: (-row[1], -row[2], row[0]))

    print("## Orbin build health")
    print()
    print("| Module | Direct project deps | Kotlin files |")
    print("| --- | ---: | ---: |")
    for module, dep_count, kotlin_files in rows:
        print(f"| `{module}` | {dep_count} | {kotlin_files} |")
    print()
    print("Use this report to spot dependency fan-out and modules that are growing beyond a focused responsibility.")


if __name__ == "__main__":
    main()
