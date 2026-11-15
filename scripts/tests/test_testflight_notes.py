from __future__ import annotations

import io
import sys
import unittest
import urllib.parse
from pathlib import Path
from unittest import mock

SCRIPTS_DIR = Path(__file__).resolve().parents[1]
if str(SCRIPTS_DIR) not in sys.path:
    sys.path.insert(0, str(SCRIPTS_DIR))

import testflight_notes


class TestFlightNotesTest(unittest.TestCase):
    def test_find_build_waits_for_valid_processing_and_preserves_exact_filters(self) -> None:
        states = iter(["PROCESSING", "PROCESSING", "VALID"])
        queries: list[dict[str, list[str]]] = []

        def fake_request(method: str, path: str, bearer: str, body: dict | None = None) -> dict:
            self.assertEqual(method, "GET")
            query = urllib.parse.parse_qs(urllib.parse.urlsplit(path).query)
            if path.startswith("/apps?"):
                self.assertEqual(query, {"filter[bundleId]": ["io.github.defuuls.orbin"], "limit": ["1"]})
                return {"data": [{"id": "app-1"}]}
            self.assertTrue(path.startswith("/builds?"))
            queries.append(query)
            return {"data": [{"id": "build-42", "attributes": {"version": "206.1", "processingState": next(states)}}]}

        output = io.StringIO()
        with (
            mock.patch.object(testflight_notes, "request", side_effect=fake_request),
            mock.patch.object(testflight_notes.time, "monotonic", return_value=0.0),
            mock.patch.object(testflight_notes.time, "sleep") as sleep,
            mock.patch("sys.stdout", output),
        ):
            build_id = testflight_notes.find_build("token-1", "io.github.defuuls.orbin", "188", "206.1")

        self.assertEqual(build_id, "build-42")
        self.assertEqual(sleep.call_count, 2)
        expected_query = {
            "filter[app]": ["app-1"],
            "filter[version]": ["206.1"],
            "filter[preReleaseVersion.version]": ["188"],
            "fields[builds]": ["version,processingState"],
            "limit": ["1"],
        }
        self.assertEqual(queries, [expected_query] * 3)
        self.assertEqual(
            output.getvalue().splitlines(),
            ["Build 206.1 processing state: PROCESSING.", "Build 206.1 processing state: VALID."],
        )

    def test_find_build_rejects_terminal_processing_failures_without_waiting(self) -> None:
        for state in ("FAILED", "INVALID"):
            with self.subTest(state=state):
                output = io.StringIO()
                with (
                    mock.patch.object(
                        testflight_notes,
                        "request",
                        side_effect=[
                            {"data": [{"id": "app-1"}]},
                            {"data": [{"id": "build-42", "attributes": {"version": "206.1", "processingState": state}}]},
                        ],
                    ),
                    mock.patch.object(testflight_notes.time, "sleep") as sleep,
                    mock.patch("sys.stdout", output),
                    self.assertRaisesRegex(SystemExit, rf"Build 206\.1 processing failed.*\({state}\)"),
                ):
                    testflight_notes.find_build("token-1", "io.github.defuuls.orbin", "188", "206.1")

                sleep.assert_not_called()
                self.assertEqual(output.getvalue(), f"Build 206.1 processing state: {state}.\n")

    def test_find_build_times_out_for_missing_processing_or_unknown_state(self) -> None:
        cases = (
            ({"version": "206.1"}, "MISSING"),
            ({"version": "206.1", "processingState": "PROCESSING"}, "PROCESSING"),
            ({"version": "206.1", "processingState": "unexpected\nAPI detail"}, "UNKNOWN"),
        )
        for attributes, safe_state in cases:
            with self.subTest(state=safe_state):
                now = [0.0]

                def fake_request(method: str, path: str, bearer: str, body: dict | None = None) -> dict:
                    if path.startswith("/apps?"):
                        return {"data": [{"id": "app-1"}]}
                    return {"data": [{"id": "build-42", "attributes": attributes}]}

                def advance_clock(seconds: float) -> None:
                    now[0] += seconds

                output = io.StringIO()
                with (
                    mock.patch.object(testflight_notes, "BUILD_WAIT_SECONDS", 5),
                    mock.patch.object(testflight_notes, "BUILD_POLL_SECONDS", 2),
                    mock.patch.object(testflight_notes, "request", side_effect=fake_request),
                    mock.patch.object(testflight_notes.time, "monotonic", side_effect=lambda: now[0]),
                    mock.patch.object(testflight_notes.time, "sleep", side_effect=advance_clock),
                    mock.patch("sys.stdout", output),
                    self.assertRaisesRegex(SystemExit, rf"did not reach processing state VALID.*last state: {safe_state}"),
                ):
                    testflight_notes.find_build("token-1", "io.github.defuuls.orbin", "188", "206.1")

                self.assertLessEqual(now[0], 7.0)
                self.assertEqual(output.getvalue(), f"Build 206.1 processing state: {safe_state}.\n")

    def test_find_build_refreshes_token_after_15_minutes(self) -> None:
        tokens = ["token-1", "token-2"]
        issued = []

        def make_token() -> str:
            tok = tokens[len(issued)]
            issued.append(tok)
            return tok

        seen_bearers: list[str] = []

        def fake_request(method: str, path: str, bearer: str, body: dict | None = None) -> dict:
            seen_bearers.append(bearer)
            if path.startswith("/apps?"):
                return {"data": [{"id": "app-1"}]}
            if len(seen_bearers) == 2:
                return {"data": [{"id": "build-42", "attributes": {"version": "206.1", "processingState": "PROCESSING"}}]}
            return {"data": [{"id": "build-42", "attributes": {"version": "206.1", "processingState": "VALID"}}]}

        # monotonic calls:
        # 1. token_issued_at = 0.0
        # 2. deadline = 0.0 + BUILD_WAIT_SECONDS
        # 3. loop 1 refresh check: 100.0 (< 900)
        # 4. loop 1 deadline check: 100.0
        # 5. loop 2 refresh check: 950.0 (>= 900 -> refreshes!)
        # 6. loop 2 new token_issued_at = 950.0
        clock = iter([0.0, 0.0, 100.0, 100.0, 950.0, 950.0])

        with (
            mock.patch.object(testflight_notes, "request", side_effect=fake_request),
            mock.patch.object(testflight_notes.time, "monotonic", side_effect=lambda: next(clock)),
            mock.patch.object(testflight_notes.time, "sleep"),
            mock.patch("sys.stdout", io.StringIO()),
        ):
            build_id = testflight_notes.find_build(make_token, "io.github.defuuls.orbin", "188", "206.1")

        self.assertEqual(build_id, "build-42")
        self.assertEqual(issued, ["token-1", "token-2"])
        self.assertEqual(seen_bearers, ["token-1", "token-1", "token-2"])


if __name__ == "__main__":
    unittest.main()
