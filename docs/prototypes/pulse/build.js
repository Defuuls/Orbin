// Inlines src/ into one self-contained HTML file. No dependencies: it resolves the
// module graph from single-line `import ... from '...'` statements, orders modules so
// dependencies come first, strips import/export syntax, and fails the build on any
// top-level name collision so the flattening can never silently change meaning.
import { readFileSync, writeFileSync, mkdirSync } from 'node:fs';
import { dirname, resolve, relative } from 'node:path';
import { execFileSync } from 'node:child_process';

const root = new URL('./src/', import.meta.url).pathname;
const entry = resolve(root, 'main.js');
const IMPORT_RE = /^import\s[^;]*?from\s+'([^']+)';?[ \t]*\n/gm;

const order = [];
const seen = new Set();
function visit(file) {
  if (seen.has(file)) return;
  seen.add(file);
  const src = readFileSync(file, 'utf8');
  for (const m of src.matchAll(IMPORT_RE)) visit(resolve(dirname(file), m[1]));
  const leftover = src.replace(IMPORT_RE, '').match(/^\s*import\s/m);
  if (leftover) throw new Error(`Unsupported import syntax in ${file}`);
  order.push(file);
}
visit(entry);

const declared = new Map();
const parts = [];
for (const file of order) {
  const out = [];
  for (const line of readFileSync(file, 'utf8').replace(IMPORT_RE, '').split('\n')) {
    if (/^export\s+(default|\{|\*)/.test(line)) throw new Error(`Unsupported export syntax in ${file}: ${line}`);
    const stripped = line.replace(/^export\s+(?=(async\s+function|function|const|let|class)\b)/, '');
    const d = stripped.match(/^(?:async\s+function|function|const|let|class)\s+([A-Za-z_$][\w$]*)/);
    if (d) {
      if (declared.has(d[1])) throw new Error(`Name collision: "${d[1]}" in ${relative(root, file)} and ${declared.get(d[1])}`);
      declared.set(d[1], relative(root, file));
    }
    out.push(stripped);
  }
  parts.push(`// ---- ${relative(root, file)} ----\n${out.join('\n')}`);
}

const script = `(() => {\n'use strict';\n${parts.join('\n\n')}\n})();`;
const css = readFileSync(resolve(root, 'styles.css'), 'utf8');
const html = readFileSync(resolve(root, 'index.template.html'), 'utf8')
  .replace('/*__STYLES__*/', () => css)
  .replace('/*__SCRIPT__*/', () => script.replace(/<\/script/gi, '<\\/script'));

const outDir = new URL('./dist/', import.meta.url).pathname;
mkdirSync(outDir, { recursive: true });
writeFileSync(resolve(outDir, 'index.html'), html);
writeFileSync(resolve(outDir, 'app.js'), script);
execFileSync(process.execPath, ['--check', resolve(outDir, 'app.js')], { stdio: 'inherit' });
console.log(`built dist/index.html (${(html.length / 1024).toFixed(1)} KB, ${order.length} modules)`);
