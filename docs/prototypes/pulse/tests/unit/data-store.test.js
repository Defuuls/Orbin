import test from 'node:test';
import assert from 'node:assert/strict';
import { ok, err } from '../../src/data/result.js';
import { createMockDataSource, BOARDS } from '../../src/data/mock.js';
import { createStorage } from '../../src/store/storage.js';
import { createStore, watchSelect } from '../../src/store/store.js';
import { createCache } from '../../src/store/cache.js';
import { createLoader, mergeLoad } from '../../src/store/loader.js';
import { createBackoff } from '../../src/store/backoff.js';
import { unreadCount } from '../../src/domain/unread.js';

const memoryBackend = () => {
  const m = new Map();
  return { getItem: (k) => (m.has(k) ? m.get(k) : null), setItem: (k, v) => m.set(k, String(v)), removeItem: (k) => m.delete(k), _m: m };
};

test('result: err carries a reason and a human message; ok carries a value', () => {
  assert.deepEqual(ok(3), { ok: true, value: 3 });
  const e = err('offline');
  assert.equal(e.ok, false);
  assert.equal(e.error.reason, 'offline');
  assert.match(e.error.message, /offline/i);
  assert.equal(err('weird').error.message, 'Something went wrong.');
  assert.equal(err('weird', 'custom').error.message, 'custom');
});

test('mock: feed is deterministic and ordered newest first', async () => {
  const clock = () => 1_700_000_000_000;
  const a = await createMockDataSource({ now: clock }).getFeed(['lake/gdn', 'ridge/trn']);
  const b = await createMockDataSource({ now: clock }).getFeed(['lake/gdn', 'ridge/trn']);
  assert.deepEqual(a, b);
  assert.equal(a.value.length, 28);
  for (let i = 1; i < a.value.length; i++) assert.ok(a.value[i - 1].lastModified >= a.value[i].lastModified);
});

test('mock: same board name and thread numbers across sources produce distinct keys', async () => {
  const ds = createMockDataSource({ now: () => 0 });
  const feed = (await ds.getFeed(['lake/gdn', 'ridge/gdn'])).value;
  const keys = new Set(feed.map((t) => t.key));
  assert.equal(keys.size, feed.length);
  assert.ok(feed.some((t) => t.source === 'lake' && t.thread === 1000));
  assert.ok(feed.some((t) => t.source === 'ridge' && t.thread === 1000));
});

test('mock: empty follow list and empty fault return an empty list, not an error', async () => {
  const ds = createMockDataSource({ now: () => 0 });
  assert.deepEqual(await ds.getFeed([]), ok([]));
  ds.setFaults({ empty: true });
  assert.deepEqual(await ds.getFeed(['lake/gdn']), ok([]));
});

test('mock: faults produce errors, failNext counts down, offline is sticky', async () => {
  const ds = createMockDataSource({ now: () => 0, faults: { failNext: 2 } });
  assert.equal((await ds.getFeed(['lake/gdn'])).error.reason, 'server');
  assert.equal((await ds.listBoards()).error.reason, 'server');
  assert.equal((await ds.getFeed(['lake/gdn'])).ok, true);
  ds.setFaults({ offline: true });
  assert.equal((await ds.refreshThreads(['lake/gdn/1000'])).error.reason, 'offline');
  assert.equal((await ds.getFeed(['lake/gdn'])).error.reason, 'offline');
});

test('mock: replies grow over time for active threads, never for archived ones', async () => {
  let t = 0;
  const ds = createMockDataSource({ now: () => t });
  const before = (await ds.getFeed(BOARDS.map((b) => b.key))).value;
  t = 10 * 60000;
  const after = (await ds.getFeed(BOARDS.map((b) => b.key))).value;
  const byKey = new Map(before.map((x) => [x.key, x]));
  let grew = 0;
  for (const x of after) {
    const old = byKey.get(x.key);
    assert.ok(x.replyCount >= old.replyCount);
    if (x.status === 'archived') assert.equal(x.replyCount, old.replyCount);
    if (x.replyCount > old.replyCount) grew += 1;
  }
  assert.ok(grew > 0);
});

test('mock: huge fault generates the requested size; extra threads are injectable verbatim', async () => {
  const ds = createMockDataSource({ now: () => 0, faults: { hugePerBoard: 2000 } });
  assert.equal((await ds.getFeed(['lake/gdn'])).value.length, 2000);
  const hostile = {
    key: 'lake/gdn/999999', source: 'lake', board: 'gdn', thread: 999999, title: '<img src=x onerror=alert(1)>',
    replyCount: 1, lastModified: 0, status: 'live', hue: 10,
  };
  const ds2 = createMockDataSource({ now: () => 0, extraThreads: [hostile] });
  const feed = (await ds2.getFeed(['lake/gdn'])).value;
  assert.equal(feed.find((t) => t.key === hostile.key).title, '<img src=x onerror=alert(1)>');
});

