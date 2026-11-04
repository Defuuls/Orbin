from __future__ import annotations

import sys
import unittest
from pathlib import Path
from unittest import mock

SCRIPTS_DIR = Path(__file__).resolve().parents[1]
if str(SCRIPTS_DIR) not in sys.path:
    sys.path.insert(0, str(SCRIPTS_DIR))

import testflight_notes


class TestFlightNotesTest(unittest.TestCase):
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
                return {"data": []}
            return {"data": [{"id": "build-42"}]}

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
        ):
            build_id = testflight_notes.find_build(make_token, "io.github.defuuls.orbin", "188", "206.1")

        self.assertEqual(build_id, "build-42")
        self.assertEqual(seen_bearers, ["token-1", "token-1", "token-2"])


if __name__ == "__main__":
    unittest.main()
