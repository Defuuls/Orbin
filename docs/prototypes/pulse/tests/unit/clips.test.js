import test from 'node:test';
import assert from 'node:assert/strict';
import {
  linkFor, toClip, toggleClip, removeClip, clearClips, isClipped, sanitizeClips, exportText, exportMarkdown,
  matchesClip, clipThread, isSafeLink, MAX_CLIPS,
} from '../../src/domain/clips.js';
import { emptyUser, sanitizeUser } from '../../src/domain/collections.js';
import { buildStream, summarize } from '../../src/domain/stream.js';
import { buildCommands, executeCommand } from '../../src/domain/commands.js';
import { defaultSettings } from '../../src/domain/settings.js';
import { makeRowContext } from '../../src/domain/row-model.js';

const NOW = 1_700_000_000_000;
const labels = { lake: 'Lakeside', ridge: 'Ridgeline' };
const T = (n, over = {}) => ({ key: `lake/gdn/${n}`, source: 'lake', board: 'gdn', thread: String(n), title: `Thread ${n}`, ...over });

test('linkFor: derived https link, encoded; a supplied https url wins; unsafe urls are ignored', () => {
  assert.equal(linkFor(T(5)), 'https://lake.example/gdn/thread/5');
  assert.equal(linkFor(T(5, { board: 'a b/c' })), 'https://lake.example/a%20b%2Fc/thread/5');
  assert.equal(linkFor(T(5, { url: 'https://x.test/t/5' })), 'https://x.test/t/5');
  for (const bad of ['javascript:alert(1)', 'http://x.test', 'https://x.test/a b', 'data:text/html,hi', 42, null]) {
    assert.equal(linkFor(T(5, { url: bad })), 'https://lake.example/gdn/thread/5', String(bad));
  }
});

test('toggleClip adds newest first, dedupes by link, and a second toggle removes it', () => {
  let u = emptyUser();
  u = toggleClip(u, T(1), NOW).user;
  u = toggleClip(u, T(2), NOW + 1).user;
  assert.deepEqual(u.clips.map((c) => c.thread), ['2', '1']);
  assert.equal(isClipped(u, T(1)), true);
  u = toggleClip(u, T(1), NOW + 2).user;
  assert.deepEqual(u.clips.map((c) => c.thread), ['2']);
  assert.equal(isClipped(u, T(1)), false);
  assert.equal(emptyUser().clips.length, 0);
});

test('two sources sharing a board and number do not collide', () => {
  let u = toggleClip(emptyUser(), T(1), NOW).user;
  u = toggleClip(u, T(1, { key: 'ridge/gdn/1', source: 'ridge' }), NOW).user;
  assert.equal(u.clips.length, 2);
});

test('clip limit is enforced and reported, not silently dropped', () => {
  let u = { ...emptyUser(), clips: Array.from({ length: MAX_CLIPS }, (_, i) => toClip(T(i), NOW)) };
  const r = toggleClip(u, T(99999), NOW);
  assert.equal(r.limited, true);
  assert.equal(r.user, u);
  assert.equal(toggleClip(u, T(0), NOW).limited, false, 'removing at the limit still works');
});

test('toClip flattens whitespace, caps the title and keeps its own copy', () => {
  const c = toClip(T(1, { title: `  a\n\tb  ${'x'.repeat(400)}` }), NOW);
  assert.ok(!/[\n\t]/.test(c.title) && c.title.length <= 300 && c.title.startsWith('a b '));
  assert.equal(toClip(T(1, { title: '   ' }), NOW).title, 'https://lake.example/gdn/thread/1');
  assert.deepEqual(clipThread(c), { key: c.key, source: 'lake', board: 'gdn', thread: '1', title: c.title });
});

test('removeClip and clearClips', () => {
  const u = toggleClip(emptyUser(), T(1), NOW).user;
  assert.equal(removeClip(u, 'https://nope.example/').clips.length, 1);
  assert.equal(removeClip(u, linkFor(T(1))).clips.length, 0);
  assert.equal(clearClips(u).clips.length, 0);
});

