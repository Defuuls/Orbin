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
CODE_TOKENS = (
    Path(__file__).resolve().parents[2]
    / "ui-next" / "src" / "commonMain" / "kotlin" / "com" / "orbin" / "uinext" / "tokens" / "ReaderTokens.kt"
)

THEMES = ("Light", "Dark", "AMOLED")
TOKEN_REF = re.compile(r"\b(?:color|type|space|size|radius|ratio)\.[A-Za-z][A-Za-z0-9]*\b")
HEX = re.compile(r"#(?:[0-9A-Fa-f]{8}|[0-9A-Fa-f]{6})\b")
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
                row = _cells(lines[index])
                if len(row) != len(headers):
                    raise ValueError(f"table row has {len(row)} cells for {len(headers)} headers: {lines[index]}")
                rows.append(row)
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


def color_components(hex_color: str) -> tuple[tuple[float, float, float], float]:
    """Read opaque #RRGGBB or Android #AARRGGBB, without losing its alpha."""
    if not re.fullmatch(r"#(?:[0-9A-Fa-f]{6}|[0-9A-Fa-f]{8})", hex_color):
        raise ValueError(f"not an RGB/ARGB color: {hex_color!r}")
    alpha = int(hex_color[1:3], 16) / 255 if len(hex_color) == 9 else 1.0
    offset = 3 if len(hex_color) == 9 else 1
    channels = tuple(int(hex_color[i : i + 2], 16) / 255 for i in range(offset, offset + 6, 2))
    return channels, alpha


def composite_color(hex_color: str, background: tuple[float, float, float]) -> tuple[float, float, float]:
    channels, alpha = color_components(hex_color)
    return tuple(channel * alpha + ground * (1 - alpha) for channel, ground in zip(channels, background))


def relative_luminance(color: str | tuple[float, float, float]) -> float:
    if isinstance(color, str):
        channels, alpha = color_components(color)
        if alpha != 1:
            raise ValueError("a translucent color needs its actual background before luminance can be measured")
    else:
        channels = color
    linear = [c / 12.92 if c <= 0.04045 else ((c + 0.055) / 1.055) ** 2.4 for c in channels]
    return 0.2126 * linear[0] + 0.7152 * linear[1] + 0.0722 * linear[2]


def contrast_ratio(foreground: str, background: str) -> float:
    """Measure the drawn foreground after source-over compositing onto its stated ground."""
    ground, alpha = color_components(background)
    if alpha != 1:
        raise ValueError("a translucent contrast background needs an explicit underlying ground")
    drawn = composite_color(foreground, ground)
    lighter, darker = sorted((relative_luminance(drawn), relative_luminance(ground)), reverse=True)
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

    def types(self) -> dict[str, tuple[float, float | None, int]]:
        rows = self.one(["Token", "Size", "Line height", "Weight", "Use"])
        return {row[0]: (number(row[1]), None if row[2] == "natural" else number(row[2]), int(row[3])) for row in rows}

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


