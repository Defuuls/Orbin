#!/usr/bin/env python3
"""Rebuild launcher, splash, notification and preview assets from the card-stack mark.

Source of truth, written by this script and committed:

* docs/assets/orbin-icon-mark.svg — large foreground (thread lines, original proportions)
* docs/assets/orbin-icon-mark-small.svg — small foreground (no lines; front card and
  play triangle scaled 10% about the front-card center)

Exports at 48px and under (mdpi 48px fallbacks, the 24dp notification icon, the
site favicon) and the Android 13+ monochrome themed-icon layer use the small
mark. Everything larger uses the large mark.

The mark is two offset rounded cards on coral #FF5D68: a dark-coral back card
and a white front card with a coral play-triangle cut into it. There are no
circles, rings, targets or letter O.

Requires cairosvg and Pillow. Run from any directory:

    python3 scripts/generate_launcher_icons.py
    python3 scripts/generate_launcher_icons.py --check
"""
from __future__ import annotations

import argparse
import io
import math
from pathlib import Path

import cairosvg
from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / "app/src/main/res"
CORAL = "#FF5D68"
DARK = "#B8323F"
WHITE = "#FFFFFF"
BLACK = "#000000"

# Variant B geometry in a 1024 canvas (from /workspace/orbin-icon-v2/variant-B.svg).
BACK = dict(x=334.0, y=140.0, w=500.0, h=600.0, r=80.0)
FRONT = dict(x=190.0, y=300.0, w=500.0, h=600.0, r=80.0)
PLAY = ((370.2, 613.0), (370.2, 803.0), (545.0, 708.0))
LINES = (
    dict(x=260.0, y=370.0, w=300.0, h=34.0, r=17.0),
    dict(x=260.0, y=434.0, w=220.0, h=34.0, r=17.0),
)
LINE_OPACITY = 0.9
SMALL_SCALE = 1.10  # front card + play triangle

# Adaptive icon: 108dp canvas, 66dp safe-zone circle (radius 33). 72dp is the
# typical visible face after the launcher mask, used for legacy and iOS PNGs.
VIEWPORT = 108.0
SAFE_RADIUS = 33.0
SAFE_PAD = 0.93  # keep a little clearance inside the 66dp circle
FACE = 72.0


def _scale_pt(pt, center, s):
    cx, cy = center
    x, y = pt
    return (cx + s * (x - cx), cy + s * (y - cy))


def _scale_rect(rect, center, s):
    cx, cy = center
    x, y, w, h, r = rect["x"], rect["y"], rect["w"], rect["h"], rect["r"]
    # Scale about the given center, not the rect's own origin.
    corners = [_scale_pt((x, y), center, s), _scale_pt((x + w, y + h), center, s)]
    nx, ny = corners[0]
    return dict(x=nx, y=ny, w=w * s, h=h * s, r=r * s)


def small_front_and_play():
    """Front card + play triangle scaled 10% about the front-card center."""
    fx, fy, fw, fh = FRONT["x"], FRONT["y"], FRONT["w"], FRONT["h"]
    center = (fx + fw / 2.0, fy + fh / 2.0)
    return _scale_rect(FRONT, center, SMALL_SCALE), tuple(
        _scale_pt(p, center, SMALL_SCALE) for p in PLAY
    )


def rounded_rect_d(x, y, w, h, r) -> str:
    r = min(r, w / 2.0, h / 2.0)
    return (
        f"M{x + r:.3f},{y:.3f} "
        f"H{x + w - r:.3f} "
        f"A{r:.3f},{r:.3f} 0 0 1 {x + w:.3f},{y + r:.3f} "
        f"V{y + h - r:.3f} "
        f"A{r:.3f},{r:.3f} 0 0 1 {x + w - r:.3f},{y + h:.3f} "
        f"H{x + r:.3f} "
        f"A{r:.3f},{r:.3f} 0 0 1 {x:.3f},{y + h - r:.3f} "
        f"V{y + r:.3f} "
        f"A{r:.3f},{r:.3f} 0 0 1 {x + r:.3f},{y:.3f} Z"
    )


