from __future__ import annotations

import sys
import unittest
from pathlib import Path

SCRIPTS_DIR = Path(__file__).resolve().parents[1]
if str(SCRIPTS_DIR) not in sys.path:
    sys.path.insert(0, str(SCRIPTS_DIR))

import prepare_release


class PrepareReleaseTest(unittest.TestCase):
    def test_update_changelog_inserts_new_section(self) -> None:
        release = prepare_release.Release(
            number=189,
            codename="Nopales",
            version_code=207,
            summary=["bump"],
        )
        initial = (
            "# Changelog\n\n"
            "## [Unreleased]\n\n"
            "## [188-Mizuna] - 2026-11-03\n\n"
            "### Fixed\n"
            "- Prior fix.\n\n"
            "[Unreleased]: https://github.com/Defuuls/Orbin/compare/v188-Mizuna...HEAD\n"
            "[188-Mizuna]: https://github.com/Defuuls/Orbin/compare/v187-Komatsuna...v188-Mizuna\n"
        )
        updated = prepare_release.update_changelog(
            initial,
            release,
            {"Added": ["New feature."]},
            "2026-11-04",
        )
        self.assertIn("## [189-Nopales] - 2026-11-04\n\n### Added\n- New feature.\n\n\n## [188-Mizuna]", updated)
        self.assertIn("[Unreleased]: https://github.com/Defuuls/Orbin/compare/v189-Nopales...HEAD", updated)
        self.assertIn(
            "[189-Nopales]: https://github.com/Defuuls/Orbin/compare/v188-Mizuna...v189-Nopales",
            updated,
        )

    def test_update_changelog_replaces_existing_section(self) -> None:
        release = prepare_release.Release(
            number=189,
            codename="Nopales",
            version_code=207,
            summary=["bump"],
        )
        existing = (
            "# Changelog\n\n"
            "## [Unreleased]\n\n"
            "## [189-Nopales] - 2026-11-03\n\n"
            "### Added\n"
            "- Old draft entry.\n\n"
            "## [188-Mizuna] - 2026-11-02\n\n"
            "### Fixed\n"
            "- Prior fix.\n\n"
            "[Unreleased]: https://github.com/Defuuls/Orbin/compare/v189-Nopales...HEAD\n"
            "[189-Nopales]: https://github.com/Defuuls/Orbin/compare/v188-Mizuna...v189-Nopales\n"
        )
        updated = prepare_release.update_changelog(
            existing,
            release,
            {"Added": ["Updated entry."], "Fixed": ["Second entry."]},
            "2026-11-04",
        )
        self.assertNotIn("Old draft entry.", updated)
        self.assertEqual(updated.count("## [189-Nopales]"), 1)
        self.assertIn(
            "## [189-Nopales] - 2026-11-04\n\n### Added\n- Updated entry.\n\n### Fixed\n- Second entry.\n\n## [188-Mizuna]",
            updated,
        )


if __name__ == "__main__":
    unittest.main()