test('mock: refreshThreads returns only requested keys', async () => {
  const ds = createMockDataSource({ now: () => 0 });
  const res = await ds.refreshThreads(['lake/gdn/1037', 'ridge/trn/1074']);
  assert.deepEqual(Object.keys(res.value).sort(), ['lake/gdn/1037', 'ridge/trn/1074']);
});

test('mock: demo user has follows, one favorite and watched threads with exact unread', () => {
  const user = createMockDataSource({ now: () => 5_000_000 }).demoUser(1);
  assert.equal(user.follows.length, 4);
  assert.deepEqual(user.favorites, ['lake/pho']);
  const unread = Object.values(user.watched).map(unreadCount).sort();
  assert.ok(Object.keys(user.watched).length === 3);
  assert.ok(unread.every((n) => n >= 0));
});

test('storage: round trips, falls back on missing, corrupt and wrong-version data', () => {
  const backend = memoryBackend();
  const s = createStorage(backend);
  assert.equal(s.persistent, true);
  assert.equal(s.get('x', 'fallback'), 'fallback');
  s.set('x', { a: 1 });
  assert.deepEqual(s.get('x'), { a: 1 });
  backend._m.set('pulse:bad', '{not json');
  assert.equal(s.get('bad', 'fb'), 'fb');
  backend._m.set('pulse:old', JSON.stringify({ v: 0, data: 1 }));
  assert.equal(s.get('old', 'fb'), 'fb');
  s.remove('x');
  assert.equal(s.get('x', 'gone'), 'gone');
});

test('storage: unavailable or throwing backends fall back to memory and never throw', () => {
  const throwing = { getItem() { throw new Error('denied'); }, setItem() { throw new Error('denied'); }, removeItem() { throw new Error('denied'); } };
  const s = createStorage(throwing);
  assert.equal(s.persistent, false);
  assert.doesNotThrow(() => s.set('k', 1));
  assert.equal(s.get('k'), 1);
  const none = createStorage(undefined);
  none.set('k', 2);
  assert.equal(none.get('k'), 2);
});

test('storage: quota error on write keeps the in-memory copy and reports failure', () => {
  const backend = memoryBackend();
  const s = createStorage(backend);
  backend.setItem = () => { throw new Error('QuotaExceededError'); };
  assert.equal(s.set('big', { a: 1 }), false);
});

test('store: set merges, functional set, no-op returns same state, unsubscribe works', () => {
  const store = createStore({ a: 1, b: 2 });
  const seen = [];
  const off = store.subscribe((s) => seen.push(s.a));
  store.set({ a: 5 });
  store.set((s) => ({ ...s, a: s.a + 1 }));
  store.set((s) => s);
  off();
  store.set({ a: 99 });
  assert.deepEqual(seen, [5, 6]);
  assert.equal(store.get().b, 2);
});

test('store: watchSelect fires only when a selected value changes', () => {
  const store = createStore({ a: 1, b: 1 });
  let calls = 0;
  watchSelect(store, (s) => [s.a], () => { calls += 1; });
  store.set({ b: 2 });
  assert.equal(calls, 0);
  store.set({ a: 2 });
  assert.equal(calls, 1);
  store.set({ a: 2, b: 3 });
  assert.equal(calls, 1);
});

test('cache: set/get, expiry, size cap evicts oldest, clear', () => {
  let t = 1000;
  const cache = createCache({ now: () => t, maxEntries: 2, maxAgeMs: 100 });
  cache.set('a', 1);
  t = 1010;
  cache.set('b', 2);
  t = 1020;
  cache.set('c', 3);
  assert.equal(cache.get('a'), null, 'oldest evicted');
  assert.equal(cache.get('c').value, 3);
  t = 1500;
  assert.equal(cache.get('c'), null, 'expired');
  cache.set('d', 4);
  cache.clear();
  assert.equal(cache.size(), 0);
});

test('cache: persists through storage and drops expired entries on load', () => {
  const backend = memoryBackend();
  let t = 0;
  const c1 = createCache({ storage: createStorage(backend), now: () => t, maxAgeMs: 100 });
  c1.set('k', [1, 2]);
  const c2 = createCache({ storage: createStorage(backend), now: () => t, maxAgeMs: 100 });
  assert.deepEqual(c2.get('k').value, [1, 2]);
  t = 500;
  const c3 = createCache({ storage: createStorage(backend), now: () => t, maxAgeMs: 100 });
  assert.equal(c3.size(), 0);
});

