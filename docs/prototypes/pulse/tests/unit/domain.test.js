import test from 'node:test';
import assert from 'node:assert/strict';
import { boardKey, threadKey, parseThreadKey, boardKeyOfThread } from '../../src/domain/keys.js';
import { unreadCount, hasUnread, watchedNewReplies, markSeen, applyRefresh, sortWatched } from '../../src/domain/unread.js';
import {
  emptyUser, toggleKey, toggleWatch, toggleSave, recordHistory, markWatchedSeen, markAllSeen,
  applyRefreshAll, clearActivity, sanitizeUser, MAX_WATCHED,
} from '../../src/domain/collections.js';
import { formatAge, formatAgeShort, formatCount, plural } from '../../src/domain/format.js';
import { swipeDecision, clampIndex } from '../../src/domain/swipe.js';
import {
  SETTINGS_SECTIONS, defaultSettings, validateSettings, searchSettings, resolveScoped,
  setScoped, resetScoped, scopeId, sanitizeOverrides,
} from '../../src/domain/settings.js';
import { makeRowContext, toRowModel, matchesQuery } from '../../src/domain/row-model.js';

const thread = (over = {}) => ({
  key: threadKey('lake', 'gdn', 1000), source: 'lake', board: 'gdn', thread: 1000,
  title: 'Tomato blight', replyCount: 10, lastModified: 0, status: 'live', hue: 120, ...over,
});

test('keys: same board name and thread number in two sources stay distinct', () => {
  assert.notEqual(threadKey('lake', 'gdn', 1000), threadKey('ridge', 'gdn', 1000));
  assert.equal(threadKey('lake', 'gdn', 1000), 'lake/gdn/1000');
});

test('keys: round trip and board extraction', () => {
  const k = threadKey('ridge', 'trn', 1074);
  assert.deepEqual(parseThreadKey(k), { source: 'ridge', board: 'trn', thread: 1074 });
  assert.equal(boardKeyOfThread(k), boardKey('ridge', 'trn'));
});

test('keys: rejects separators, blanks and non-positive or fractional thread numbers', () => {
  assert.throws(() => boardKey('a/b', 'c'));
  assert.throws(() => boardKey('', 'c'));
  assert.throws(() => threadKey('a', 'b', 0));
  assert.throws(() => threadKey('a', 'b', -3));
  assert.throws(() => threadKey('a', 'b', 1.5));
  assert.throws(() => threadKey('a', 'b', 'x'));
  assert.throws(() => parseThreadKey('only/two'));
  assert.throws(() => parseThreadKey('a/b/0'));
});

test('unread: latest minus last seen, clamped at zero', () => {
  assert.equal(unreadCount({ latestReplyCount: 12, lastSeenReplyCount: 9 }), 3);
  assert.equal(unreadCount({ latestReplyCount: 5, lastSeenReplyCount: 9 }), 0);
  assert.equal(unreadCount({}), 0);
  assert.equal(unreadCount({ latestReplyCount: NaN, lastSeenReplyCount: 2 }), 0);
  assert.equal(unreadCount({ latestReplyCount: 7.9, lastSeenReplyCount: 2.2 }), 5);
  assert.equal(hasUnread({ latestReplyCount: 1, lastSeenReplyCount: 0 }), true);
  assert.equal(hasUnread({ latestReplyCount: 1, lastSeenReplyCount: 1 }), false);
});

test('unread: only watched entries count toward the badge', () => {
  const list = [
    { watched: true, latestReplyCount: 5, lastSeenReplyCount: 2 },
    { watched: true, latestReplyCount: 3, lastSeenReplyCount: 3 },
    { watched: false, latestReplyCount: 99, lastSeenReplyCount: 0 },
  ];
  assert.equal(watchedNewReplies(list), 3);
  assert.equal(watchedNewReplies([]), 0);
});

