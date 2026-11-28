import test from 'node:test';
import assert from 'node:assert/strict';
import { LENSES, lensFromHash, buildStream, lensLoad, summarize } from '../../src/domain/stream.js';
import { buildCommands, scoreText, rankCommands, parseInput, executeCommand } from '../../src/domain/commands.js';
import { emptyUser, toggleWatch } from '../../src/domain/collections.js';
import { defaultSettings } from '../../src/domain/settings.js';
import { toClip } from '../../src/domain/clips.js';
import { makeRowContext } from '../../src/domain/row-model.js';

const NOW = 1_700_000_000_000;
const labels = { lake: 'Lakeside', ridge: 'Ridgeline' };
const T = (n, over = {}) => ({
  key: `lake/gdn/${n}`, source: 'lake', board: 'gdn', thread: String(n), title: `Thread ${n}`,
  replyCount: 10, lastModified: NOW - n * 60000, status: 'live', hue: 100, ...over,
});

function mk({ feed = [], boards = [], user = emptyUser(), settings = defaultSettings() } = {}) {
  const state = {
    route: 'home', user, settings, overrides: {},
    feed: { status: 'ready', data: feed, at: NOW }, boards: { status: 'ready', data: boards, at: NOW },
    watchedLoad: { status: 'ready', at: NOW }, clock: NOW,
  };
  const rowCtx = makeRowContext({ user, settings, now: NOW, labels });
  return { state, rowCtx };
}
const stream = (lens, args, query = '') => {
  const { state, rowCtx } = mk(args);
  return buildStream({ lens, state, rowCtx, labels, query });
};
const ids = (items) => items.map((i) => i.id);

test('lenses: seven, with unique ids and hashes; settings uses #prefs', () => {
  assert.equal(LENSES.length, 7);
  assert.equal(new Set(LENSES.map((l) => l.id)).size, 7);
  assert.equal(new Set(LENSES.map((l) => l.hash)).size, 7);
  assert.equal(lensFromHash('#prefs'), 'settings');
  assert.equal(lensFromHash('watched'), 'watched');
  assert.equal(lensFromHash('#settings'), null);
  assert.equal(lensFromHash(''), null);
});

test('home: only followed boards, newest first, headers carry counts', () => {
  const feed = [T(5), T(1), T(3), T(9, { source: 'ridge', key: 'ridge/gdn/9' })];
  const user = { ...emptyUser(), follows: ['lake/gdn'] };
  const items = stream('home', { feed, user });
  assert.deepEqual(ids(items), ['h:latest', 't:lake/gdn/1', 't:lake/gdn/3', 't:lake/gdn/5']);
  assert.equal(items[0].count, 3);
});

test('home: unread watched threads rise to "Needs you" and appear exactly once', () => {
  const feed = [T(1), T(2), T(3)];
  let user = { ...emptyUser(), follows: ['lake/gdn'] };
  user = toggleWatch(user, T(3), NOW).user;
  user = { ...user, watched: { 'lake/gdn/3': { ...user.watched['lake/gdn/3'], latestReplyCount: 14 } } };
  const items = stream('home', { feed, user });
  assert.deepEqual(ids(items), ['h:attention', 't:lake/gdn/3', 'h:latest', 't:lake/gdn/1', 't:lake/gdn/2']);
});

test('home: nothing followed gives one explanatory note, not a blank stream', () => {
  const items = stream('home', { feed: [], user: emptyUser() });
  assert.deepEqual(ids(items), ['n:empty']);
  assert.match(items[0].hint, /Feeds/);
});

test('home: loading and error states add no empty note (the boundary owns those)', () => {
  const { state, rowCtx } = mk();
  for (const status of ['loading', 'error']) {
    const items = buildStream({ lens: 'home', state: { ...state, feed: { status } }, rowCtx, labels });
    assert.deepEqual(items, [], status);
  }
  const stale = buildStream({ lens: 'home', state: { ...state, feed: { status: 'stale', data: [], at: NOW } }, rowCtx, labels });
  assert.equal(stale[0].kind, 'note');
});

