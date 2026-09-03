#!/usr/bin/env python3
"""Fail fast when Orbin's module dependency rules drift.

This complements Gradle and detekt by checking project-to-project dependencies at the repository
boundary. Keep the rules boring and explicit: architecture should be visible in one file and cheap
enough to run on every PR.
"""
from __future__ import annotations

import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parents[1]
PROJECT_DEP = re.compile(r'project\(\s*"(:[^"]+)"\s*\)')


def module_path(module: str) -> pathlib.Path:
    return ROOT.joinpath(*module.lstrip(":").split(":"))


def project_dependencies(module: str) -> set[str]:
    build = module_path(module) / "build.gradle.kts"
    if not build.exists():
        return set()
    return set(PROJECT_DEP.findall(build.read_text(encoding="utf-8")))


def included_modules() -> list[str]:
    settings = (ROOT / "settings.gradle.kts").read_text(encoding="utf-8")
    return re.findall(r'include\("(:[^"]+)"\)', settings)


def fail(errors: list[str], message: str) -> None:
    errors.append(message)


def main() -> int:
    errors: list[str] = []
    modules = included_modules()
    deps = {module: project_dependencies(module) for module in modules}

    # Feature modules are leaves in the product graph. They may share domain/core/UI primitives,
    # but must not call sideways into another feature.
    feature_modules = {m for m in modules if m.startswith(":feature:")}
    for module in sorted(feature_modules):
        sideways = deps[module] & feature_modules
        if sideways:
            fail(errors, f"{module} depends on feature module(s): {', '.join(sorted(sideways))}")

    # The pure model module is the innermost ring and may not acquire project dependencies.
    if deps.get(":core:model"):
        fail(errors, f":core:model must remain dependency-free, found {sorted(deps[':core:model'])}")

    # Domain may depend only inward on core and the provider SPI, never concrete infrastructure.
    allowed_domain_prefixes = (":core:", ":provider:api")
    for dep in sorted(deps.get(":domain", set())):
        if not dep.startswith(allowed_domain_prefixes):
            fail(errors, f":domain must not depend on infrastructure module {dep}")

    # Provider implementations may use the SPI/core/network plumbing, but never app/features/data
    # or each other. This keeps an engine replaceable and independently testable.
    for module in sorted(m for m in modules if m.startswith(":provider:") and m != ":provider:api"):
        for dep in sorted(deps[module]):
            if dep.startswith(":feature:") or dep in {":app", ":data", ":media", ":ui-next"}:
                fail(errors, f"{module} has forbidden dependency {dep}")
            if dep.startswith(":provider:") and dep != ":provider:api":
                fail(errors, f"{module} must not depend on provider implementation {dep}")

    # ui-next is presentation-only. It must not know about repositories, providers or features.
    for dep in sorted(deps.get(":ui-next", set())):
        if dep.startswith(":feature:") or dep.startswith(":provider:") or dep in {":data", ":domain", ":network"}:
            fail(errors, f":ui-next has forbidden dependency {dep}")

    # Prevent Android/infrastructure imports from slipping into the pure core model source tree.
    model_root = module_path(":core:model") / "src/main/kotlin"
    forbidden_imports = (
        "import android.",
        "import androidx.",
        "import com.orbin.data.",
        "import com.orbin.domain.",
        "import com.orbin.feature.",
        "import com.orbin.network.",
        "import com.orbin.provider.",
    )
    for file in model_root.rglob("*.kt"):
        text = file.read_text(encoding="utf-8")
        for needle in forbidden_imports:
            if needle in text:
                fail(errors, f"{file.relative_to(ROOT)} imports forbidden outer layer: {needle}")

    if errors:
        print("Architecture validation failed:")
        for error in errors:
            print(f"  - {error}")
        return 1

    print(f"Architecture validation passed for {len(modules)} modules.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