test('unread: markSeen resets exactly, so deleted replies cannot hide future ones', () => {
  const afterDelete = { latestReplyCount: 4, lastSeenReplyCount: 9 };
  const seen = markSeen(afterDelete);
  assert.equal(seen.lastSeenReplyCount, 4);
  assert.equal(unreadCount({ ...seen, latestReplyCount: 6 }), 2);
});

test('unread: refresh that shrinks the thread rebases the baseline', () => {
  const e = { latestReplyCount: 9, lastSeenReplyCount: 9, status: 'live' };
  const r = applyRefresh(e, { latestReplyCount: 5, status: 'live' });
  assert.equal(r.lastSeenReplyCount, 5);
  assert.equal(unreadCount(applyRefresh(r, { latestReplyCount: 8 })), 3);
});

test('unread: refresh keeps status when the update has none; carries archived', () => {
  const e = { latestReplyCount: 1, lastSeenReplyCount: 1, status: 'live' };
  assert.equal(applyRefresh(e, { latestReplyCount: 2 }).status, 'live');
  assert.equal(applyRefresh(e, { latestReplyCount: 2, status: 'archived' }).status, 'archived');
});

test('unread: sortWatched puts unread first, most unread first, is stable and does not mutate', () => {
  const a = { key: 'a', latestReplyCount: 5, lastSeenReplyCount: 5, addedAt: 3 };
  const b = { key: 'b', latestReplyCount: 9, lastSeenReplyCount: 8, addedAt: 1 };
  const c = { key: 'c', latestReplyCount: 9, lastSeenReplyCount: 2, addedAt: 2 };
  const input = [a, b, c];
  assert.deepEqual(sortWatched(input).map((e) => e.key), ['c', 'b', 'a']);
  assert.deepEqual(input.map((e) => e.key), ['a', 'b', 'c']);
});

test('collections: follow, favorite, watch and save are independent', () => {
  let user = emptyUser();
  const t = thread();
  user = { ...user, follows: toggleKey(user.follows, 'lake/gdn') };
  assert.deepEqual(user.favorites, []);
  assert.deepEqual(user.watched, {});
  user = toggleWatch(user, t, 100).user;
  assert.deepEqual(user.saved, {});
  assert.deepEqual(user.follows, ['lake/gdn']);
  user = toggleSave(user, t, 101);
  assert.ok(user.watched[t.key] && user.saved[t.key]);
  user = toggleWatch(user, t, 102).user;
  assert.ok(!user.watched[t.key] && user.saved[t.key]);
});

test('collections: toggleKey adds then removes without mutating', () => {
  const list = ['a'];
  const added = toggleKey(list, 'b');
  assert.deepEqual(added, ['a', 'b']);
  assert.deepEqual(list, ['a']);
  assert.deepEqual(toggleKey(added, 'a'), ['b']);
});

test('collections: watching baselines unread at zero', () => {
  const { user } = toggleWatch(emptyUser(), thread({ replyCount: 40 }), 5);
  const e = user.watched[thread().key];
  assert.equal(unreadCount(e), 0);
  assert.equal(e.watched, true);
  assert.equal(e.addedAt, 5);
});

test('collections: watch limit blocks additions but still allows removal', () => {
  const watched = {};
  for (let i = 1; i <= MAX_WATCHED; i++) watched[`s/b/${i}`] = { key: `s/b/${i}`, title: 't', watched: true };
  const full = { ...emptyUser(), watched };
  const blocked = toggleWatch(full, thread(), 1);
  assert.equal(blocked.limited, true);
  assert.equal(blocked.user, full);
  const removed = toggleWatch(full, { ...thread(), key: 's/b/1' }, 1);
  assert.equal(removed.limited, false);
  assert.equal(Object.keys(removed.user.watched).length, MAX_WATCHED - 1);
});