def rect_d(rect) -> str:
    return rounded_rect_d(rect["x"], rect["y"], rect["w"], rect["h"], rect["r"])


def polygon_d(pts) -> str:
    return "M" + " L".join(f"{x:.3f},{y:.3f}" for x, y in pts) + " Z"


def sample_rounded_rect(rect, n=32):
    x, y, w, h, r = rect["x"], rect["y"], rect["w"], rect["h"], rect["r"]
    r = min(r, w / 2.0, h / 2.0)
    corners = (
        (x + w - r, y + r, -math.pi / 2, 0.0),
        (x + w - r, y + h - r, 0.0, math.pi / 2),
        (x + r, y + h - r, math.pi / 2, math.pi),
        (x + r, y + r, math.pi, 3 * math.pi / 2),
    )
    pts = []
    for cx, cy, a0, a1 in corners:
        for i in range(n + 1):
            t = a0 + (a1 - a0) * i / n
            pts.append((cx + r * math.cos(t), cy + r * math.sin(t)))
    return pts


def mark_geometry(small: bool):
    back = dict(BACK)
    if small:
        front, play = small_front_and_play()
        lines = ()
    else:
        front, play, lines = dict(FRONT), PLAY, LINES
    outline = sample_rounded_rect(back) + sample_rounded_rect(front)
    xs = [p[0] for p in outline]
    ys = [p[1] for p in outline]
    cx = (min(xs) + max(xs)) / 2.0
    cy = (min(ys) + max(ys)) / 2.0
    radius = max(math.hypot(x - cx, y - cy) for x, y in outline)
    return {
        "back": back,
        "front": front,
        "play": play,
        "lines": lines,
        "cx": cx,
        "cy": cy,
        "radius": radius,
        "small": small,
    }


def adaptive_scale(geom) -> float:
    return (SAFE_RADIUS * SAFE_PAD) / geom["radius"]


def mark_svg(geom, *, background=None, mono=False) -> str:
    """SVG in the original 1024 viewBox. background=None means transparent."""
    parts = [
        '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1024 1024">',
    ]
    if background:
        parts.append(f'<rect width="1024" height="1024" fill="{background}"/>')
    if mono:
        back_fill, back_op, front_fill, play_fill, line_fill = (
            BLACK,
            "0.5",
            BLACK,
            WHITE,
            WHITE,
        )
    else:
        back_fill, back_op, front_fill, play_fill, line_fill = (
            DARK,
            "1",
            WHITE,
            CORAL,
            CORAL,
        )
    parts.append(
        f'<path fill="{back_fill}" fill-opacity="{back_op}" d="{rect_d(geom["back"])}"/>'
    )
    if mono:
        # Triangle is a hole so a tinted monochrome layer stays a notch.
        combined = rect_d(geom["front"]) + " " + polygon_d(geom["play"])
        parts.append(
            f'<path fill="{front_fill}" fill-rule="evenodd" d="{combined}"/>'
        )
    else:
        parts.append(f'<path fill="{front_fill}" d="{rect_d(geom["front"])}"/>')
        parts.append(f'<path fill="{play_fill}" d="{polygon_d(geom["play"])}"/>')
        for line in geom["lines"]:
            parts.append(
                f'<path fill="{line_fill}" fill-opacity="{LINE_OPACITY}" d="{rect_d(line)}"/>'
            )
    parts.append("</svg>")
    return "".join(parts) + "\n"