test('loader: first load reports loading then ready and caches', async () => {
  const cache = createCache({ now: () => 5 });
  const loader = createLoader({ cache, now: () => 5 });
  const states = [];
  const done = await loader.load('k', async () => ok([1]), (s) => states.push(s));
  assert.equal(done, true);
  assert.deepEqual(states.map((s) => s.status), ['loading', 'ready']);
  assert.deepEqual(cache.get('k').value, [1]);
});

test('loader: with cache shows it immediately as refreshing, then ready', async () => {
  const cache = createCache({ now: () => 5 });
  cache.set('k', ['old']);
  const states = [];
  await createLoader({ cache, now: () => 9 }).load('k', async () => ok(['new']), (s) => states.push(s));
  assert.deepEqual(states.map((s) => s.status), ['refreshing', 'ready']);
  assert.deepEqual(states[0].data, ['old']);
  assert.deepEqual(states[1].data, ['new']);
});

test('loader: failure with cache becomes stale and keeps the old data and age', async () => {
  const cache = createCache({ now: () => 5 });
  cache.set('k', ['old']);
  const states = [];
  const done = await createLoader({ cache, now: () => 9 }).load('k', async () => err('offline'), (s) => states.push(s));
  assert.equal(done, false);
  const last = states.at(-1);
  assert.equal(last.status, 'stale');
  assert.deepEqual(last.data, ['old']);
  assert.equal(last.at, 5);
  assert.equal(last.error.reason, 'offline');
});

test('loader: first-ever failure is an error, never an empty list', async () => {
  const states = [];
  await createLoader({ cache: createCache({}) }).load('k', async () => err('server'), (s) => states.push(s));
  assert.equal(states.at(-1).status, 'error');
  assert.equal(states.at(-1).data, undefined);
});

test('loader: a slow, superseded response cannot overwrite a newer one', async () => {
  const cache = createCache({ now: () => 1 });
  const loader = createLoader({ cache, now: () => 1 });
  let releaseSlow;
  const slow = new Promise((r) => { releaseSlow = r; });
  const states = [];
  const first = loader.load('k', () => slow, (s) => states.push(['first', s.status, s.data]));
  const second = loader.load('k', async () => ok(['fresh']), (s) => states.push(['second', s.status, s.data]));
  await second;
  releaseSlow(ok(['stale-response']));
  assert.equal(await first, null);
  assert.deepEqual(cache.get('k').value, ['fresh']);
  assert.ok(!states.some((s) => s[0] === 'first' && s[1] === 'ready'));
});

test('loader: different keys never interfere', async () => {
  const loader = createLoader({ cache: createCache({}) });
  const out = {};
  await Promise.all([
    loader.load('a', async () => ok('A'), (s) => { if (s.status === 'ready') out.a = s.data; }),
    loader.load('b', async () => ok('B'), (s) => { if (s.status === 'ready') out.b = s.data; }),
  ]);
  assert.deepEqual(out, { a: 'A', b: 'B' });
});

test('mergeLoad: loading keeps old content; first failure is error; later failure is stale', () => {
  const prev = { status: 'ready', data: [1], at: 7 };
  assert.deepEqual(mergeLoad(prev, { status: 'loading' }), { status: 'loading', data: [1], at: 7, error: undefined });
  const failed = mergeLoad(prev, { status: 'error', error: { reason: 'offline' } });
  assert.equal(failed.status, 'stale');
  assert.deepEqual(failed.data, [1]);
  assert.equal(failed.at, 7);
  assert.equal(mergeLoad(undefined, { status: 'error', error: {} }).status, 'error');
  const fresh = mergeLoad(prev, { status: 'ready', data: [2], at: 9 });
  assert.deepEqual([fresh.data, fresh.at], [[2], 9]);
});

test('backoff: doubles to a ceiling, resets on success, jitter is bounded', () => {
  const b = createBackoff({ baseMs: 1000, maxMs: 5000 });
  assert.deepEqual([b.next(), b.next(), b.next(), b.next(), b.next()], [1000, 2000, 4000, 5000, 5000]);
  assert.equal(b.failures(), 5);
  b.reset();
  assert.equal(b.next(), 1000);
  const j = createBackoff({ baseMs: 1000, maxMs: 5000, jitter: 0.2, random: () => 1 });
  assert.equal(j.next(), 1200);
  const lo = createBackoff({ baseMs: 1000, maxMs: 5000, jitter: 0.2, random: () => 0 });
  assert.equal(lo.next(), 800);
});
