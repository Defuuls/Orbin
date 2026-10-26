#!/usr/bin/env python3
"""Rebuild launcher assets from the README hero. Requires Inkscape and Pillow.

Run from any directory; --check compares generated assets without writing them.
"""
from __future__ import annotations

import argparse
import io
import subprocess
import tempfile
import xml.etree.ElementTree as ET
from pathlib import Path

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
RES = Path('app/src/main/res')
SVG = '{http://www.w3.org/2000/svg}'
BEAM = '#E8E4DC'
VOID = '#0A0A0C'


def assets():
    hero = ET.parse(ROOT / 'docs/assets/orbin-brand-showcase.svg')
    mark = hero.find(f'.//{SVG}g[@id="aperture-mark-vector"]')
    assert mark is not None and mark.attrib['fill'] == BEAM
    paths = [p.attrib['d'] for p in mark.findall(f'{SVG}path')]
    assert len(paths) == 5
    # Preserve every original coordinate and overlapping path, including the center hole.
    geometry = ''.join(f'<path d="{p}"/>' for p in paths)
    vector_header = '''<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp" android:height="108dp"
    android:viewportWidth="108" android:viewportHeight="108">
    <!-- Generated from aperture-mark-vector in docs/assets/orbin-brand-showcase.svg.
         Radius 30dp fits inside the adaptive icon's 33dp safe-zone radius. -->
    <group android:translateX="54" android:translateY="54"
        android:scaleX="0.3" android:scaleY="0.3">
'''
    for name, color in [('ic_launcher_aperture_foreground', BEAM), ('ic_launcher_monochrome', '#FFFFFF')]:
        body = ''.join(f'        <path android:fillColor="{color}"\n            android:pathData="{p}" />\n' for p in paths)
        yield RES / f'drawable/{name}.xml', (vector_header + body + '    </group>\n</vector>\n').encode()

    # A 72-unit final face corresponds to the adaptive layer's central 72/108 area.
    # The same 60-unit mark is used on iOS and in legacy launcher icons.
    source = f'''<svg xmlns="http://www.w3.org/2000/svg" width="1024" height="1024" viewBox="0 0 72 72">
<rect width="72" height="72" fill="{VOID}"/>
<g transform="translate(36 36) scale(.3)" fill="{BEAM}">{geometry}</g></svg>'''
    with tempfile.TemporaryDirectory() as tmp:
        src = Path(tmp) / 'icon.svg'
        png = Path(tmp) / 'icon.png'
        src.write_text(source)
        subprocess.run(['inkscape', str(src), '--export-width=2048', '--export-height=2048', f'--export-filename={png}'], check=True, capture_output=True)
        master = Image.open(png).convert('RGB')
        def encode(im):
            buf = io.BytesIO()
            im.save(buf, format='PNG', optimize=True)
            return buf.getvalue()
        yield Path('iosApp/Orbin/Assets.xcassets/AppIcon.appiconset/AppIcon.png'), encode(master.resize((1024, 1024), Image.Resampling.LANCZOS))
        for density, size in [('mdpi', 48), ('hdpi', 72), ('xhdpi', 96), ('xxhdpi', 144), ('xxxhdpi', 192)]:
            icon = master.resize((size, size), Image.Resampling.LANCZOS)
            yield RES / f'mipmap-{density}/ic_launcher.png', encode(icon)
            mask = Image.new('L', master.size)
            ImageDraw.Draw(mask).ellipse((0, 0, 2047, 2047), fill=255)
            rounded = master.convert('RGBA')
            rounded.putalpha(mask)
            yield RES / f'mipmap-{density}/ic_launcher_round.png', encode(rounded.resize((size, size), Image.Resampling.LANCZOS))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--check', action='store_true')
    args = parser.parse_args()
    mismatches = []
    for relative, content in assets():
        target = ROOT / relative
        if args.check:
            if not target.exists() or target.read_bytes() != content:
                mismatches.append(str(relative))
        else:
            target.write_bytes(content)
    if mismatches:
        raise SystemExit('Launcher assets need regeneration: ' + ', '.join(mismatches))
    print('Launcher assets match the hero.' if args.check else 'Generated launcher assets from the hero.')


if __name__ == '__main__':
    main()
