#!/usr/bin/env node
/*
 * Visual regression check for docs/design-system.
 *
 * Renders every diagram and the companion page in light and dark, at phone and desktop
 * widths, and compares each with its committed baseline in docs/design-system/baselines.
 *
 *   node scripts/design_docs_visual.cjs            compare; exit 1 on any difference
 *   node scripts/design_docs_visual.cjs --update   rewrite the baselines
 *
 * Text is forced into a font bundled in this repository and all network requests are blocked,
 * so the result depends only on the Chromium build. Use the Playwright version named in
 * .github/workflows/design-docs.yml. Failing renders are written to build/design-docs-visual.
 */
'use strict';

const fs = require('fs');
const path = require('path');
const { pathToFileURL } = require('url');
const { chromium } = require('playwright');

const ROOT = path.resolve(__dirname, '..');
const DESIGN_DIR = path.join(ROOT, 'docs', 'design-system');
const DIAGRAM_DIR = path.join(DESIGN_DIR, 'diagrams');
const BASELINE_DIR = path.join(DESIGN_DIR, 'baselines');
const FAILURE_DIR = path.join(ROOT, 'build', 'design-docs-visual');
const FONT_DIR = path.join(ROOT, 'ui-next', 'src', 'commonMain', 'composeResources', 'font');
const FONT_FILES = { 400: 'tiktok_sans_regular.ttf', 500: 'tiktok_sans_medium.ttf', 700: 'tiktok_sans_bold.ttf' };

const SCHEMES = ['light', 'dark'];
const WIDTHS = { phone: 390, desktop: 1280 };
// Text rasterization otherwise follows the machine's fontconfig (hinting, subpixel layout), which
// moves glyph edges and can change where a line wraps.
const STABLE_RENDERING_ARGS = [
  '--font-render-hinting=none',
  '--disable-font-subpixel-positioning',
  '--disable-lcd-text',
  '--force-color-profile=srgb',
];
// A pixel differs when any channel is further apart than this (0-255).
const CHANNEL_TOLERANCE = 40;
// A render fails when more than this share of its pixels differ.
const MAX_DIFFERENT_SHARE = 0.002;

function fontCss() {
  const faces = Object.entries(FONT_FILES).map(([weight, file]) => {
    const data = fs.readFileSync(path.join(FONT_DIR, file)).toString('base64');
    return `@font-face{font-family:'DocsTest';font-weight:${weight};src:url(data:font/ttf;base64,${data}) format('truetype')}`;
  });
  return `${faces.join('')}*{font-family:'DocsTest' !important}`;
}

function diagramNames() {
  return fs.readdirSync(DIAGRAM_DIR).filter((file) => file.endsWith('.svg')).sort();
}

function targets() {
  const list = [];
  for (const scheme of SCHEMES) {
    for (const [widthName, width] of Object.entries(WIDTHS)) {
      for (const file of diagramNames()) {
        list.push({ kind: 'diagram', file, scheme, width, name: `${file.replace('.svg', '')}-${scheme}-${widthName}.png` });
      }
      list.push({ kind: 'page', scheme, width, name: `schematic-${scheme}-${widthName}.png` });
    }
  }
  return list;
}

// Loads every test font face, then fails if any text would still be drawn with a system font.
// Without this a render taken before the faces decode, or a glyph the test font lacks, falls back
// to whatever the machine has installed and the comparison fails for a reason it cannot name.
async function settle(page) {
  const unloaded = await page.evaluate(async (weights) => {
    for (const weight of weights) await document.fonts.load(`${weight} 16px DocsTest`);
    await document.fonts.ready;
    await new Promise((resolve) => requestAnimationFrame(() => requestAnimationFrame(resolve)));
    return Array.from(document.fonts)
      .filter((face) => face.family.includes('DocsTest') && face.status !== 'loaded')
      .map((face) => `${face.weight}: ${face.status}`);
  }, Object.keys(FONT_FILES));
  if (unloaded.length > 0) throw new Error(`test font faces did not load: ${unloaded.join(', ')}`);

  const session = await page.context().newCDPSession(page);
  await session.send('DOM.enable');
  await session.send('CSS.enable');
  const { root } = await session.send('DOM.getDocument');
  const { nodeIds } = await session.send('DOM.querySelectorAll', { nodeId: root.nodeId, selector: 'body *' });
  const systemFonts = new Set();
  for (const nodeId of nodeIds) {
    const { fonts } = await session.send('CSS.getPlatformFontsForNode', { nodeId });
    for (const font of fonts) if (!font.isCustomFont) systemFonts.add(font.familyName);
  }
  await session.detach();
  if (systemFonts.size > 0) {
    throw new Error(`text fell back to system fonts (${Array.from(systemFonts).join(', ')}); the render would differ between machines`);
  }
}

async function renderDiagram(page, target, css) {
  const svg = fs.readFileSync(path.join(DIAGRAM_DIR, target.file), 'utf8');
  await page.setContent(
    `<!doctype html><html><head><meta charset="utf-8"><style>${css}html,body{margin:0}svg{display:block;max-width:100%;height:auto}</style></head><body>${svg}</body></html>`,
  );
  await settle(page);
  return page.locator('svg').screenshot();
}