test('collections: history moves a revisit to the front, dedupes and caps', () => {
  let user = emptyUser();
  const a = thread({ key: 'lake/gdn/1' });
  const b = thread({ key: 'lake/gdn/2' });
  user = recordHistory(user, a, 1);
  user = recordHistory(user, b, 2);
  user = recordHistory(user, a, 3);
  assert.deepEqual(user.history.map((h) => h.key), ['lake/gdn/1', 'lake/gdn/2']);
  assert.equal(user.history[0].at, 3);
  let capped = emptyUser();
  for (let i = 1; i <= 7; i++) capped = recordHistory(capped, thread({ key: `s/b/${i}` }), i, 5);
  assert.equal(capped.history.length, 5);
  assert.equal(capped.history[0].key, 's/b/7');
});

test('collections: mark seen, mark all, refresh all', () => {
  let user = emptyUser();
  const t1 = thread({ key: 'lake/gdn/1', replyCount: 5 });
  const t2 = thread({ key: 'lake/gdn/2', replyCount: 5 });
  user = toggleWatch(toggleWatch(user, t1, 1).user, t2, 2).user;
  user = applyRefreshAll(user, { 'lake/gdn/1': { latestReplyCount: 9 }, 'lake/gdn/2': { latestReplyCount: 7 } });
  assert.equal(unreadCount(user.watched['lake/gdn/1']), 4);
  user = markWatchedSeen(user, 'lake/gdn/1');
  assert.equal(unreadCount(user.watched['lake/gdn/1']), 0);
  assert.equal(unreadCount(user.watched['lake/gdn/2']), 2);
  assert.equal(markWatchedSeen(user, 'nope'), user);
  user = markAllSeen(user);
  assert.equal(watchedNewReplies(Object.values(user.watched)), 0);
});

test('collections: refresh ignores keys that are not watched', () => {
  const user = emptyUser();
  const next = applyRefreshAll(user, { 'x/y/1': { latestReplyCount: 3 } });
  assert.deepEqual(next.watched, {});
});

test('collections: clearActivity removes history only', () => {
  let user = toggleWatch(emptyUser(), thread(), 1).user;
  user = toggleSave(recordHistory(user, thread(), 2), thread(), 3);
  user = { ...user, follows: ['lake/gdn'] };
  const cleared = clearActivity(user);
  assert.deepEqual(cleared.history, []);
  assert.ok(cleared.watched[thread().key]);
  assert.ok(cleared.saved[thread().key]);
  assert.deepEqual(cleared.follows, ['lake/gdn']);
});

test('collections: sanitizeUser survives corrupt input', () => {
  assert.deepEqual(sanitizeUser(null), emptyUser());
  assert.deepEqual(sanitizeUser('garbage'), emptyUser());
  const dirty = sanitizeUser({
    follows: ['a/b', 5, null], favorites: 'nope',
    watched: { x: { key: 'x', title: 't' }, y: 3, z: { key: 'z' } },
    saved: [], history: [{ key: 'k', title: 't' }, { nope: 1 }, null],
  });
  assert.deepEqual(dirty.follows, ['a/b']);
  assert.deepEqual(dirty.favorites, []);
  assert.deepEqual(Object.keys(dirty.watched), ['x']);
  assert.deepEqual(dirty.saved, {});
  assert.equal(dirty.history.length, 1);
});

test('format: ages across boundaries and bad input', () => {
  assert.equal(formatAge(0), 'just now');
  assert.equal(formatAge(44000), 'just now');
  assert.equal(formatAge(45000), '1 min ago');
  assert.equal(formatAge(5 * 60000), '5 min ago');
  assert.equal(formatAge(59 * 60000), '59 min ago');
  assert.equal(formatAge(3 * 3600000), '3 h ago');
  assert.equal(formatAge(2 * 86400000), '2 d ago');
  assert.equal(formatAge(-5000), 'just now');
  assert.equal(formatAge(NaN), 'just now');
});

test('format: counts and plurals', () => {
  assert.equal(formatCount(0), '0');
  assert.equal(formatCount(999), '999');
  assert.equal(formatCount(1000), '1k');
  assert.equal(formatCount(1500), '1.5k');
  assert.equal(formatCount(15400), '15k');
  assert.equal(formatCount(-4), '0');
  assert.equal(formatCount(undefined), '0');
  assert.equal(plural(1, 'reply', 'replies'), 'reply');
  assert.equal(plural(0, 'reply', 'replies'), 'replies');
  assert.equal(plural(2, 'reply', 'replies'), 'replies');
});

