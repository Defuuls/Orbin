from __future__ import annotations

import json
import sys
import unittest
from pathlib import Path

SCRIPTS_DIR = Path(__file__).resolve().parents[1]
if str(SCRIPTS_DIR) not in sys.path:
    sys.path.insert(0, str(SCRIPTS_DIR))

import token_literals


class TokenLiteralTest(unittest.TestCase):
    def test_no_file_adds_literals_beyond_the_baseline(self) -> None:
        baseline = json.loads(token_literals.BASELINE.read_text(encoding="utf-8"))
        problems = token_literals.violations(token_literals.count_literals(), baseline)
        self.assertEqual(problems, [], "Use a design token instead of a literal:\n" + "\n".join(problems))

    def test_patterns_catch_each_kind(self) -> None:
        sample = (
            "Color(0xFF00D4FF) RoundedCornerShape(22.dp) fontSize = 13.sp "
            "lineHeight = 18.sp x.copy(alpha = 0.14f)"
        )
        found = {kind: len(p.findall(sample)) for kind, p in token_literals.PATTERNS.items()}
        self.assertEqual(found, {"color": 1, "radius": 1, "textSize": 2, "opacity": 1})

    def test_token_names_are_not_literals(self) -> None:
        sample = "RoundedCornerShape(OrbinRadius.card) reader.accent fontSize = OrbinType.meta.fontSize"
        self.assertTrue(all(not p.search(sample) for p in token_literals.PATTERNS.values()))

    def test_increase_is_reported(self) -> None:
        problems = token_literals.violations({"a.kt": {"color": 2}}, {"a.kt": {"color": 1}})
        self.assertEqual(len(problems), 1)
        self.assertEqual(token_literals.violations({"a.kt": {"color": 1}}, {"a.kt": {"color": 2}}), [])

    def test_retired_cyan_accent_is_gone_from_ui(self) -> None:
        for base in token_literals.SCANNED:
            for path in base.rglob("*.kt"):
                rel = path.relative_to(token_literals.ROOT).as_posix()
                if rel in token_literals.ALLOWLIST:
                    continue
                self.assertNotIn("0xFF00D4FF", path.read_text(encoding="utf-8").upper().replace("0XFF", "0xFF"), rel)


if __name__ == "__main__":
    unittest.main()