test('watched: new replies first, then caught up; empty groups drop their headers', () => {
  let user = emptyUser();
  user = toggleWatch(user, T(1), NOW).user;
  user = toggleWatch(user, T(2), NOW + 1).user;
  assert.deepEqual(ids(stream('watched', { user })), ['h:quiet', 't:lake/gdn/2', 't:lake/gdn/1']);
  user = { ...user, watched: { ...user.watched, 'lake/gdn/1': { ...user.watched['lake/gdn/1'], latestReplyCount: 12 } } };
  assert.deepEqual(ids(stream('watched', { user })), ['h:new', 't:lake/gdn/1', 'h:quiet', 't:lake/gdn/2']);
});

test('feeds: followed boards first, favorites before alphabetical, filter by name/source', () => {
  const boards = [
    { key: 'lake/gdn', source: 'lake', board: 'gdn', name: 'Gardening' },
    { key: 'lake/pho', source: 'lake', board: 'pho', name: 'Photography' },
    { key: 'ridge/gdn', source: 'ridge', board: 'gdn', name: 'Gardening' },
    { key: 'ridge/aud', source: 'ridge', board: 'aud', name: 'Audio' },
  ];
  const user = { ...emptyUser(), follows: ['ridge/gdn'], favorites: ['lake/pho'] };
  assert.deepEqual(ids(stream('feeds', { boards, user })), [
    'h:following', 'b:ridge/gdn', 'h:boards', 'b:lake/pho', 'b:ridge/aud', 'b:lake/gdn',
  ]);
  assert.deepEqual(ids(stream('feeds', { boards, user }, 'ridge garden')), ['h:following', 'b:ridge/gdn']);
  const none = stream('feeds', { boards, user }, 'zzzz');
  assert.equal(none[0].title, 'No lines match');
});

test('same board name on two sources stays two distinct lines', () => {
  const boards = [
    { key: 'lake/gdn', source: 'lake', board: 'gdn', name: 'Gardening' },
    { key: 'ridge/gdn', source: 'ridge', board: 'gdn', name: 'Gardening' },
  ];
  const items = stream('feeds', { boards });
  assert.equal(new Set(ids(items)).size, items.length);
});

test('saved and history: ordering and independent filtering', () => {
  const user = {
    ...emptyUser(),
    saved: { 'lake/gdn/1': { ...T(1), savedAt: 5 }, 'lake/gdn/2': { ...T(2), savedAt: 9 } },
    history: [T(4), T(3)],
  };
  assert.deepEqual(ids(stream('saved', { user })), ['t:lake/gdn/2', 't:lake/gdn/1']);
  assert.deepEqual(ids(stream('history', { user })), ['t:lake/gdn/4', 't:lake/gdn/3']);
  assert.deepEqual(ids(stream('history', { user }, 'thread 3')), ['t:lake/gdn/3']);
  assert.equal(stream('saved', { user: emptyUser() })[0].title, 'Nothing saved');
});

test('settings lens: one line per setting under section headers; filter narrows', () => {
  const all = stream('settings', {});
  assert.equal(all[0].kind, 'header');
  assert.ok(ids(all).includes('s:theme'));
  assert.ok(ids(all).includes('s:clearActivity'), 'actions are lines too');
  const f = stream('settings', {}, 'amoled');
  assert.deepEqual(ids(f), ['h:s-appearance', 's:amoled']);
  assert.equal(f[1].value, false);
  assert.equal(stream('settings', {}, 'zzzz')[0].kind, 'note');
});

test('stream ids are unique within every lens', () => {
  const feed = [T(1), T(2)];
  let user = { ...emptyUser(), follows: ['lake/gdn'], history: [T(1)], saved: { 'lake/gdn/2': T(2) }, clips: [toClip(T(1), NOW), toClip(T(2), NOW)] };
  user = toggleWatch(user, T(1), NOW).user;
  for (const l of LENSES) {
    const items = stream(l.id, { feed, user });
    assert.equal(new Set(ids(items)).size, items.length, l.id);
  }
});

test('hostile titles pass through as plain data', () => {
  const evil = T(1, { title: '<img src=x onerror=alert(1)>' });
  const items = stream('home', { feed: [evil], user: { ...emptyUser(), follows: ['lake/gdn'] } });
  assert.equal(items[1].row.title, '<img src=x onerror=alert(1)>');
});