def vector_xml(geom, *, viewport=108.0, center=54.0, scale=None, mono=False, color=None) -> bytes:
    """Android VectorDrawable. Color mark uses coral triangle; mono cuts a hole."""
    if scale is None:
        scale = adaptive_scale(geom)
    cx, cy = geom["cx"], geom["cy"]
    comment = (
        "Small card-stack mark (no thread lines; front card and play triangle +10%)."
        if geom["small"]
        else "Large card-stack mark (thread lines, original proportions)."
    )
    role = (
        "White silhouette; the play triangle is a hole so the system tint shows through."
        if mono
        else "Fits inside the 66dp safe zone of the 108dp adaptive canvas."
    )
    lines = [
        '<?xml version="1.0" encoding="utf-8"?>',
        '<vector xmlns:android="http://schemas.android.com/apk/res/android"',
        f'    android:width="{viewport:.0f}dp" android:height="{viewport:.0f}dp"',
        f'    android:viewportWidth="{viewport:g}" android:viewportHeight="{viewport:g}">',
        f"    <!-- {comment} {role}",
        "         Derived from docs/assets/orbin-icon-mark"
        + ("-small" if geom["small"] else "")
        + ".svg. -->",
        f'    <group android:translateX="{center:g}" android:translateY="{center:g}">',
        f'        <group android:scaleX="{scale:.8f}" android:scaleY="{scale:.8f}">',
        f'            <group android:translateX="{-cx:.4f}" android:translateY="{-cy:.4f}">',
    ]
    if mono:
        fill = color or WHITE
        lines.append(
            f'                <path android:fillColor="{fill}" android:fillAlpha="0.5"'
        )
        lines.append(f'                    android:pathData="{rect_d(geom["back"])}" />')
        combined = rect_d(geom["front"]) + " " + polygon_d(geom["play"])
        lines.append(
            f'                <path android:fillColor="{fill}" android:fillType="evenOdd"'
        )
        lines.append(f'                    android:pathData="{combined}" />')
    else:
        lines.append(
            f'                <path android:fillColor="{DARK}"'
        )
        lines.append(f'                    android:pathData="{rect_d(geom["back"])}" />')
        lines.append(
            f'                <path android:fillColor="{WHITE}"'
        )
        lines.append(f'                    android:pathData="{rect_d(geom["front"])}" />')
        lines.append(
            f'                <path android:fillColor="{CORAL}"'
        )
        lines.append(f'                    android:pathData="{polygon_d(geom["play"])}" />')
        for line in geom["lines"]:
            lines.append(
                f'                <path android:fillColor="{CORAL}" android:fillAlpha="0.9"'
            )
            lines.append(f'                    android:pathData="{rect_d(line)}" />')
    lines.append("            </group>")
    lines.append("        </group>")
    lines.append("    </group>")
    lines.append("</vector>")
    lines.append("")
    return ("\n".join(lines)).encode()


def face_svg(geom, size_units=FACE, background=CORAL) -> str:
    """Square 'visible face' (the central 72dp of the 108dp adaptive canvas)."""
    s = adaptive_scale(geom)
    cx, cy = geom["cx"], geom["cy"]
    bg = f'<rect width="{size_units:g}" height="{size_units:g}" fill="{background}"/>' if background else ""
    # 72 face is the center crop of the 108 viewport, so the mark center sits at 36,36
    # and the scale is the same 108-space scale.
    return (
        f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {size_units:g} {size_units:g}">'
        f"{bg}"
        f'<g transform="translate({size_units/2:g} {size_units/2:g}) '
        f'scale({s:.8f}) translate({-cx:.4f} {-cy:.4f})">'
        f"{_mark_paths(geom)}"
        f"</g></svg>"
    )


def _mark_paths(geom, mono=False) -> str:
    if mono:
        combined = rect_d(geom["front"]) + " " + polygon_d(geom["play"])
        return (
            f'<path fill="{BLACK}" fill-opacity="0.5" d="{rect_d(geom["back"])}"/>'
            f'<path fill="{BLACK}" fill-rule="evenodd" d="{combined}"/>'
        )
    chunks = [
        f'<path fill="{DARK}" d="{rect_d(geom["back"])}"/>',
        f'<path fill="{WHITE}" d="{rect_d(geom["front"])}"/>',
        f'<path fill="{CORAL}" d="{polygon_d(geom["play"])}"/>',
    ]
    for line in geom["lines"]:
        chunks.append(
            f'<path fill="{CORAL}" fill-opacity="{LINE_OPACITY}" d="{rect_d(line)}"/>'
        )
    return "".join(chunks)