test('swipe: deliberate horizontal swipes only', () => {
  assert.equal(swipeDecision({ dx: -120, dy: 10, dt: 200 }), 1);
  assert.equal(swipeDecision({ dx: 120, dy: -10, dt: 200 }), -1);
  assert.equal(swipeDecision({ dx: -50, dy: 0, dt: 100 }), 0, 'too short');
  assert.equal(swipeDecision({ dx: -120, dy: 80, dt: 200 }), 0, 'too diagonal');
  assert.equal(swipeDecision({ dx: -120, dy: 0, dt: 900 }), 0, 'too slow');
  assert.equal(swipeDecision({ dx: 0, dy: 300, dt: 100 }), 0, 'vertical scroll');
  assert.equal(swipeDecision({ dx: NaN, dy: 0, dt: 100 }), 0);
  assert.equal(clampIndex(-1, 4), 0);
  assert.equal(clampIndex(4, 4), 3);
  assert.equal(clampIndex(2, 4), 2);
});

test('settings: defaults come from the registry and exclude actions', () => {
  const d = defaultSettings();
  assert.equal(d.theme, 'system');
  assert.equal(d.refreshSec, 30);
  assert.equal(d.saveHistory, true);
  assert.ok(!('clearActivity' in d));
});

test('formatAgeShort: compact ages with explicit boundaries', () => {
  assert.equal(formatAgeShort(0), 'now');
  assert.equal(formatAgeShort(44_000), 'now');
  assert.equal(formatAgeShort(45_000), '1m');
  assert.equal(formatAgeShort(59 * 60_000), '59m');
  assert.equal(formatAgeShort(61 * 60_000), '1h');
  assert.equal(formatAgeShort(23 * 3_600_000), '23h');
  assert.equal(formatAgeShort(48 * 3_600_000), '2d');
  assert.equal(formatAgeShort(-5000), 'now');
  assert.equal(formatAgeShort(NaN), 'now');
  assert.equal(formatAgeShort(undefined), 'now');
});

test('settings: validate drops unknown keys and invalid values, keeps valid ones', () => {
  const v = validateSettings({ theme: 'dark', density: 'tiles', refreshSec: 60, amoled: 'yes', bogus: 1, dimRead: false });
  assert.equal(v.theme, 'dark');
  assert.equal(v.density, 'regular');
  assert.equal(v.refreshSec, 60);
  assert.equal(v.amoled, false);
  assert.equal(v.dimRead, false);
  assert.ok(!('bogus' in v));
  assert.deepEqual(validateSettings(null), defaultSettings());
  assert.deepEqual(validateSettings(42), defaultSettings());
});

test('settings: search matches labels, hints, options, keywords and section names', () => {
  const ids = (q) => searchSettings(q).flatMap((s) => s.items.map((i) => i.id));
  assert.ok(ids('theme').includes('theme'));
  assert.ok(ids('pure black').includes('amoled'));
  assert.ok(ids('compact').includes('density'));
  assert.ok(ids('stale').includes('connection'));
  assert.deepEqual(ids('PRIVACY').sort(), ['clearActivity', 'saveHistory']);
  assert.deepEqual(ids('zzzzqq'), []);
  assert.equal(searchSettings('   '), SETTINGS_SECTIONS);
  assert.equal(searchSettings(undefined), SETTINGS_SECTIONS);
});