test('lensLoad and summarize', () => {
  const { state } = mk();
  assert.equal(lensLoad('home', state), state.feed);
  assert.equal(lensLoad('feeds', state), state.boards);
  assert.equal(lensLoad('watched', state), state.watchedLoad);
  assert.equal(lensLoad('saved', state).status, 'ready');
  assert.equal(summarize('home', state), 'quiet');
  let user = toggleWatch(emptyUser(), T(1), NOW).user;
  user = { ...user, watched: { 'lake/gdn/1': { ...user.watched['lake/gdn/1'], latestReplyCount: 11 } } };
  assert.equal(summarize('watched', { ...state, user }), '1 new');
});

// ---- commands ----

test('scoreText ranks prefix > word prefix > substring > subsequence > none', () => {
  const s = (t, q) => scoreText(t, q);
  assert.ok(s('Theme: Dark', 'the') > s('Go to Theme', 'the'));
  assert.ok(s('Go to Theme', 'the') > s('Bathe', 'the'));
  assert.ok(s('Theme: Dark', 'tmd') > 0);
  assert.equal(s('Theme', 'zzz'), 0);
  assert.equal(s('Theme', ''), 1);
  assert.equal(s('Theme', '   '), 1);
});

test('commands: current choice is hidden, toggles flip, mark-all-read appears only with unread', () => {
  const { state } = mk();
  const labelsOf = (st) => buildCommands(st).map((c) => c.label);
  assert.ok(!labelsOf(state).includes('Theme: System'));
  assert.ok(labelsOf(state).includes('Theme: Dark'));
  assert.ok(labelsOf(state).includes('Turn on AMOLED black'));
  assert.ok(!labelsOf(state).includes('Mark all read'));
  let user = toggleWatch(emptyUser(), T(1), NOW).user;
  user = { ...user, watched: { 'lake/gdn/1': { ...user.watched['lake/gdn/1'], latestReplyCount: 99 } } };
  assert.ok(labelsOf({ ...state, user }).includes('Mark all read'));
  const on = { ...state, settings: { ...state.settings, amoled: true } };
  assert.ok(labelsOf(on).includes('Turn off AMOLED black'));
  assert.equal(new Set(buildCommands(state).map((c) => c.id)).size, buildCommands(state).length);
});

test('rankCommands: empty query keeps order, limit applies, keywords match', () => {
  const cmds = buildCommands(mk().state);
  assert.equal(rankCommands(cmds, '', 5).length, 5);
  assert.equal(rankCommands(cmds, '', 5)[0].id, 'go:home');
  assert.equal(rankCommands(cmds, 'offline')[0].id, 'set:connection:offline');
  assert.equal(rankCommands(cmds, 'wat')[0].id, 'go:watched');
  assert.deepEqual(rankCommands(cmds, 'zzzqq'), []);
});

test('parseInput: slash enters command mode, everything else filters', () => {
  assert.deepEqual(parseInput('/theme'), { mode: 'command', query: 'theme' });
  assert.deepEqual(parseInput('  / dark '), { mode: 'command', query: 'dark' });
  assert.deepEqual(parseInput('/'), { mode: 'command', query: '' });
  assert.deepEqual(parseInput(' tomato '), { mode: 'filter', query: 'tomato' });
  assert.deepEqual(parseInput(null), { mode: 'filter', query: '' });
  assert.deepEqual(parseInput('a/b'), { mode: 'filter', query: 'a/b' });
});

test('executeCommand routes each command type to the right action', () => {
  const calls = [];
  const actions = {
    setRoute: (v) => calls.push(['route', v]), markAllRead: () => calls.push(['mark']),
    refreshAll: () => calls.push(['refresh']), setSetting: (id, v) => calls.push(['set', id, v]),
  };
  const by = (id) => buildCommands(mk().state).find((c) => c.id === id);
  assert.ok(executeCommand(by('go:saved'), actions));
  assert.ok(executeCommand(by('do:refresh'), actions));
  assert.ok(executeCommand(by('set:theme:dark'), actions));
  assert.ok(executeCommand(by('set:amoled'), actions));
  assert.ok(executeCommand({ run: { type: 'markAllRead' } }, actions));
  assert.equal(executeCommand({ run: { type: 'nope' } }, actions), false);
  assert.deepEqual(calls, [['route', 'saved'], ['refresh'], ['set', 'theme', 'dark'], ['set', 'amoled', true], ['mark']]);
});