test('sanitizeClips drops hostile, malformed and duplicate entries', () => {
  const good = toClip(T(1), NOW);
  const out = sanitizeClips([
    good, good, null, 'x', { ...good, url: 'javascript:alert(1)' }, { ...good, url: 'http://insecure.test/' },
    { ...good, url: 'https://a.test/x', key: '' }, { ...good, url: 'https://b.test/x', title: 42, at: 'soon' },
  ]);
  assert.equal(out.length, 2);
  assert.equal(out[1].title, 'https://b.test/x');
  assert.equal(out[1].at, 0);
  assert.deepEqual(sanitizeClips('nope'), []);
  assert.equal(sanitizeClips(Array.from({ length: MAX_CLIPS + 20 }, (_, i) => toClip(T(i), NOW))).length, MAX_CLIPS);
  assert.deepEqual(sanitizeUser({ follows: [] }).clips, [], 'old saved data without clips loads');
  assert.equal(isSafeLink('https://x.test/p?q=1#a'), true);
});

test('export text: one block per link, readable, ends with a newline; empty exports nothing', () => {
  const clips = [toClip(T(2), NOW), toClip(T(1, { title: 'Line\nbreak' }), NOW)];
  assert.equal(exportText(clips, labels), 'Thread 2 (Lakeside /gdn/)\nhttps://lake.example/gdn/thread/2\n\nLine break (Lakeside /gdn/)\nhttps://lake.example/gdn/thread/1\n');
  assert.equal(exportText([], labels), '');
  assert.equal(exportMarkdown([], labels), '');
});

test('export markdown escapes hostile titles so they cannot forge links or markup', () => {
  const evil = toClip(T(1, { title: '[click](https://evil.test) <b>*x*</b>' }), NOW);
  const md = exportMarkdown([evil], labels);
  assert.ok(md.startsWith('- [\\[click\\]\\(https://evil\\.test\\) \\<b\\>\\*x\\*\\</b\\>](<https://lake.example/gdn/thread/1>)'), md);
  assert.equal(md.split('\n').length, 2);
});

test('clips lens: tools line first, newest first, filter narrows, empty note otherwise', () => {
  const user = { ...emptyUser(), clips: [toClip(T(2, { title: 'Tomato' }), NOW), toClip(T(1), NOW)] };
  const state = { route: 'clips', user, settings: defaultSettings(), overrides: {}, feed: { status: 'ready', data: [] }, boards: { status: 'ready', data: [] }, watchedLoad: { status: 'ready' }, clock: NOW };
  const rowCtx = makeRowContext({ user, settings: state.settings, now: NOW, labels });
  const items = buildStream({ lens: 'clips', state, rowCtx, labels });
  assert.deepEqual(items.map((i) => i.kind), ['clipsTools', 'clip', 'clip']);
  assert.equal(items[0].count, 2);
  assert.deepEqual(items.slice(1).map((i) => i.clip.thread), ['2', '1']);
  const f = buildStream({ lens: 'clips', state, rowCtx, labels, query: 'tomato' });
  assert.deepEqual(f.map((i) => i.kind), ['clipsTools', 'clip']);
  assert.equal(f[0].count, 1);
  assert.equal(buildStream({ lens: 'clips', state, rowCtx, labels, query: 'zzzz' })[0].kind, 'note');
  const none = { ...state, user: emptyUser() };
  assert.equal(buildStream({ lens: 'clips', state: none, rowCtx, labels })[0].title, 'No clips');
  assert.equal(summarize('clips', state), '2 kept');
  assert.equal(matchesClip(user.clips[0], 'lakeside gdn', labels), true);
});

