"""Checks docs/design-system: the spec's arithmetic, contrast, token references, diagrams and companion page.

The spec (README.md) is the source of truth. These tests read its tables, so a number that is
changed in one place and not another fails here rather than in an implementation.
"""

from __future__ import annotations

import re
import unittest
import xml.etree.ElementTree as ET
from pathlib import Path

DESIGN_DIR = Path(__file__).resolve().parents[2] / "docs" / "design-system"
SPEC = DESIGN_DIR / "README.md"
PAGE = DESIGN_DIR / "schematic.html"
DIAGRAMS = DESIGN_DIR / "diagrams"

THEMES = ("Light", "Dark", "AMOLED")
TOKEN_REF = re.compile(r"\b(?:color|type|space|size|radius)\.[A-Za-z][A-Za-z0-9]*\b")
HEX = re.compile(r"#[0-9A-Fa-f]{6}\b")
SVG_NS = "{http://www.w3.org/2000/svg}"
# Words that only a posting control would carry. Orbin is read-only.
POSTING_WORDS = re.compile(r"\b(post a reply|post reply|write a reply|quick reply|compose|sage|new thread)\b", re.IGNORECASE)


def parse_tables(markdown: str) -> list[tuple[list[str], list[list[str]]]]:
    """Every Markdown table as (headers, rows), in document order."""
    tables: list[tuple[list[str], list[list[str]]]] = []
    lines = markdown.splitlines()
    index = 0
    while index < len(lines) - 1:
        if lines[index].startswith("|") and re.fullmatch(r"\|(?:\s*:?-+:?\s*\|)+", lines[index + 1].strip()):
            headers = _cells(lines[index])
            rows = []
            index += 2
            while index < len(lines) and lines[index].startswith("|"):
                rows.append(_cells(lines[index]))
                index += 1
            tables.append((headers, rows))
        else:
            index += 1
    return tables


def _cells(line: str) -> list[str]:
    return [cell.strip().strip("`") for cell in line.strip().strip("|").split("|")]


def number(value: str) -> float:
    """'56dp' -> 56.0, '14sp' -> 14.0, '114.67dp' -> 114.67."""
    match = re.fullmatch(r"(\d+(?:\.\d+)?)(?:dp|sp)?", value)
    if match is None:
        raise ValueError(f"not a dimension: {value!r}")
    return float(match.group(1))


def relative_luminance(hex_color: str) -> float:
    channels = [int(hex_color[i : i + 2], 16) / 255 for i in (1, 3, 5)]
    linear = [c / 12.92 if c <= 0.04045 else ((c + 0.055) / 1.055) ** 2.4 for c in channels]
    return 0.2126 * linear[0] + 0.7152 * linear[1] + 0.0722 * linear[2]


def contrast_ratio(first: str, second: str) -> float:
    lighter, darker = sorted((relative_luminance(first), relative_luminance(second)), reverse=True)
    return (lighter + 0.05) / (darker + 0.05)


class Spec:
    """The spec's tables, looked up by their header row."""

    def __init__(self) -> None:
        self.text = SPEC.read_text(encoding="utf-8")
        self.tables = parse_tables(self.text)

    def find(self, headers: list[str]) -> list[list[list[str]]]:
        return [rows for found, rows in self.tables if found == headers]

    def one(self, headers: list[str]) -> list[list[str]]:
        found = self.find(headers)
        if len(found) != 1:
            raise AssertionError(f"expected one table with headers {headers}, found {len(found)}")
        return found[0]

    def colors(self) -> dict[str, dict[str, str]]:
        rows = self.one(["Token", "Light", "Dark", "AMOLED", "Use"])
        return {row[0]: dict(zip(THEMES, row[1:4])) for row in rows}

    def types(self) -> dict[str, tuple[float, float, int]]:
        rows = self.one(["Token", "Size", "Line height", "Weight", "Use"])
        return {row[0]: (number(row[1]), number(row[2]), int(row[3])) for row in rows}

    def measures(self) -> dict[str, float]:
        values: dict[str, float] = {}
        for rows in self.find(["Token", "Value", "Use"]):
            for row in rows:
                values[row[0]] = number(row[1])
        return values

    def raw_values(self) -> dict[str, str]:
        """Every token as the page should repeat it."""
        values = {name: "/".join(themes[t] for t in THEMES) for name, themes in self.colors().items()}
        for row in self.one(["Token", "Size", "Line height", "Weight", "Use"]):
            values[row[0]] = "/".join(row[1:4])
        for rows in self.find(["Token", "Value", "Use"]):
            for row in rows:
                values[row[0]] = row[1]
        return values


