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

    def test_privacy_hosts_requires_every_contacted_host_in_the_policy(self) -> None:
        sources = {
            "app/src/main/kotlin/Api.kt": 'val a = "https://api.tracker.io/v1"; val b = "https://boards.4chan.org/g/"',
            "iosApp/Orbin/Sync.swift": 'let u = "https://Sync.Example-Cloud.com/x"',
        }

        validate_repo.check_privacy_hosts(sources, "We contact `boards.4chan.org` only.")

        self.assertEqual(
            [
                "[privacy-hosts] api.tracker.io (in app/src/main/kotlin/Api.kt) is not listed in PRIVACY.md "
                "under 'Who the app talks to'",
                "[privacy-hosts] sync.example-cloud.com (in iosApp/Orbin/Sync.swift) is not listed in PRIVACY.md "
                "under 'Who the app talks to'",
            ],
            validate_repo.failures,
        )

    def test_privacy_hosts_ignores_tests_tooling_and_placeholders(self) -> None:
        sources = {
            "app/src/test/kotlin/ApiTest.kt": '"https://only-in-a-test.io/"',
            "app-ios/src/androidHostTest/kotlin/T.kt": '"https://host-test-only.io/"',
            "scripts/run-orbin/src/Drive.kt": '"https://tooling-only.io/"',
            "core/testing/src/main/kotlin/Fakes.kt": '"https://fake-only.io/"',
            "app/src/main/kotlin/Docs.kt": '// https://www.apache.org/licenses "https://i.example.org/a" "https://x.invalid/"',
        }

        validate_repo.check_privacy_hosts(sources, "")

        self.assertEqual([], validate_repo.failures)

    MIRROR = "# comment\nhttps://mirror.example/maven2/\n"
    ROOT_BUILD = 'file("gradle/maven-central-mirror.txt")\nsystemProperty("robolectric.dependency.repo.url", m)'
    GOOD_SETTINGS = (
        'val m = file("gradle/maven-central-mirror.txt")\n'
        "repositories {\n"
        "    google()\n"
        '    maven(m) { name = "MavenCentralMirror" }\n'
        "    mavenCentral()\n"
        "}\n"
        "pluginManagement {\n"
        "    repositories {\n"
        '        maven(m) { name = "MavenCentralMirror" }\n'
        "        gradlePluginPortal()\n"
        "    }\n"
        "}\n"
    )

    def mirror_files(self, **overrides: str) -> dict[str, str]:
        files = {
            validate_repo.MIRROR_FILE: self.MIRROR,
            "settings.gradle.kts": self.GOOD_SETTINGS,
            "build-logic/settings.gradle.kts": self.GOOD_SETTINGS,
            "build.gradle.kts": self.ROOT_BUILD,
        }
        files.update(overrides)
        return files

    def test_maven_mirror_accepts_mirror_before_central_everywhere(self) -> None:
        validate_repo.check_maven_mirror(self.mirror_files())

        self.assertEqual([], validate_repo.failures)

    def test_maven_mirror_rejects_mirror_listed_after_central(self) -> None:
        after = self.GOOD_SETTINGS.replace(
            '    maven(m) { name = "MavenCentralMirror" }\n    mavenCentral()\n',
            '    mavenCentral()\n    maven(m) { name = "MavenCentralMirror" }\n',
        )

        validate_repo.check_maven_mirror(self.mirror_files(**{"build-logic/settings.gradle.kts": after}))

        self.assertEqual(
            ["[maven-mirror] build-logic/settings.gradle.kts:2 lists Maven Central or the plugin portal without the mirror before it"],
            validate_repo.failures,
        )

    def test_maven_mirror_rejects_a_repository_block_without_the_mirror(self) -> None:
        settings = self.GOOD_SETTINGS + "pluginManagement {\n    repositories {\n        mavenCentral()\n    }\n}\n"

        validate_repo.check_maven_mirror(self.mirror_files(**{"settings.gradle.kts": settings}))

        self.assertEqual(
            ["[maven-mirror] settings.gradle.kts:14 lists Maven Central or the plugin portal without the mirror before it"],
            validate_repo.failures,
        )

    def test_maven_mirror_requires_plugin_repositories(self) -> None:
        # build-logic once had none, so its kotlin-dsl plugin still came from the plugin portal.
        without = self.GOOD_SETTINGS[: self.GOOD_SETTINGS.index("pluginManagement")]

        validate_repo.check_maven_mirror(self.mirror_files(**{"build-logic/settings.gradle.kts": without}))

        self.assertEqual(
            ["[maven-mirror] build-logic/settings.gradle.kts has no pluginManagement repositories listing the mirror"],
            validate_repo.failures,
        )

    def test_maven_mirror_must_precede_the_plugin_portal(self) -> None:
        settings = self.GOOD_SETTINGS.replace(
            '        maven(m) { name = "MavenCentralMirror" }\n        gradlePluginPortal()\n',
            '        gradlePluginPortal()\n        maven(m) { name = "MavenCentralMirror" }\n',
        )

        validate_repo.check_maven_mirror(self.mirror_files(**{"settings.gradle.kts": settings}))

        self.assertEqual(
            ["[maven-mirror] settings.gradle.kts:8 lists Maven Central or the plugin portal without the mirror before it"],
            validate_repo.failures,
        )

    def test_maven_mirror_sees_past_nested_blocks(self) -> None:
        # The plugin repositories nest `google { content { ... } }`; a shortcut match ending at the
        # first closing brace would end the block there and never see the misplaced mirror.
        nested = (
            'val m = file("gradle/maven-central-mirror.txt")\n'
            "pluginManagement {\n"
            "    repositories {\n"
            "        google {\n"
            "            content {\n"
            '                includeGroupByRegex("androidx.*")\n'
            "            }\n"
            "        }\n"
            "        mavenCentral()\n"
            '        maven(m) { name = "MavenCentralMirror" }\n'
            "    }\n"
            "}\n"
        )

        validate_repo.check_maven_mirror(self.mirror_files(**{"settings.gradle.kts": nested}))

        self.assertEqual(
            ["[maven-mirror] settings.gradle.kts:3 lists Maven Central or the plugin portal without the mirror before it"],
            validate_repo.failures,
        )

    def test_maven_mirror_requires_one_https_url_and_robolectric_wiring(self) -> None:
        validate_repo.check_maven_mirror(
            self.mirror_files(**{validate_repo.MIRROR_FILE: "http://a/\nhttp://b/\n", "build.gradle.kts": ""})
        )

        self.assertEqual(
            [
                "[maven-mirror] gradle/maven-central-mirror.txt must hold exactly one https:// URL",
                "[maven-mirror] build.gradle.kts does not point Robolectric's downloads at the mirror",
            ],
            validate_repo.failures,
        )

    def test_maven_mirror_passes_for_the_repository_as_it_is(self) -> None:
        validate_repo.check_maven_mirror()

        self.assertEqual([], validate_repo.failures)

    def test_privacy_hosts_passes_for_the_repository_as_it_is(self) -> None:
        validate_repo.check_privacy_hosts()

        self.assertEqual([], validate_repo.failures)

    def test_tracked_hygiene_flags_files_not_on_the_allowlist(self) -> None:
        tracked_files = "\n".join(
            [
                "README.md",
                "scripts/run-orbin/README.md",
                "scripts/run-orbin/NOTES.md",
                "NOTES.md",
                ".scratch/settings.json",
                "scripts/__pycache__/foo.pyc",
            ]
        )
        with mock.patch("subprocess.check_output", return_value=tracked_files):
            validate_repo.check_tracked_hygiene()

        self.assertEqual(
            validate_repo.failures,
            [
                "[hygiene] tracked file not on the allowlist: scripts/run-orbin/NOTES.md",
                "[hygiene] tracked file not on the allowlist: NOTES.md",
                "[hygiene] tracked file not on the allowlist: .scratch/settings.json",
                "[hygiene] tracked Python bytecode artifact: scripts/__pycache__/foo.pyc",
            ],
        )

    def test_tracked_hygiene_allows_clean_tree(self) -> None:
        tracked_files = "\n".join(
            [
                ".gitignore",
                "README.md",
                "docs/design-system/IMPLEMENTATION.md",
                "scripts/run-orbin/README.md",
                "scripts/validate_repo.py",
            ]
        )
        with mock.patch("subprocess.check_output", return_value=tracked_files):
            validate_repo.check_tracked_hygiene()

        self.assertEqual(validate_repo.failures, [])

    def test_tracked_hygiene_passes_for_the_repository_as_it_is(self) -> None:
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
