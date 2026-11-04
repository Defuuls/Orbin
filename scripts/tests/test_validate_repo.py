from __future__ import annotations

import sys
import unittest
from pathlib import Path
from unittest import mock

SCRIPTS_DIR = Path(__file__).resolve().parents[1]
if str(SCRIPTS_DIR) not in sys.path:
    sys.path.insert(0, str(SCRIPTS_DIR))

import validate_repo


class ValidateRepoTest(unittest.TestCase):
    def setUp(self) -> None:
        validate_repo.failures.clear()
        validate_repo.notes.clear()

    def tearDown(self) -> None:
        validate_repo.failures.clear()
        validate_repo.notes.clear()

    def test_tracked_hygiene_flags_forbidden_files(self) -> None:
        tracked_files = "\n".join(
            [
                "README.md",
                "scripts/run-orbin/README.md",
                "scripts/run-orbin/SKILL.md",
                "SKILL.md",
                "CLAUDE.md",
                "AGENTS.md",
                "GEMINI.md",
                ".antigravity/config.json",
                ".claude/settings.json",
                "scripts/__pycache__/foo.pyc",
            ]
        )
        with mock.patch("subprocess.check_output", return_value=tracked_files):
            validate_repo.check_tracked_hygiene()

        self.assertEqual(
            validate_repo.failures,
            [
                "[hygiene] tracked assistant/scratchpad file: scripts/run-orbin/SKILL.md",
                "[hygiene] tracked assistant/scratchpad file: SKILL.md",
                "[hygiene] tracked assistant/scratchpad file: CLAUDE.md",
                "[hygiene] tracked assistant/scratchpad file: AGENTS.md",
                "[hygiene] tracked assistant/scratchpad file: GEMINI.md",
                "[hygiene] tracked assistant/scratchpad file: .antigravity/config.json",
                "[hygiene] tracked assistant/scratchpad file: .claude/settings.json",
                "[hygiene] tracked Python bytecode artifact: scripts/__pycache__/foo.pyc",
            ],
        )

    def test_tracked_hygiene_allows_clean_tree(self) -> None:
        tracked_files = "\n".join(
            [
                "README.md",
                "scripts/run-orbin/README.md",
                "scripts/validate_repo.py",
            ]
        )
        with mock.patch("subprocess.check_output", return_value=tracked_files):
            validate_repo.check_tracked_hygiene()

        self.assertEqual(validate_repo.failures, [])


if __name__ == "__main__":
    unittest.main()
