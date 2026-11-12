from __future__ import annotations

import sys
import tempfile
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

    def check_workflow_options(self, content: str) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            workflows = root / ".github/workflows"
            workflows.mkdir(parents=True)
            (workflows / "benchmark.yml").write_text(content, encoding="utf-8")
            with mock.patch.object(validate_repo, "ROOT", root):
                validate_repo.check_emulator_options()

    def test_emulator_serial_console_conflict_is_rejected(self) -> None:
        for options in (
            "-no-window -show-kernel -logcat '*:E'",
            '"-no-window -logcat \'*:E\' -show-kernel"',
        ):
            with self.subTest(options=options):
                validate_repo.failures.clear()
                self.check_workflow_options(f"          emulator-options: {options}\n")
                self.assertEqual(len(validate_repo.failures), 1)
                self.assertIn("benchmark.yml:1", validate_repo.failures[0])
                self.assertIn("-show-kernel", validate_repo.failures[0])
                self.assertIn("-logcat", validate_repo.failures[0])

    def test_each_emulator_diagnostic_option_is_safe_alone(self) -> None:
        for options in ("-no-window -show-kernel", "-no-window -logcat '*:E'", "-no-window"):
            with self.subTest(options=options):
                self.check_workflow_options(f"          emulator-options: {options}\n")
                self.assertEqual(validate_repo.failures, [])

    def test_emulator_flags_in_separate_steps_do_not_conflict(self) -> None:
        self.check_workflow_options(
            "      - with:\n"
            "          emulator-options: -show-kernel\n"
            "      - with:\n"
            "          emulator-options: -logcat '*:E'\n"
        )
        self.assertEqual(validate_repo.failures, [])

    def test_emulator_option_comments_are_not_arguments(self) -> None:
        self.check_workflow_options("          emulator-options: -show-kernel # -logcat is an alternative\n")
        self.assertEqual(validate_repo.failures, [])

    def test_repository_emulator_options_have_no_serial_console_conflict(self) -> None:
        validate_repo.check_emulator_options()
        self.assertEqual(validate_repo.failures, [])


if __name__ == "__main__":
    unittest.main()