def launch_mark_svg(geom) -> str:
    """Mark only, transparent ground, fitted to fill most of a 1024 canvas."""
    # Use a slightly tighter pad than the adaptive safe zone so the launch
    # logo has similar weight to the previous 80pt orbit artwork.
    s = (512 * 0.86) / geom["radius"]
    cx, cy = geom["cx"], geom["cy"]
    return (
        '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1024 1024">'
        f'<g transform="translate(512 512) scale({s:.8f}) translate({-cx:.4f} {-cy:.4f})">'
        f"{_mark_paths(geom)}"
        f"</g></svg>"
    )


def render_svg(svg: str, size: int) -> Image.Image:
    raw = cairosvg.svg2png(
        bytestring=svg.encode(), output_width=size, output_height=size
    )
    return Image.open(io.BytesIO(raw)).convert("RGBA")


def encode_png(im: Image.Image, *, rgb=False) -> bytes:
    if rgb:
        if im.mode != "RGB":
            bg = Image.new("RGB", im.size, (255, 93, 104))  # coral, in case of residual alpha
            if im.mode == "RGBA":
                bg.paste(im, mask=im.split()[-1])
                im = bg
            else:
                im = im.convert("RGB")
        # Guarantee no alpha channel in the file.
        im = im.convert("RGB")
    else:
        im = im.convert("RGBA")
    buf = io.BytesIO()
    im.save(buf, format="PNG", optimize=True)
    return buf.getvalue()


def circle_mask(im: Image.Image) -> Image.Image:
    m = Image.new("L", im.size, 0)
    ImageDraw.Draw(m).ellipse((0, 0, im.size[0] - 1, im.size[1] - 1), fill=255)
    out = Image.new("RGBA", im.size, (0, 0, 0, 0))
    out.paste(im, (0, 0), im if im.mode == "RGBA" else None)
    out.putalpha(m)
    # Preserve interior alpha of the source (round PNGs of an opaque face are fine).
    if im.mode == "RGBA":
        src_a = im.split()[-1]
        out.putalpha(ImageChops_multiply(src_a, m))
    return out


def ImageChops_multiply(a, b):
    from PIL import ImageChops

    return ImageChops.multiply(a, b)


def squircle_mask(im: Image.Image, radius_ratio=0.22) -> Image.Image:
    m = Image.new("L", im.size, 0)
    r = max(1, int(min(im.size) * radius_ratio))
    ImageDraw.Draw(m).rounded_rectangle(
        (0, 0, im.size[0] - 1, im.size[1] - 1), r, fill=255
    )
    out = Image.new("RGBA", im.size, (0, 0, 0, 0))
    if im.mode == "RGBA":
        from PIL import ImageChops

        out.paste(im, (0, 0), ImageChops.multiply(im.split()[-1], m))
    else:
        out.paste(im, (0, 0), m)
    return out


def _font(size):
    for path in (
        "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf",
        "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",
    ):
        try:
            return ImageFont.truetype(path, size)
        except OSError:
            continue
    return ImageFont.load_default()