test('settings: scoped values resolve board -> site -> app and reset restores inheritance', () => {
  let o = {};
  const scope = { source: 'lake', board: 'gdn' };
  assert.deepEqual(resolveScoped(o, 'autoplay', scope, false), { value: false, from: 'app' });
  o = setScoped(o, scopeId('site', 'lake'), 'autoplay', true);
  assert.deepEqual(resolveScoped(o, 'autoplay', scope, false), { value: true, from: 'site:lake' });
  assert.deepEqual(resolveScoped(o, 'autoplay', { source: 'ridge', board: 'gdn' }, false), { value: false, from: 'app' });
  o = setScoped(o, scopeId('board', 'lake', 'gdn'), 'autoplay', false);
  assert.deepEqual(resolveScoped(o, 'autoplay', scope, true), { value: false, from: 'board:lake/gdn' });
  o = resetScoped(o, scopeId('board', 'lake', 'gdn'), 'autoplay');
  assert.equal(resolveScoped(o, 'autoplay', scope, false).from, 'site:lake');
  o = resetScoped(o, scopeId('site', 'lake'), 'autoplay');
  assert.deepEqual(o, {});
  assert.equal(resetScoped(o, 'site:lake', 'autoplay'), o);
});

test('settings: scoped update does not mutate and sanitizeOverrides drops junk', () => {
  const base = {};
  const next = setScoped(base, 'site:lake', 'autoplay', true);
  assert.deepEqual(base, {});
  assert.deepEqual(sanitizeOverrides({ 'site:lake': { autoplay: true, x: 3 }, evil: { a: true }, 'board:a/b': 'no' }), { 'site:lake': { autoplay: true } });
  assert.deepEqual(sanitizeOverrides(null), {});
  assert.deepEqual(next, { 'site:lake': { autoplay: true } });
});

test('row model: unread, watched, saved, read and archived flags', () => {
  const t = thread({ lastModified: 1000 });
  let user = toggleWatch(emptyUser(), t, 1).user;
  user = applyRefreshAll(user, { [t.key]: { latestReplyCount: 14 } });
  user = recordHistory(toggleSave(user, t, 2), t, 3);
  const ctx = makeRowContext({ user, settings: { dimRead: true }, now: 1000 + 5 * 60000, labels: { lake: 'Lakeside' } });
  const m = toRowModel(t, ctx);
  assert.equal(m.unread, 4);
  assert.equal(m.watched, true);
  assert.equal(m.saved, true);
  assert.equal(m.read, false, 'unread threads are never dimmed');
  assert.equal(m.board, 'gdn');
  assert.match(m.meta, /\/gdn\/ · Lakeside · 14 replies · 5 min ago/);
  const archived = toRowModel(thread({ status: 'archived' }), ctx);
  assert.equal(archived.archived, true);
  assert.match(archived.meta, /Archived/);
});

test('row model: read dimming respects the setting; singular reply; bad hue', () => {
  const t = thread({ replyCount: 1, hue: undefined });
  const user = recordHistory(emptyUser(), t, 1);
  const on = toRowModel(t, makeRowContext({ user, settings: { dimRead: true }, now: 0, labels: {} }));
  const off = toRowModel(t, makeRowContext({ user, settings: { dimRead: false }, now: 0, labels: {} }));
  assert.equal(on.read, true);
  assert.equal(off.read, false);
  assert.match(on.meta, /1 reply/);
  assert.equal(on.hue, 210);
});

test('row model: query matching is case-insensitive and every word must match', () => {
  const t = thread();
  const labels = { lake: 'Lakeside' };
  assert.equal(matchesQuery(t, 'tomato', labels), true);
  assert.equal(matchesQuery(t, 'TOMATO blight', labels), true);
  assert.equal(matchesQuery(t, 'tomato lakeside', labels), true);
  assert.equal(matchesQuery(t, 'tomato cheese', labels), false);
  assert.equal(matchesQuery(t, '   ', labels), false);
  assert.equal(matchesQuery(t, undefined, labels), false);
});

test('settings: an override for one setting never leaks into another setting', () => {
  const o = setScoped({}, 'site:lake', 'autoplay', true);
  assert.deepEqual(resolveScoped(o, 'someOtherSetting', { source: 'lake' }, 'app-value'), { value: 'app-value', from: 'app' });
  assert.deepEqual(resolveScoped(o, 'autoplay', { source: 'lake' }, false), { value: true, from: 'site:lake' });
});