def parse_code_tokens(kotlin: str) -> dict[str, str]:
    """ReaderTokens.kt as {token: value}, in the same form as Spec.raw_values()."""
    themes: dict[str, dict[str, str]] = {}
    values: dict[str, str] = {}
    scope = None
    lines = iter(kotlin.splitlines())
    for line in lines:
        opened = re.match(r"(?:val (Reader(?:Light|Dark|Amoled)Colors) =|object (Reader(?:Type|Space|Size|Radius|Ratio)) \{)", line)
        if opened:
            scope = opened.group(1) or opened.group(2)
            continue
        if line in (")", "    )", "}"):
            scope = None
            continue
        if scope is None:
            continue
        color = re.fullmatch(r"\s+(\w+) = Color\(0x([0-9A-F]{8})\),", line)
        if scope.endswith("Colors") and color:
            argb = color.group(2)
            themes.setdefault(color.group(1), {})[scope] = f"#{argb[2:] if argb.startswith('FF') else argb}"
        elif line.strip().startswith("val "):
            # A natural TextStyle may span lines; consume its balanced expression as one token.
            declaration = line.strip()
            while declaration.endswith("=") or declaration.count("(") > declaration.count(")"):
                try:
                    declaration += " " + next(lines).strip()
                except StopIteration as error:
                    raise ValueError(f"unterminated token: {declaration}") from error
            match = re.fullmatch(r"val (\w+): (TextStyle|Dp|Float)( get\(\))? = (.+)", declaration)
            if match is None:
                raise ValueError(f"unreadable token line in {scope}: {declaration}")
            name, kind, accessor, expression = match.groups()
            if accessor and (scope != "ReaderRatio" or kind != "Float"):
                raise ValueError(f"unexpected token accessor in {scope}: {declaration}")
            namespace = scope[len("Reader"):].lower()
            key = f"{namespace}.{name}"
            if key in values:
                raise ValueError(f"duplicate token: {key}")
            if scope == "ReaderType" and kind == "TextStyle":
                fixed = re.fullmatch(r"readerType\((\d+), (\d+), (\d+)\)", expression)
                natural = re.fullmatch(r"TextStyle\((.*)\)", expression)
                if fixed:
                    size, height, weight = fixed.groups()
                    values[key] = f"{size}sp/{height}sp/{weight}"
                elif natural:
                    fields = {}
                    for field in natural.group(1).split(","):
                        if field.strip():
                            field_name, separator, value = field.strip().partition(" = ")
                            if not separator or field_name in fields:
                                raise ValueError(f"unreadable natural style: {declaration}")
                            fields[field_name] = value
                    size = re.fullmatch(r"(\d+)\.sp", fields.get("fontSize", ""))
                    weight = {"FontWeight.Normal": "400", "FontWeight.Medium": "500", "FontWeight.Bold": "700"}.get(
                        fields.get("fontWeight")
                    )
                    if set(fields) != {"fontFamily", "fontSize", "fontWeight"} or fields.get("fontFamily") != "FontFamily.Default" or not size or not weight:
                        raise ValueError(f"unreadable natural style: {declaration}")
                    values[key] = f"{size.group(1)}sp/natural/{weight}"
                else:
                    raise ValueError(f"unreadable style: {declaration}")
            elif scope in ("ReaderSpace", "ReaderSize", "ReaderRadius") and kind == "Dp":
                measure = re.fullmatch(r"(\d+)\.dp", expression)
                if measure is None:
                    raise ValueError(f"unreadable dimension: {declaration}")
                values[key] = f"{measure.group(1)}dp"
            elif scope == "ReaderRatio" and kind == "Float":
                ratio = re.fullmatch(r"(\d+(?:\.\d+)?)f(?: / (\d+(?:\.\d+)?)f)?", expression)
                if ratio is None or (ratio.group(2) is not None and float(ratio.group(2)) == 0):
                    raise ValueError(f"unreadable ratio: {declaration}")
                value = float(ratio.group(1)) / (float(ratio.group(2)) if ratio.group(2) else 1)
                values[key] = f"{value:.6f}".rstrip("0").rstrip(".")
            else:
                raise ValueError(f"unexpected token kind in {scope}: {declaration}")
        elif "=" in line and not line.lstrip().startswith(("/", "*")):
            raise ValueError(f"unreadable token line in {scope}: {line.strip()}")
    for name, by_theme in themes.items():
        values[f"color.{name}"] = "/".join(
            by_theme[scope] for scope in ("ReaderLightColors", "ReaderDarkColors", "ReaderAmoledColors")
        )
    return values