// ---- opened thread ----
import { parsePost, buildThreadItems, threadSummary } from '../../src/domain/thread.js';
import { createMockDataSource } from '../../src/data/mock.js';

test('parsePost: refs, greentext and plain text become segments, never markup', () => {
  const [a, b, c, d] = parsePost('>>12 agreed\n>quoted line\n>>7>>8\n<b>x</b> >>99999999999999');
  assert.deepEqual(a, { quote: false, parts: [{ t: 'ref', v: '>>12', no: 12 }, { t: 'text', v: ' agreed' }] });
  assert.deepEqual(b, { quote: true, parts: [{ t: 'text', v: 'quoted line' }] });
  assert.deepEqual(c.parts.map((p) => p.no), [7, 8]);
  assert.equal(c.quote, false, '">>" starts a reference, not a quote');
  assert.deepEqual(d.parts, [{ t: 'text', v: '<b>x</b> >>99999999999999' }], 'markup and over-long refs stay literal text');
  assert.deepEqual(parsePost(''), [{ quote: false, parts: [] }]);
  assert.deepEqual(parsePost(null), [{ quote: false, parts: [] }]);
});

const THREAD = { key: 'lake/gdn/1000', source: 'lake', board: 'gdn', thread: 1000, title: 'T' };
const POSTS = [
  { no: 1000, at: NOW - 3_600_000, text: 'op' },
  { no: 1001, at: NOW - 600_000, text: '>>1000 hi' },
  { no: 1002, at: NOW, text: '>>1000 and >>1001\n>>1002 self\n>>5 missing' },
];

test('buildThreadItems: header first, OP flagged, backlinks only for posts that exist', () => {
  const items = buildThreadItems({ thread: THREAD, posts: POSTS, now: NOW });
  assert.deepEqual(ids(items), ['th:lake/gdn/1000', 'p:1000', 'p:1001', 'p:1002']);
  assert.equal(items[1].op, true);
  assert.equal(items[2].op, false);
  assert.deepEqual(items[1].replies, [1001, 1002]);
  assert.deepEqual(items[2].replies, [1002]);
  assert.deepEqual(items[3].replies, [], 'self and missing refs create no backlink');
  assert.equal(items[1].ageText, '1h');
  assert.equal(items[3].ageText, 'now');
});

test('buildThreadItems: filter narrows posts, keeps the header, handles no posts', () => {
  assert.deepEqual(ids(buildThreadItems({ thread: THREAD, posts: POSTS, query: ' HI ', now: NOW })), ['th:lake/gdn/1000', 'p:1001']);
  assert.deepEqual(ids(buildThreadItems({ thread: THREAD, posts: POSTS, query: 'zzz', now: NOW })), ['th:lake/gdn/1000']);
  assert.deepEqual(ids(buildThreadItems({ thread: THREAD, now: NOW })), ['th:lake/gdn/1000']);
});

test('threadSummary says when only a window of a long thread is shown', () => {
  const items = buildThreadItems({ thread: THREAD, posts: POSTS, now: NOW });
  assert.equal(threadSummary(items, 3), '3 posts');
  assert.equal(threadSummary(items, 186), 'latest 3 of 186');
  assert.equal(threadSummary(items.slice(0, 1), 5), '0 posts');
  assert.equal(threadSummary(items.slice(0, 2), 0), '1 post');
});