class ParserTest(unittest.TestCase):
    def test_contrast_ratio_matches_known_values(self) -> None:
        self.assertAlmostEqual(contrast_ratio("#000000", "#FFFFFF"), 21.0, places=2)
        self.assertAlmostEqual(contrast_ratio("#FFFFFF", "#FFFFFF"), 1.0, places=2)
        self.assertAlmostEqual(contrast_ratio("#FFFFFF", "#4CAF50"), 2.78, places=2)
        self.assertEqual(contrast_ratio("#123456", "#ABCDEF"), contrast_ratio("#ABCDEF", "#123456"))

    def test_number_rejects_values_that_are_not_dimensions(self) -> None:
        self.assertEqual(number("56dp"), 56.0)
        self.assertEqual(number("114.67dp"), 114.67)
        for bad in ("", "dp", "56 dp", "#FFFFFF", "56px", "-4dp"):
            with self.assertRaises(ValueError, msg=bad):
                number(bad)

    def test_parse_tables_reads_headers_rows_and_backticks(self) -> None:
        tables = parse_tables("text\n\n| A | B |\n| --- | :-: |\n| `x.y` | 1dp |\n| z | 2dp |\n\nmore\n| not | a table |\n")
        self.assertEqual(tables, [(["A", "B"], [["x.y", "1dp"], ["z", "2dp"]])])


class TokenTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.spec = Spec()
        cls.colors = cls.spec.colors()
        cls.types = cls.spec.types()
        cls.m = cls.spec.measures()

    def test_token_names_are_unique_and_namespaced(self) -> None:
        names = []
        for headers, rows in self.spec.tables:
            if headers and headers[0] == "Token":
                names += [row[0] for row in rows]
        self.assertEqual(len(names), len(set(names)), "a token is defined twice")
        for name in names:
            self.assertRegex(name, r"^(color|type|space|size|radius)\.[a-z][A-Za-z0-9]*$")
        self.assertGreater(len(names), 40)

    def test_token_tables_hold_only_their_own_namespace(self) -> None:
        self.assertTrue(all(name.startswith("color.") for name in self.colors))
        self.assertTrue(all(name.startswith("type.") for name in self.types))
        self.assertTrue(all(name.split(".")[0] in {"space", "size", "radius"} for name in self.m))

    def test_colors_are_six_digit_hex_in_every_theme(self) -> None:
        for name, themes in self.colors.items():
            for theme in THEMES:
                self.assertRegex(themes[theme], r"^#[0-9A-F]{6}$", f"{name} {theme}")

    def test_amoled_grounds_are_pure_black(self) -> None:
        for name in ("color.appBar", "color.background"):
            self.assertEqual(self.colors[name]["AMOLED"], "#000000", name)

    def test_contrast_pairs_meet_their_minimum_in_every_theme(self) -> None:
        pairs = self.spec.one(["Foreground", "Background", "Minimum ratio"])
        self.assertGreaterEqual(len(pairs), 10)
        failures = []
        for foreground, background, minimum in pairs:
            self.assertIn(float(minimum), (3.0, 4.5), f"{foreground} on {background}")
            for theme in THEMES:
                ratio = contrast_ratio(self.colors[foreground][theme], self.colors[background][theme])
                if ratio < float(minimum):
                    failures.append(f"{foreground} on {background} in {theme}: {ratio:.2f} < {minimum}")
        self.assertEqual(failures, [])

    def test_every_text_color_is_checked_against_the_main_ground(self) -> None:
        pairs = {(f, b) for f, b, _ in self.spec.one(["Foreground", "Background", "Minimum ratio"])}
        grounds = {"color.appBar", "color.background", "color.surfaceVariant", "color.selected", "color.hairline"}
        for name in self.colors:
            if name in grounds:
                continue
            self.assertTrue(any(f == name for f, _ in pairs), f"{name} has no contrast pair")
        for name in ("color.ink", "color.inkMuted"):
            for ground in ("color.background", "color.surfaceVariant", "color.selected"):
                self.assertIn((name, ground), pairs)

    def test_line_heights_and_weights_are_sane(self) -> None:
        for name, (size, line, weight) in self.types.items():
            self.assertGreaterEqual(size, 12, f"{name} is smaller than 12sp")
            self.assertGreaterEqual(line / size, 1.2, f"{name} line height is too tight")
            self.assertIn(weight, (400, 500, 700), name)

    def test_touch_targets(self) -> None:
        m = self.m
        self.assertGreaterEqual(m["size.touchTarget"], 48)
        self.assertGreaterEqual(m["size.appBar"], m["size.touchTarget"])
        self.assertLessEqual(m["size.icon"], m["size.touchTarget"])
        self.assertLessEqual(m["size.pillHeight"], m["size.touchTarget"])
        self.assertGreaterEqual(m["size.pillMinWidth"], m["size.pillHeight"])
        self.assertEqual(m["radius.pill"] * 2, m["size.pillHeight"], "the pill is not fully rounded")
        self.assertGreaterEqual(m["size.cellMin"], m["size.touchTarget"])


class LayoutTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.spec = Spec()
        cls.types = cls.spec.types()
        cls.m = cls.spec.measures()

    def test_cell_minimum_is_thumbnail_plus_padding(self) -> None:
        m = self.m
        self.assertEqual(m["size.cellMin"], m["size.thumbnail"] + 2 * m["space.rowPadY"])
        self.assertEqual(m["size.cellMinWide"], m["size.thumbnailWide"] + 2 * m["space.rowPadY"])
        self.assertEqual(m["space.rowPadY"], m["space.postPadY"], "a row and a post share one cell")
        self.assertGreater(m["size.thumbnailWide"], m["size.thumbnail"])

    def test_single_line_row_text_fits_beside_the_thumbnail(self) -> None:
        text_height = (
            self.types["type.rowTitle"][1] + self.m["space.line"] + self.types["type.meta"][1]
            + self.m["space.line"] + self.types["type.preview"][1]
        )
        self.assertLessEqual(text_height, self.m["size.thumbnail"])

    def test_row_budgets_add_up_and_quote_their_tokens(self) -> None:
        budgets = self.spec.find(["Part", "Token", "Width"])
        self.assertEqual(len(budgets), 2)
        catalog_pane_at_960 = self._pane_row(960)[0]
        for rows, total in zip(budgets, (360, catalog_pane_at_960)):
            self.assertEqual(sum(number(row[2]) for row in rows), total)
            remainders = [row for row in rows if row[1] == "remainder"]
            self.assertEqual(len(remainders), 1)
            self.assertGreater(number(remainders[0][2]), 150, "the text column is too narrow to read")
            for part, token, width in rows:
                if token != "remainder":
                    self.assertEqual(number(width), self.m[token], f"{part} does not match {token}")
        self.assertEqual(budgets[0][1][1], "size.thumbnail")
        self.assertEqual(budgets[1][1][1], "size.thumbnailWide")

    def _pane_row(self, window: int) -> tuple[float, float, float]:
        for row in self.spec.one(["Window width", "Catalog pane", "Divider", "Thread pane"]):
            if number(row[0]) == window:
                return number(row[1]), number(row[2]), number(row[3])
        raise AssertionError(f"no pane example for {window}dp")

    def test_pane_examples_follow_the_rule(self) -> None:
        m = self.m
        rows = self.spec.one(["Window width", "Catalog pane", "Divider", "Thread pane"])
        windows = [number(row[0]) for row in rows]
        self.assertIn(m["size.twoPaneMinWidth"], windows, "the smallest two-pane window has no example")
        self.assertIn(960, windows)
        capped = False
        for row in rows:
            window, catalog, divider, thread = (number(cell) for cell in row)
            self.assertGreaterEqual(window, m["size.twoPaneMinWidth"])
            self.assertEqual(divider, m["size.paneDivider"])
            expected = min(m["size.listPaneMax"], (window - divider) * 5 // 12)
            self.assertEqual(catalog, expected, f"catalog pane at {window}dp")
            self.assertEqual(catalog + divider + thread, window, f"panes at {window}dp do not fill the window")
            self.assertGreater(thread, catalog, "the thread pane must be the wider one")
            capped = capped or catalog == m["size.listPaneMax"]
        self.assertTrue(capped, "no example reaches the catalog pane's maximum width")

    def test_two_pane_thresholds_are_ordered(self) -> None:
        m = self.m
        self.assertLess(m["size.wideMin"], m["size.twoPaneMinWidth"])
        self.assertLess(m["size.listPaneMax"], m["size.twoPaneMinWidth"] / 2 + 100)
        self.assertLessEqual(m["size.drawerMax"], 360 - m["size.appBar"] + 16)

    def test_grid_examples_follow_the_rule(self) -> None:
        m = self.m
        rows = self.spec.one(["Pane width", "Columns", "Card width"])
        self.assertIn(360, [number(row[0]) for row in rows])
        for pane, columns, card in rows:
            width = number(pane)
            expected_columns = max(1, int(width // m["size.gridCardMin"]))
            self.assertEqual(int(columns), expected_columns, f"columns at {pane}")
            expected_card = (width - (expected_columns + 1) * m["space.gridGap"]) / expected_columns
            self.assertAlmostEqual(number(card), expected_card, places=2, msg=f"card width at {pane}")

    def test_grid_card_text_block_fits_its_content(self) -> None:
        m, types = self.m, self.types
        content = 2 * m["space.cardPad"] + 2 * types["type.cardTitle"][1] + m["space.line"] + types["type.meta"][1]
        self.assertEqual(m["size.gridCardText"], content)


class ReferenceTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.spec = Spec()
        cls.defined = set(cls.spec.raw_values())
        cls.svgs = sorted(DIAGRAMS.glob("*.svg"))

    def test_every_token_named_anywhere_is_defined(self) -> None:
        self.assertGreaterEqual(len(self.svgs), 6)
        for path in [SPEC, PAGE, *self.svgs]:
            unknown = sorted(set(TOKEN_REF.findall(path.read_text(encoding="utf-8"))) - self.defined)
            self.assertEqual(unknown, [], f"{path.name} names tokens the spec does not define")

    def test_every_token_is_used_by_a_rule(self) -> None:
        prose = "\n".join(line for line in self.spec.text.splitlines() if not re.match(r"\| `[a-z]+\.[A-Za-z0-9]+` \|", line))
        unused = sorted(self.defined - set(TOKEN_REF.findall(prose)))
        self.assertEqual(unused, [], "tokens defined but never referenced by a rule")

    def test_spec_and_page_show_every_diagram(self) -> None:
        linked = set(re.findall(r"\]\(diagrams/([a-z0-9-]+\.svg)\)", self.spec.text))
        on_page = set(re.findall(r'src="diagrams/([a-z0-9-]+\.svg)"', PAGE.read_text(encoding="utf-8")))
        on_disk = {path.name for path in self.svgs}
        self.assertEqual(linked, on_disk)
        self.assertEqual(on_page, on_disk)

    def test_page_repeats_every_token_value_exactly(self) -> None:
        page = PAGE.read_text(encoding="utf-8")
        shown: dict[str, str] = {}
        for attributes in re.findall(r"<tr (data-token=[^>]+)>", page):
            fields = dict(re.findall(r'data-([a-z]+)="([^"]*)"', attributes))
            value = fields.get("value") or "/".join(fields[key] for key in ("light", "dark", "amoled"))
            self.assertNotIn(fields["token"], shown, "the page lists a token twice")
            shown[fields["token"]] = value
        self.assertEqual(shown, self.spec.raw_values())

    def test_page_visible_values_match_its_data_attributes(self) -> None:
        page = PAGE.read_text(encoding="utf-8")
        rows = re.findall(r"<tr data-token=[^>]+>.*?</tr>", page, flags=re.DOTALL)
        self.assertEqual(len(rows), len(self.defined))
        for row in rows:
            fields = dict(re.findall(r'data-([a-z]+)="([^"]*)"', row.split(">", 1)[0]))
            visible = re.sub(r"<[^>]+>", " ", row.split(">", 1)[1])
            for key, value in fields.items():
                for part in value.split("/") if key == "value" else [value]:
                    self.assertIn(part, visible, f"{fields['token']} shows a different {key}")

    def test_page_defers_to_the_spec(self) -> None:
        self.assertIn("the spec is correct", PAGE.read_text(encoding="utf-8"))
        self.assertIn("this document is correct", self.spec.text)


class DiagramTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.svgs = sorted(DIAGRAMS.glob("*.svg"))
        cls.palette = {value for themes in Spec().colors().values() for value in themes.values()}

    def test_diagrams_are_well_formed_and_described(self) -> None:
        for path in self.svgs:
            root = ET.parse(path).getroot()
            self.assertEqual(root.tag, f"{SVG_NS}svg", path.name)
            self.assertEqual(root.get("role"), "img", path.name)
            _, _, width, height = (float(part) for part in root.get("viewBox", "").split())
            self.assertEqual((float(root.get("width")), float(root.get("height"))), (width, height), path.name)
            for tag in ("title", "desc"):
                element = root.find(f"{SVG_NS}{tag}")
                self.assertIsNotNone(element, f"{path.name} has no <{tag}>")
                self.assertGreater(len((element.text or "").strip()), 5, f"{path.name} <{tag}> is empty")

    def test_diagrams_use_only_token_colors(self) -> None:
        for path in self.svgs:
            used = {value.upper() for value in HEX.findall(path.read_text(encoding="utf-8"))}
            self.assertEqual(sorted(used - self.palette), [], f"{path.name} uses colors that are not tokens")

    def test_diagrams_carry_a_dark_variant(self) -> None:
        for path in self.svgs:
            self.assertIn("prefers-color-scheme:dark", path.read_text(encoding="utf-8"), path.name)

    def test_element_ids_are_unique_across_diagrams(self) -> None:
        seen: dict[str, str] = {}
        for path in self.svgs:
            for element in ET.parse(path).getroot().iter():
                identifier = element.get("id")
                if identifier is None:
                    continue
                self.assertNotIn(identifier, seen, f"id {identifier} is in {path.name} and {seen.get(identifier)}")
                seen[identifier] = path.name

    def test_text_stays_inside_the_drawing(self) -> None:
        for path in self.svgs:
            root = ET.parse(path).getroot()
            _, _, width, height = (float(part) for part in root.get("viewBox").split())
            for element in root.findall(f"{SVG_NS}text"):
                x, y = float(element.get("x")), float(element.get("y"))
                self.assertTrue(0 <= x <= width and 0 < y <= height, f"{path.name}: text at {x},{y} is outside")

    def test_nothing_shows_a_posting_control(self) -> None:
        for path in [*self.svgs, PAGE]:
            content = path.read_text(encoding="utf-8")
            visible = re.sub(r"<style>.*?</style>", " ", content, flags=re.DOTALL)
            visible = re.sub(r"<[^>]+>", " ", visible)
            visible = visible.replace("The compose button, the reply box and the reply arrow on each post. Orbin does not post.", "")
            self.assertIsNone(POSTING_WORDS.search(visible), f"{path.name} shows a posting control")


if __name__ == "__main__":
    unittest.main()