class CodeTokenTest(unittest.TestCase):
    """The Kotlin tokens the app draws with must equal the document's tables."""

    def test_code_tokens_equal_the_spec(self) -> None:
        code = parse_code_tokens(CODE_TOKENS.read_text(encoding="utf-8"))
        spec = Spec().raw_values()
        self.assertEqual(sorted(set(spec) - set(code)), [], "tokens in the spec that the code lacks")
        self.assertEqual(sorted(set(code) - set(spec)), [], "tokens in the code that the spec lacks")
        self.assertEqual({name: code[name] for name in spec}, spec)

    def test_parser_reads_each_token_shape(self) -> None:
        sample = "\n".join(
            [
                "val ReaderLightColors =",
                "    ReaderColors(",
                "        ink = Color(0xFF111111),",
                "    )",
                "val ReaderDarkColors =",
                "    ReaderColors(",
                "        ink = Color(0xFF222222),",
                "    )",
                "val ReaderAmoledColors =",
                "    ReaderColors(",
                "        ink = Color(0xFF333333),",
                "    )",
                "object ReaderType {",
                "    /** Body. */",
                "    val body: TextStyle = readerType(15, 22, 400)",
                "    val postBody: TextStyle = TextStyle(fontFamily = FontFamily.Default, fontSize = 14.sp, fontWeight = FontWeight.Normal)",
                "    val postDetails: TextStyle =",
                "        TextStyle(",
                "            fontFamily = FontFamily.Default,",
                "            fontSize = 10.sp,",
                "            fontWeight = FontWeight.Normal,",
                "        )",
                "}",
                "object ReaderSize {",
                "    val appBar: Dp = 56.dp",
                "}",
                "object ReaderRatio {",
                "    val gridCard: Float = 9f / 18f",
                "    val gridThumbnail: Float = 16f / 13f",
                "}",
            ]
        )
        self.assertEqual(
            parse_code_tokens(sample),
            {
                "type.body": "15sp/22sp/400",
                "type.postBody": "14sp/natural/400",
                "type.postDetails": "10sp/natural/400",
                "size.appBar": "56dp",
                "ratio.gridCard": "0.5",
                "ratio.gridThumbnail": "1.230769",
                "color.ink": "#111111/#222222/#333333",
            },
        )

    def test_parser_preserves_translucent_argb_colors(self) -> None:
        sample = "\n".join(
            f"val Reader{theme}Colors =\n    ReaderColors(\n        ink = Color(0x{value}),\n    )"
            for theme, value in (("Light", "DE000000"), ("Dark", "B2FFFFFF"), ("Amoled", "FFFFFFFF"))
        )
        self.assertEqual(parse_code_tokens(sample), {"color.ink": "#DE000000/#B2FFFFFF/#FFFFFF"})

    def test_parser_rejects_dynamic_or_undefined_natural_styles_and_ratios(self) -> None:
        bad_styles = (
            "TextStyle(fontFamily = FontFamily.Default, fontSize = 14.sp)",
            "TextStyle(fontFamily = FontFamily.Default, fontSize = POST_SIZE.sp, fontWeight = FontWeight.Normal)",
            "TextStyle(fontFamily = FontFamily.Default, fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Normal)",
        )
        for expression in bad_styles:
            with self.assertRaises(ValueError, msg=expression):
                parse_code_tokens(f"object ReaderType {{\n    val postBody: TextStyle = {expression}\n}}")
        for expression in ("9f / 0f", "-1f", "WIDTH / HEIGHT", "0.5.toFloat()"):
            with self.assertRaises(ValueError, msg=expression):
                parse_code_tokens(f"object ReaderRatio {{\n    val gridCard: Float = {expression}\n}}")

    def test_parser_rejects_a_token_written_another_way(self) -> None:
        for bad in ("    val appBar = 56.dp", "    val appBar: Dp = 56.5.dp", "    val appBar: Dp = BAR"):
            with self.assertRaises(ValueError, msg=bad):
                parse_code_tokens("object ReaderSize {\n" + bad + "\n}")

    def test_a_theme_missing_a_color_fails(self) -> None:
        sample = "val ReaderLightColors =\n    ReaderColors(\n        ink = Color(0xFF111111),\n    )"
        with self.assertRaises(KeyError):
            parse_code_tokens(sample)