test('commands: clip in a thread; export only when there is something to export; execution routes to actions', () => {
  const user = toggleClip(emptyUser(), T(1), NOW).user;
  const base = { route: 'home', user: emptyUser(), settings: defaultSettings(), thread: null };
  assert.ok(!buildCommands(base).some((c) => c.id.startsWith('do:exportClips')));
  const withClips = buildCommands({ ...base, user });
  assert.deepEqual(withClips.filter((c) => c.id.startsWith('do:exportClips')).map((c) => c.run.format), ['text', 'markdown']);
  const inThread = { ...base, user, thread: { key: T(1).key, snapshot: T(1), load: { status: 'loading' } } };
  assert.equal(buildCommands(inThread).find((c) => c.id === 'do:clipThread').label, 'Remove clipped link');
  assert.equal(buildCommands({ ...inThread, user: emptyUser() }).find((c) => c.id === 'do:clipThread').label, 'Clip link');
  const calls = [];
  const actions = { toggleClip: (t) => calls.push(['clip', t.thread]), exportClips: (f) => calls.push(['export', f]) };
  executeCommand({ run: { type: 'clipThread', thread: T(1) } }, actions);
  executeCommand({ run: { type: 'exportClips', format: 'markdown' } }, actions);
  assert.deepEqual(calls, [['clip', '1'], ['export', 'markdown']]);
});

// ---- links typed inside posts ----
import { parsePost } from '../../src/domain/thread.js';
import { toggleLinkClip, toLinkClip, hasClip } from '../../src/domain/clips.js';

test('parsePost: https links become link parts, trailing punctuation stays text, others stay literal', () => {
  const [a, b, c, d, e] = parsePost([
    'see https://pics.example/a/1.jpg, ok',
    '(https://x.test/p?q=1&r=2#h)',
    'http://insecure.test and javascript:alert(1) and https:// alone',
    'https://a.test/x>>12 https://b.test/y',
    'https://' + 'a'.repeat(600),
  ].join('\n'));
  assert.deepEqual(a.parts, [{ t: 'text', v: 'see ' }, { t: 'link', v: 'https://pics.example/a/1.jpg' }, { t: 'text', v: ', ok' }]);
  assert.deepEqual(b.parts, [{ t: 'text', v: '(' }, { t: 'link', v: 'https://x.test/p?q=1&r=2#h' }, { t: 'text', v: ')' }]);
  assert.ok(c.parts.every((p) => p.t === 'text'), 'http, javascript: and bare https:// are never links');
  assert.deepEqual(d.parts.map((p) => p.t), ['link', 'ref', 'text', 'link'], 'a link stops at > so a following ref still parses');
  assert.ok(e.parts.every((p) => p.t === 'text'), 'over-long links stay text');
  assert.equal(parsePost('>https://q.test/x')[0].quote, true);
});

test('link clips: keep their origin, dedupe by url, toggle off, ignore unsafe urls, short titles', () => {
  const t = T(5);
  let u = toggleLinkClip(emptyUser(), 'https://pics.example/gdn/9.jpg', t, 9, NOW).user;
  assert.equal(u.clips.length, 1);
  assert.deepEqual([u.clips[0].kind, u.clips[0].post, u.clips[0].thread, u.clips[0].title], ['link', 9, '5', 'pics.example/gdn/9.jpg']);
  assert.equal(hasClip(u, 'https://pics.example/gdn/9.jpg'), true);
  assert.equal(toggleLinkClip(u, 'javascript:alert(1)', t, 9, NOW).user, u);
  assert.equal(toggleLinkClip(u, 'http://x.test', t, 9, NOW).user, u);
  u = toggleLinkClip(u, 'https://pics.example/gdn/9.jpg', t, 12, NOW).user;
  assert.equal(u.clips.length, 0);
  assert.ok(toLinkClip(`https://x.test/${'a'.repeat(200)}`, t, 1, NOW).title.length <= 80);
  assert.equal('post' in toLinkClip('https://x.test/', t, -1, NOW), false);
});

test('sanitize keeps link kind and post, drops bad ones; export labels the origin', () => {
  const c = toLinkClip('https://x.test/a', T(5), 9, NOW);
  const [ok, odd] = sanitizeClips([c, { ...toClip(T(1), NOW), kind: 'evil', post: -3 }]);
  assert.deepEqual([ok.kind, ok.post], ['link', 9]);
  assert.equal('kind' in odd || 'post' in odd, false);
  assert.equal(exportText([c], labels), 'x.test/a (link from Lakeside /gdn/ No.9)\nhttps://x.test/a\n');
});