test('commands: an open thread adds back/watch/save commands that reflect state', () => {
  const { state } = mk();
  const open = { ...state, thread: { key: THREAD.key, snapshot: THREAD, load: { status: 'ready' } } };
  const by = (st) => Object.fromEntries(buildCommands(st).map((c) => [c.id, c.label]));
  assert.equal(by(open)['do:closeThread'], 'Back to stream');
  assert.equal(by(open)['do:watchThread'], 'Watch thread');
  assert.equal(by(state)['do:closeThread'], undefined);
  const watched = { ...open, user: toggleWatch(open.user, { ...THREAD, replyCount: 1 }, NOW).user };
  assert.equal(by(watched)['do:watchThread'], 'Stop watching thread');
  const calls = [];
  const actions = { closeThread: () => calls.push('close'), toggleWatch: (t) => calls.push(['w', t.key]), toggleSave: (t) => calls.push(['s', t.key]) };
  for (const id of ['do:closeThread', 'do:watchThread', 'do:saveThread']) executeCommand(buildCommands(open).find((c) => c.id === id), actions);
  assert.deepEqual(calls, ['close', ['w', THREAD.key], ['s', THREAD.key]]);
});

test('mock getThread: deterministic window, OP first, honest failures', async () => {
  const ds = createMockDataSource({ now: () => NOW });
  const key = 'lake/gdn/1000';
  const a = await ds.getThread(key);
  const b = await ds.getThread(key);
  assert.ok(a.ok);
  assert.deepEqual(a.value.posts, b.value.posts, 'same input, same posts');
  assert.equal(a.value.posts[0].no, 1000);
  assert.ok(a.value.posts.length <= 60 && a.value.posts.length === Math.min(a.value.thread.replyCount + 1, 60));
  assert.deepEqual(a.value.posts.map((p) => p.no), a.value.posts.map((_, i) => 1000 + i));
  assert.equal((await ds.getThread('lake/gdn/999999')).error.reason, 'notfound');
  assert.equal((await ds.getThread('garbage')).error.reason, 'notfound');
  ds.setFaults({ offline: true });
  assert.equal((await ds.getThread(key)).error.reason, 'offline');
});

// ---- media ----
import { safeMediaSrc, formatBytes, formatDuration, normalizeMedia, collectMedia, neighbor, autoplayWanted } from '../../src/domain/media.js';
import { dismissDecision } from '../../src/domain/swipe.js';

test('safeMediaSrc: only inline images/videos, https and blob are allowed', () => {
  for (const ok of ['data:image/png;base64,AAAA', 'data:video/webm;base64,AAAA', 'data:image/svg+xml;charset=utf-8,%3Csvg', 'https://x.test/a.jpg', 'blob:abc']) assert.equal(safeMediaSrc(ok), ok);
  for (const bad of ['javascript:alert(1)', 'http://x.test/a.jpg', 'data:text/html,<script>', 'file:///etc/passwd', '//x.test/a', '', null, undefined, 5]) assert.equal(safeMediaSrc(bad), '', String(bad));
});

test('formatBytes and formatDuration have explicit boundaries and bad-input guards', () => {
  assert.deepEqual([0, 1023, 1024, 1536, 1048575, 1048576, 3_460_000].map(formatBytes), ['0 B', '1023 B', '1 KB', '2 KB', '1024 KB', '1.0 MB', '3.3 MB']);
  assert.deepEqual([NaN, -1, undefined].map(formatBytes), ['0 B', '0 B', '0 B']);
  assert.deepEqual([0, 5, 59.9, 60, 605, NaN].map(formatDuration), ['0:00', '0:05', '0:59', '1:00', '10:05', '0:00']);
});

test('normalizeMedia drops unknown kinds, unsafe sources and nameless files; ids follow post and index', () => {
  const list = normalizeMedia([
    { kind: 'image', name: 'a.jpg', bytes: 2048, w: 800, h: 600, src: 'data:image/png;base64,AA' },
    { kind: 'flash', name: 'x.swf', src: 'https://x.test/x.swf' },
    { kind: 'image', name: 'evil.jpg', src: 'javascript:alert(1)' },
    { kind: 'image', name: '  ', src: 'https://x.test/a.jpg' },
    { kind: 'video', name: 'v.webm', bytes: 9090, duration: 65, src: 'https://x.test/v.webm' },
    null,
  ], 1005);
  assert.deepEqual(list.map((m) => m.id), ['1005:0', '1005:1']);
  assert.equal(list[0].key, 'm:1005:0');
  assert.equal(list[0].text, 'a.jpg · 2 KB · 800×600');
  assert.equal(list[1].text, 'v.webm · 9 KB · 1:05');
  assert.deepEqual(list.map((m) => m.tag), ['IMG', 'VID']);
  assert.deepEqual(normalizeMedia(undefined, 1), []);
  assert.deepEqual(normalizeMedia('x', 1), []);
  assert.equal(normalizeMedia([{ kind: 'image', name: 'a', w: -5, h: 1e9, src: 'blob:x' }], 1)[0].text, 'a · 0 B', 'bad dimensions are ignored');
});