class ParserTest(unittest.TestCase):
    def test_contrast_ratio_matches_known_values(self) -> None:
        self.assertAlmostEqual(contrast_ratio("#000000", "#FFFFFF"), 21.0, places=2)
        self.assertAlmostEqual(contrast_ratio("#FFFFFF", "#FFFFFF"), 1.0, places=2)
        self.assertAlmostEqual(contrast_ratio("#FFFFFF", "#4CAF50"), 2.78, places=2)
        self.assertEqual(contrast_ratio("#123456", "#ABCDEF"), contrast_ratio("#ABCDEF", "#123456"))

    def test_translucent_ink_is_composited_onto_its_actual_ground(self) -> None:
        self.assertAlmostEqual(contrast_ratio("#80000000", "#FFFFFF"), contrast_ratio("#7F7F7F", "#FFFFFF"))
        self.assertEqual(contrast_ratio("#00ABCDEF", "#232323"), 1.0)
        self.assertAlmostEqual(contrast_ratio("#FFFFFFFF", "#000000"), 21.0)
        white = color_components("#FFFFFF")[0]
        selected = color_components("#444444")[0]
        self.assertNotEqual(composite_color("#B2FFFFFF", white), composite_color("#B2FFFFFF", selected))
        with self.assertRaises(ValueError):
            relative_luminance("#B2FFFFFF")
        with self.assertRaises(ValueError):
            contrast_ratio("#FFFFFF", "#80000000")

    def test_color_parser_rejects_invalid_or_css_shorthand_values(self) -> None:
        for invalid in ("#FFF", "#12345", "#1234567", "#GG0000", "123456", "rgba(0,0,0,.5)"):
            with self.assertRaises(ValueError, msg=invalid):
                color_components(invalid)

    def test_number_rejects_values_that_are_not_dimensions(self) -> None:
        self.assertEqual(number("56dp"), 56.0)
        self.assertEqual(number("114.67dp"), 114.67)
        for bad in ("", "dp", "56 dp", "#FFFFFF", "56px", "-4dp"):
            with self.assertRaises(ValueError, msg=bad):
                number(bad)

    def test_parse_tables_reads_headers_rows_and_backticks(self) -> None:
        tables = parse_tables("text\n\n| A | B |\n| --- | :-: |\n| `x.y` | 1dp |\n| z | 2dp |\n\nmore\n| not | a table |\n")
        self.assertEqual(tables, [(["A", "B"], [["x.y", "1dp"], ["z", "2dp"]])])

    def test_parse_tables_rejects_rows_with_missing_or_extra_columns(self) -> None:
        for row in ("| one |", "| one | two | three |"):
            with self.assertRaises(ValueError, msg=row):
                parse_tables("| A | B |\n| --- | --- |\n" + row)


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
            self.assertRegex(name, r"^(color|type|space|size|radius|ratio)\.[a-z][A-Za-z0-9]*$")
        self.assertGreater(len(names), 40)

    def test_token_tables_hold_only_their_own_namespace(self) -> None:
        self.assertTrue(all(name.startswith("color.") for name in self.colors))
        self.assertTrue(all(name.startswith("type.") for name in self.types))
        self.assertTrue(all(name.split(".")[0] in {"space", "size", "radius", "ratio"} for name in self.m))

    def test_colors_are_rgb_or_argb_hex_in_every_theme(self) -> None:
        for name, themes in self.colors.items():
            for theme in THEMES:
                self.assertRegex(themes[theme], r"^#(?:[0-9A-F]{6}|[0-9A-F]{8})$", f"{name} {theme}")

    def test_amoled_grounds_are_pure_black(self) -> None:
        for name in ("color.appBar", "color.statusBar", "color.background"):
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
        grounds = {"color.appBar", "color.statusBar", "color.background", "color.surfaceVariant", "color.selected", "color.hairline"}
        for name in self.colors:
            if name in grounds:
                continue
            self.assertTrue(any(f == name for f, _ in pairs), f"{name} has no contrast pair")
        for name in ("color.ink", "color.inkMuted"):
            for ground in ("color.background", "color.surfaceVariant", "color.selected"):
                self.assertIn((name, ground), pairs)
        for ground in ("color.background", "color.selected"):
            self.assertIn(("color.subject", ground), pairs)

    def test_line_heights_and_weights_are_sane(self) -> None:
        for name, (size, line, weight) in self.types.items():
            if name == "type.postDetails":
                self.assertEqual(size, 10, "Clover's verified phone post details use 10sp")
            else:
                self.assertGreaterEqual(size, 12, f"{name} is smaller than 12sp")
            if line is None:
                self.assertIn(name, {"type.postBody", "type.postBodyWide", "type.postDetails", "type.postDetailsWide"})
            else:
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
        self.assertGreaterEqual(m["size.appBarAction"], m["size.touchTarget"])
        self.assertGreaterEqual(m["size.appBarNavigation"], m["size.touchTarget"])
        # Clover's overflow is narrower, while retaining the full 56dp control height.
        self.assertEqual(m["size.appBarOverflow"], 44)
        self.assertGreaterEqual(m["size.appBarOverflow"], m["size.icon"])


class LayoutTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.spec = Spec()
        cls.types = cls.spec.types()
        cls.m = cls.spec.measures()

    def test_legacy_shared_cell_minimum_is_thumbnail_plus_padding(self) -> None:
        m = self.m
        self.assertEqual(m["size.cellMin"], m["size.thumbnail"] + 2 * m["space.rowPadY"])
        self.assertEqual(m["size.cellMinWide"], m["size.thumbnailWide"] + 2 * m["space.rowPadY"])
        self.assertEqual(m["space.rowPadY"], m["space.postPadY"], "legacy shared cells retain matching vertical padding")
        self.assertGreater(m["size.thumbnailWide"], m["size.thumbnail"])

    def test_legacy_fixed_height_text_fits_beside_the_thumbnail(self) -> None:
        text_height = (
            self.types["type.rowTitle"][1] + self.m["space.line"] + self.types["type.meta"][1]
            + self.m["space.line"] + self.types["type.preview"][1]
        )
        self.assertLessEqual(text_height, self.m["size.thumbnail"])

    def test_compact_post_metrics_match_the_verified_phone_and_tablet_defaults(self) -> None:
        for suffix, expected_size in (("", 14), ("Wide", 16)):
            body = self.types[f"type.postBody{suffix}"]
            details = self.types[f"type.postDetails{suffix}"]
            inset = self.m[f"space.postInset{suffix}"]
            self.assertEqual(body, (expected_size, None, 400))
            self.assertEqual(details, (expected_size - 4, None, 400))
            self.assertEqual(inset, expected_size - 6)
        self.assertEqual(self.m["size.thumbnail"], 72)
        self.assertEqual(self.m["size.thumbnailWide"], 100)
        self.assertEqual(self.m["size.hairline"], 1)
        self.assertEqual(self.m["size.threadStatus"], 88)

    def test_row_budgets_add_up_and_quote_their_tokens(self) -> None:
        budgets = self.spec.find(["Part", "Token", "Width"])
        self.assertEqual(len(budgets), 2)
        catalog_pane_at_960 = self._pane_row(960)[0]
        for rows, total, suffix in zip(budgets, (360, catalog_pane_at_960), ("", "Wide")):
            self.assertEqual(len(rows), 4, "compact rows reserve only the image, two text insets and the text column")
            self.assertEqual(sum(number(row[2]) for row in rows), total)
            remainders = [row for row in rows if row[1] == "remainder"]
            self.assertEqual(len(remainders), 1)
            self.assertGreater(number(remainders[0][2]), 150, "the text column is too narrow to read")
            for part, token, width in rows:
                if token != "remainder":
                    self.assertEqual(number(width), self.m[token], f"{part} does not match {token}")
            self.assertEqual([row[1] for row in rows], [f"size.thumbnail{suffix}", f"space.postInset{suffix}", "remainder", f"space.postInset{suffix}"])
            self.assertEqual(number(remainders[0][2]), total - self.m[f"size.thumbnail{suffix}"] - 2 * self.m[f"space.postInset{suffix}"])

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
        self.assertEqual(m["size.drawerMax"], 336)
        for window in (360, 411, 960):
            width = min(window - m["size.appBar"], m["size.drawerMax"])
            self.assertGreater(width, 0)
            self.assertLessEqual(width, window - m["size.appBar"])
        self.assertEqual(min(360 - m["size.appBar"], m["size.drawerMax"]), 304)
        self.assertEqual(min(411 - m["size.appBar"], m["size.drawerMax"]), 336)

    def test_toolbar_uses_the_verified_control_widths(self) -> None:
        self.assertEqual(
            tuple(self.m[f"size.{name}"] for name in ("appBar", "appBarNavigation", "appBarAction", "appBarOverflow")),
            (56, 56, 50, 44),
        )

    GRID_HEADERS = ["Viewport width", "Viewport height", "Columns", "Image height", "Card width"]

    def test_grid_examples_follow_the_rule(self) -> None:
        m = self.m
        rows = self.spec.one(self.GRID_HEADERS)
        viewports = [(number(row[0]), number(row[1])) for row in rows]
        self.assertIn((360, 560), viewports)
        for viewport_width, viewport_height, columns, image, card in rows:
            width, height = number(viewport_width), number(viewport_height)
            expected_image = max(height * m["ratio.gridImageHeight"], m["size.gridCardMin"])
            self.assertAlmostEqual(number(image), expected_image, places=2, msg=f"image at {width}x{height}")
            expected_columns = max(1, int(width // expected_image))
            self.assertEqual(int(columns), expected_columns, f"columns at {width}x{height}")
            expected_card = (width - 2 * m["space.gridOuter"] - (expected_columns - 1) * m["space.gridGap"]) / expected_columns
            self.assertAlmostEqual(number(card), expected_card, places=2, msg=f"card width at {width}x{height}")

    def test_grid_examples_cover_portrait_wide_and_floored_viewports(self) -> None:
        m = self.m
        rows = self.spec.one(self.GRID_HEADERS)
        columns = [int(row[2]) for row in rows]
        images = [number(row[3]) for row in rows]
        heights = [number(row[1]) for row in rows]
        self.assertIn(1, columns, "no portrait example with one full-width column")
        self.assertTrue(any(count > 1 for count in columns), "no wide example with several columns")
        self.assertTrue(
            any(image == m["size.gridCardMin"] and height * m["ratio.gridImageHeight"] < image for image, height in zip(images, heights)),
            "no example is held at the minimum image height",
        )

    def test_grid_card_text_minimum_fits_two_title_lines_and_metadata(self) -> None:
        m, types = self.m, self.types
        content = 2 * m["space.cardPad"] + 2 * types["type.cardTitle"][1] + m["space.line"] + types["type.meta"][1]
        self.assertEqual(m["size.gridCardText"], content)

    def test_grid_images_are_half_the_viewport_and_fit_two_lines(self) -> None:
        m = self.m
        self.assertEqual(m["ratio.gridImageHeight"], 1 / 2)
        self.assertEqual(m["space.gridOuter"], 2)
        self.assertEqual(m["space.gridGap"], 4)
        self.assertEqual(m["radius.card"], 2)
        self.assertEqual(m["size.cardElevation"], 2)
        for row in self.spec.one(self.GRID_HEADERS):
            height, columns, image, card = number(row[1]), int(row[2]), number(row[3]), number(row[4])
            self.assertGreaterEqual(image, m["size.gridCardMin"])
            if image > m["size.gridCardMin"]:
                self.assertAlmostEqual(2 * image, height, places=4, msg="two images fill the viewport")
            if columns > 1:
                self.assertGreaterEqual(card + m["space.gridGap"], image * 0.9, "a multi-column image is roughly as wide as tall")
            self.assertGreater(card, m["size.touchTarget"])

    def test_album_cells_have_one_dp_margins_and_the_32dp_metadata_strip(self) -> None:
        m = self.m
        self.assertEqual(m["space.albumPadding"], 1)
        self.assertEqual(m["space.albumCellMargin"], 1)
        self.assertEqual(m["size.albumDetails"], 32)
        for pane in (360, 411, 600):
            columns = max(1, int(pane // m["size.gridCardMin"]))
            cell = (pane - 2 * m["space.albumPadding"] - 2 * columns * m["space.albumCellMargin"]) / columns
            outside = m["space.albumPadding"] + m["space.albumCellMargin"]
            gap = 2 * m["space.albumCellMargin"]
            self.assertAlmostEqual(columns * cell + (columns - 1) * gap + 2 * outside, pane)
            self.assertGreater(cell, m["size.touchTarget"])
            self.assertAlmostEqual(cell + m["size.albumDetails"] - cell, 32)


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

    def test_diagrams_do_not_treat_android_argb_as_css_rgba(self) -> None:
        # SVG/CSS eight-digit hex means RRGGBBAA, unlike the token tables' AARRGGBB.
        # The companion diagrams use a token's RGB base with fill/stroke opacity instead.
        css_argb = re.compile(r"\b(?:fill|stroke|color)\s*(?:=|:)\s*[\"']?#[0-9A-Fa-f]{8}\b")
        for path in self.svgs:
            self.assertIsNone(css_argb.search(path.read_text(encoding="utf-8")), path.name)

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