async function renderPage(page, css) {
  await page.goto(pathToFileURL(path.join(DESIGN_DIR, 'schematic.html')).href);
  await page.addStyleTag({ content: css });
  // An <img> cannot take the test font, so each diagram is inlined in its place.
  const sources = {};
  for (const file of diagramNames()) sources[`diagrams/${file}`] = fs.readFileSync(path.join(DIAGRAM_DIR, file), 'utf8');
  const missing = await page.evaluate((map) => {
    const unknown = [];
    for (const img of Array.from(document.querySelectorAll('img'))) {
      const source = map[img.getAttribute('src')];
      if (!source) {
        unknown.push(img.getAttribute('src'));
        continue;
      }
      const holder = document.createElement('div');
      holder.innerHTML = source;
      const svg = holder.querySelector('svg');
      svg.setAttribute('class', `${svg.getAttribute('class')} ${img.className}`);
      img.replaceWith(svg);
    }
    return unknown;
  }, sources);
  if (missing.length > 0) throw new Error(`schematic.html shows images that are not diagrams: ${missing.join(', ')}`);
  await settle(page);
  return page.screenshot({ fullPage: true });
}

async function compare(page, expected, actual) {
  return page.evaluate(
    async ({ a, b, tolerance }) => {
      const load = async (base64) => createImageBitmap(await (await fetch(`data:image/png;base64,${base64}`)).blob());
      const [first, second] = await Promise.all([load(a), load(b)]);
      if (first.width !== second.width || first.height !== second.height) {
        return { sizeChanged: `${first.width}x${first.height} became ${second.width}x${second.height}` };
      }
      const pixels = (bitmap) => {
        const canvas = new OffscreenCanvas(bitmap.width, bitmap.height);
        const context = canvas.getContext('2d');
        context.drawImage(bitmap, 0, 0);
        return context.getImageData(0, 0, bitmap.width, bitmap.height).data;
      };
      const one = pixels(first);
      const two = pixels(second);
      let different = 0;
      for (let i = 0; i < one.length; i += 4) {
        if (
          Math.abs(one[i] - two[i]) > tolerance ||
          Math.abs(one[i + 1] - two[i + 1]) > tolerance ||
          Math.abs(one[i + 2] - two[i + 2]) > tolerance
        ) {
          different += 1;
        }
      }
      return { share: different / (one.length / 4) };
    },
    { a: expected.toString('base64'), b: actual.toString('base64'), tolerance: CHANNEL_TOLERANCE },
  );
}

async function main() {
  const update = process.argv.includes('--update');
  const unknownArgs = process.argv.slice(2).filter((arg) => arg !== '--update');
  if (unknownArgs.length > 0) throw new Error(`unknown argument: ${unknownArgs.join(' ')}`);

  const css = fontCss();
  const all = targets();
  const browser = await chromium.launch({ args: STABLE_RENDERING_ARGS });
  const failures = [];
  try {
    const judge = await (await browser.newContext()).newPage();
    await judge.goto('about:blank');
    for (const target of all) {
      const context = await browser.newContext({
        colorScheme: target.scheme,
        deviceScaleFactor: 1,
        viewport: { width: target.width, height: 800 },
      });
      // Nothing outside the repository may change a render.
      await context.route(/^https?:/, (route) => route.abort());
      const page = await context.newPage();
      const actual = target.kind === 'page' ? await renderPage(page, css) : await renderDiagram(page, target, css);
      await context.close();

      const baseline = path.join(BASELINE_DIR, target.name);
      if (update) {
        fs.mkdirSync(BASELINE_DIR, { recursive: true });
        fs.writeFileSync(baseline, actual);
        continue;
      }
      let problem = null;
      if (!fs.existsSync(baseline)) {
        problem = 'no baseline';
      } else {
        const result = await compare(judge, fs.readFileSync(baseline), actual);
        if (result.sizeChanged) problem = `size changed: ${result.sizeChanged}`;
        else if (result.share > MAX_DIFFERENT_SHARE) problem = `${(result.share * 100).toFixed(2)}% of pixels differ`;
      }
      if (problem) {
        fs.mkdirSync(FAILURE_DIR, { recursive: true });
        fs.writeFileSync(path.join(FAILURE_DIR, target.name), actual);
        failures.push(`${target.name}: ${problem}`);
      }
    }
  } finally {
    await browser.close();
  }

  const expectedNames = new Set(all.map((target) => target.name));
  const stale = fs.existsSync(BASELINE_DIR) ? fs.readdirSync(BASELINE_DIR).filter((file) => !expectedNames.has(file)) : [];
  if (update) {
    for (const file of stale) fs.unlinkSync(path.join(BASELINE_DIR, file));
    console.log(`wrote ${all.length} baselines${stale.length ? `, removed ${stale.length} stale` : ''}`);
    return;
  }
  for (const file of stale) failures.push(`${file}: baseline has no matching render`);
  if (failures.length > 0) {
    console.error(`${failures.length} of ${all.length} renders differ from their baselines:`);
    for (const failure of failures) console.error(`  ${failure}`);
    console.error('Failing renders are in build/design-docs-visual. If the change is intended, run with --update.');
    process.exitCode = 1;
    return;
  }
  console.log(`${all.length} renders match their baselines`);
}

main().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});