test('collectMedia keeps reading order and neighbor stops at the ends', () => {
  const posts = [
    { no: 10, media: [{ kind: 'image', name: 'a', src: 'blob:a' }] },
    { no: 11 },
    { no: 12, media: [{ kind: 'image', name: 'b', src: 'blob:b' }, { kind: 'video', name: 'c', src: 'blob:c' }] },
  ];
  const all = collectMedia(posts);
  assert.deepEqual(all.map((m) => m.id), ['10:0', '12:0', '12:1']);
  assert.equal(neighbor(all, '10:0', -1), null);
  assert.equal(neighbor(all, '10:0', 1).id, '12:0');
  assert.equal(neighbor(all, '12:1', 1), null);
  assert.equal(neighbor(all, 'nope', 1), null);
  assert.deepEqual(collectMedia(undefined), []);
});

test('autoplayWanted resolves board -> site -> app', () => {
  const t = { source: 'lake', board: 'gdn' };
  assert.equal(autoplayWanted({}, { autoplay: false }, t), false);
  assert.equal(autoplayWanted({}, { autoplay: true }, t), true);
  assert.equal(autoplayWanted({ 'site:lake': { autoplay: true } }, { autoplay: false }, t), true);
  assert.equal(autoplayWanted({ 'site:lake': { autoplay: true }, 'board:lake/gdn': { autoplay: false } }, { autoplay: true }, t), false);
  assert.equal(autoplayWanted({ 'site:ridge': { autoplay: true } }, { autoplay: false }, t), false);
  assert.equal(autoplayWanted({}, { autoplay: true }, undefined), true);
});

test('dismissDecision: only a quick, mostly vertical, downward drag', () => {
  assert.equal(dismissDecision({ dx: 5, dy: 140, dt: 200 }), true);
  assert.equal(dismissDecision({ dx: 0, dy: 95, dt: 200 }), false);
  assert.equal(dismissDecision({ dx: 0, dy: -200, dt: 200 }), false);
  assert.equal(dismissDecision({ dx: 100, dy: 140, dt: 200 }), false);
  assert.equal(dismissDecision({ dx: 0, dy: 200, dt: 900 }), false);
  assert.equal(dismissDecision({ dx: NaN, dy: 200, dt: 100 }), false);
});

test('thread items carry normalized media and commands offer View media only when there is some', () => {
  const posts = [{ no: 1000, at: NOW, text: 'op', media: [{ kind: 'image', name: 'a.jpg', src: 'blob:a' }, { kind: 'image', name: 'bad', src: 'javascript:x' }] }];
  const items = buildThreadItems({ thread: THREAD, posts, now: NOW });
  assert.equal(items[1].media.length, 1);
  assert.equal(items[1].media[0].key, 'm:1000:0');
  const { state } = mk();
  const withMedia = { ...state, thread: { key: THREAD.key, snapshot: THREAD, load: { status: 'ready', data: { thread: THREAD, posts } } } };
  const without = { ...withMedia, thread: { ...withMedia.thread, load: { status: 'ready', data: { thread: THREAD, posts: [{ no: 1000, text: 'x' }] } } } };
  const cmd = buildCommands(withMedia).find((c) => c.id === 'do:viewMedia');
  assert.equal(cmd.hint, '1 file');
  assert.equal(buildCommands(without).find((c) => c.id === 'do:viewMedia'), undefined);
  const calls = [];
  executeCommand(cmd, { openViewer: (id) => calls.push(id) });
  assert.deepEqual(calls, ['1000:0']);
});