def contact_sheet() -> Image.Image:
    """Preview both mark versions at several sizes, plus masks and monochrome."""
    large = mark_geometry(False)
    small = mark_geometry(True)
    W, H = 1600, 980
    sheet = Image.new("RGB", (W, H), "#EDEDED")
    dr = ImageDraw.Draw(sheet)
    title = _font(28)
    label = _font(18)
    caption = _font(16)
    # Dark lower band for contrast row
    dr.rectangle((0, 620, W, H), fill="#1C1C1E")

    def paste_face(geom, px, xy, mask="squircle"):
        im = render_svg(face_svg(geom), px * 4).resize((px, px), Image.Resampling.LANCZOS)
        if mask == "circle":
            im = circle_mask(im)
        elif mask == "squircle":
            im = squircle_mask(im)
        sheet.paste(im, xy, im if im.mode == "RGBA" else None)

    def paste_mono(geom, px, xy):
        svg = (
            f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {FACE:g} {FACE:g}">'
            f'<rect width="{FACE:g}" height="{FACE:g}" fill="{WHITE}"/>'
            f'<g transform="translate({FACE/2:g} {FACE/2:g}) scale({adaptive_scale(geom):.8f}) '
            f'translate({-geom["cx"]:.4f} {-geom["cy"]:.4f})">{_mark_paths(geom, mono=True)}</g></svg>'
        )
        im = squircle_mask(
            render_svg(svg, px * 4).resize((px, px), Image.Resampling.LANCZOS), 0.22
        )
        sheet.paste(im, xy, im)

    # Header
    dr.text((32, 16), "Orbin card-stack icon", fill="#111", font=title)
    dr.text(
        (32, 52),
        "Large (with thread lines) for 49px and up.  Small (no lines, front card + play +10%) "
        "for 48px and under, and for the monochrome themed-icon layer.",
        fill="#333",
        font=caption,
    )

    rows = (
        ("Large — thread lines, original proportions", large, 100, "#111"),
        ("Small — no lines, front card + play triangle +10%", small, 360, "#111"),
    )
    for heading, geom, y, color in rows:
        dr.text((32, y - 8), heading, fill=color, font=label)
        paste_face(geom, 192, (32, y + 24), "squircle")
        dr.text((32, y + 24 + 192 + 6), "192 squircle", fill=color, font=caption)
        x = 260
        for s in (96, 48, 24):
            paste_face(geom, s, (x, y + 24 + (192 - s) // 2), "squircle")
            dr.text((x, y + 24 + 192 + 6), f"{s}px", fill=color, font=caption)
            x += s + 48
        paste_face(geom, 160, (x, y + 24 + 16), "circle")
        dr.text((x, y + 24 + 192 + 6), "circle mask", fill=color, font=caption)
        x += 200
        paste_mono(geom if geom is small else small, 160, (x, y + 24 + 16))
        dr.text((x, y + 24 + 192 + 6), "monochrome (small)", fill=color, font=caption)

    # Dark row: launch mark, notification, iOS face, themed examples
    y = 660
    color = "#EEE"
    dr.text((32, y - 24), "Launch mark, notification, iOS 1024 face, 24px small vs large", fill=color, font=label)

    lm = render_svg(launch_mark_svg(large), 192)
    # On dark, transparent launch mark sits naturally
    sheet.paste(lm, (32, y + 16), lm)
    dr.text((32, y + 16 + 192 + 4), "LaunchMark (large)", fill=color, font=caption)

    # Notification-like 24 and 48 white silhouettes on dark
    notif_svg_24 = vector_preview_notif(small, 192)
    sheet.paste(notif_svg_24, (260, y + 16 + (192 - 96) // 2), notif_svg_24)
    dr.text((260, y + 16 + 192 + 4), "notification 24 (shown 96)", fill=color, font=caption)

    ios = squircle_mask(render_svg(face_svg(large), 192), 0.22)
    sheet.paste(ios, (520, y + 16), ios)
    dr.text((520, y + 16 + 192 + 4), "iOS / Play face (large)", fill=color, font=caption)

    # 24px comparison large vs small, magnified
    for i, (name, geom) in enumerate((("large@24", large), ("small@24  ← used", small))):
        im = render_svg(face_svg(geom), 24)
        mag = im.resize((96, 96), Image.Resampling.NEAREST)
        mag = squircle_mask(mag, 0.22)
        sheet.paste(mag, (780 + i * 160, y + 16 + 48), mag)
        dr.text((780 + i * 160, y + 16 + 192 + 4), name, fill=color, font=caption)

    return sheet


def vector_preview_notif(geom, size) -> Image.Image:
    """White silhouette of the small mark, as the notification icon would look."""
    s = adaptive_scale(geom)
    # 24dp canvas with ~2dp padding → scale relative to radius 10
    notif_scale = (10.0) / geom["radius"]
    # We just preview the white mark on dark, fitted in a 24-unit box upscaled.
    svg = (
        f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24">'
        f'<g transform="translate(12 12) scale({notif_scale:.8f}) '
        f'translate({-geom["cx"]:.4f} {-geom["cy"]:.4f})">'
        f'<path fill="#FFFFFF" fill-opacity="0.5" d="{rect_d(geom["back"])}"/>'
        f'<path fill="#FFFFFF" fill-rule="evenodd" d="{rect_d(geom["front"])} {polygon_d(geom["play"])}"/>'
        f"</g></svg>"
    )
    return render_svg(svg, size)


def assets():
    large = mark_geometry(False)
    small = mark_geometry(True)

    yield Path("docs/assets/orbin-icon-mark.svg"), mark_svg(large).encode()
    yield Path("docs/assets/orbin-icon-mark-small.svg"), mark_svg(small).encode()
    yield Path("docs/assets/orbin-icon-favicon.svg"), face_svg(small, size_units=64).encode()

    # Android adaptive vectors
    yield RES / "drawable/ic_launcher_foreground.xml", vector_xml(large, mono=False)
    yield RES / "drawable/ic_launcher_monochrome.xml", vector_xml(small, mono=True, color=WHITE)

    # 24dp notification: white silhouette of the small mark, 2dp padding.
    notif_scale = 10.0 / small["radius"]
    yield (
        ROOT / "data/src/main/res/drawable/ic_stat_notify_thread.xml",
        vector_xml(small, viewport=24.0, center=12.0, scale=notif_scale, mono=True, color=WHITE),
    )

    # High-res faces for PNG derivatives.
    def face_png(geom, size, rounded=False):
        # Render the 72 face at 8x the target, then downsample.
        master = render_svg(face_svg(geom), max(size * 8, 1024))
        im = master.resize((size, size), Image.Resampling.LANCZOS)
        if rounded:
            return circle_mask(im)
        return im

    densities = (
        ("mdpi", 48, True),   # small mark
        ("hdpi", 72, False),
        ("xhdpi", 96, False),
        ("xxhdpi", 144, False),
        ("xxxhdpi", 192, False),
    )
    for density, size, use_small in densities:
        geom = small if use_small else large
        square = face_png(geom, size, rounded=False)
        yield RES / f"mipmap-{density}/ic_launcher.png", encode_png(square, rgb=True)
        yield RES / f"mipmap-{density}/ic_launcher_round.png", encode_png(
            face_png(geom, size, rounded=True), rgb=False
        )

    # iOS 1024 AppIcon: large mark, opaque RGB, no alpha, not pre-rounded.
    ios = face_png(large, 1024, rounded=False)
    yield (
        ROOT / "iosApp/Orbin/Assets.xcassets/AppIcon.appiconset/AppIcon.png",
        encode_png(ios, rgb=True),
    )

    # iOS LaunchMark: large mark, transparent ground, 80 / 160 / 240.
    launch = launch_mark_svg(large)
    for name, size in (
        ("LaunchMark.png", 80),
        ("LaunchMark@2x.png", 160),
        ("LaunchMark@3x.png", 240),
    ):
        im = render_svg(launch, size * 4).resize((size, size), Image.Resampling.LANCZOS)
        yield (
            ROOT / "iosApp/Orbin/Assets.xcassets/LaunchMark.imageset" / name,
            encode_png(im, rgb=False),
        )

    # Contact sheets.
    sheet = contact_sheet()
    buf = io.BytesIO()
    sheet.save(buf, format="PNG", optimize=True)
    preview_bytes = buf.getvalue()
    yield Path("docs/assets/card-stack-icon-preview.png"), preview_bytes
    yield Path("docs/assets/launcher-icons-after.png"), preview_bytes


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()
    mismatches = []
    wrote = []
    for relative, content in assets():
        target = ROOT / relative if not relative.is_absolute() else relative
        # assets() already yields absolute RES paths and some relative docs paths.
        if not target.is_absolute():
            target = ROOT / target
        target.parent.mkdir(parents=True, exist_ok=True)
        if args.check:
            if not target.exists() or target.read_bytes() != content:
                mismatches.append(str(target.relative_to(ROOT) if target.is_relative_to(ROOT) else target))
        else:
            target.write_bytes(content)
            wrote.append(str(target.relative_to(ROOT) if target.is_relative_to(ROOT) else target))
    if mismatches:
        raise SystemExit("Launcher assets need regeneration: " + ", ".join(mismatches))
    if args.check:
        print("Launcher assets match the card-stack mark.")
    else:
        print("Generated launcher assets from the card-stack mark:")
        for w in wrote:
            print("  ", w)


if __name__ == "__main__":
    main()
