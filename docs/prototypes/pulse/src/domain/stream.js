import { toRowModel, matchesQuery } from './row-model.js';
import { sortWatched, unreadCount } from './unread.js';
import { SETTINGS_SECTIONS, searchSettings } from './settings.js';
import { plural } from './format.js';
import { matchesClip } from './clips.js';

// The whole app is one stream of lines. A lens decides which lines appear and in what
// order. Everything here is pure: state in, ordered list of items out. Every item has a
// stable id (kind-prefixed) so the UI can move lines instead of rebuilding them.

export const LENSES = [
  { id: 'home', label: 'Home', hash: 'home', hint: 'Followed boards, newest first' },
  { id: 'watched', label: 'Watched', hash: 'watched', hint: 'Threads you track' },
  { id: 'feeds', label: 'Feeds', hash: 'feeds', hint: 'Boards you can follow' },
  { id: 'saved', label: 'Saved', hash: 'saved', hint: 'Threads kept for later' },
  { id: 'history', label: 'History', hash: 'history', hint: 'Recently opened' },
  { id: 'clips', label: 'Clips', hash: 'clips', hint: 'Links you set aside, exportable' },
  // "settings" is reserved by the host page for its own hash, so this lens uses #prefs.
  { id: 'settings', label: 'Settings', hash: 'prefs', hint: 'Appearance and behavior' },
];

export const lensById = (id) => LENSES.find((l) => l.id === id) ?? LENSES[0];
export const lensFromHash = (hash) => {
  const h = String(hash ?? '').replace(/^#/, '');
  return LENSES.find((l) => l.hash === h)?.id ?? null;
};

const header = (id, label, count) => ({ id: `h:${id}`, kind: 'header', label, count });
const note = (id, title, hint) => ({ id: `n:${id}`, kind: 'note', title, hint });
const threadItem = (t, ctx) => ({ id: `t:${t.key}`, kind: 'thread', row: toRowModel(t, ctx) });

function boardItem(b, state, labels) {
  const sourceLabel = labels[b.source] ?? b.source;
  return {
    id: `b:${b.key}`, kind: 'board', key: b.key, name: b.name, board: b.board, sourceLabel,
    followed: state.user.follows.includes(b.key), favorite: state.user.favorites.includes(b.key),
  };
}

// Groups with no items are dropped, so headers never stand alone.
function flatten(groups) {
  const out = [];
  for (const g of groups) {
    if (!g.items.length) continue;
    if (g.header) out.push(g.header(g.items.length));
    out.push(...g.items);
  }
  return out;
}

const byRecent = (a, b) => (b.lastModified ?? 0) - (a.lastModified ?? 0) || String(a.key).localeCompare(String(b.key));

function homeGroups(state, ctx) {
  const followed = new Set(state.user.follows);
  const feed = (state.feed.data ?? []).filter((t) => followed.has(`${t.source}/${t.board}`));
  const attention = sortWatched(Object.values(state.user.watched)).filter((e) => unreadCount(e) > 0);
  const attentionKeys = new Set(attention.map((e) => e.key));
  const latest = feed.filter((t) => !attentionKeys.has(t.key)).sort(byRecent);
  return [
    { header: (n) => header('attention', 'Needs you', n), items: attention.map((t) => threadItem(t, ctx)) },
    { header: (n) => header('latest', 'Latest', n), items: latest.map((t) => threadItem(t, ctx)) },
  ];
}

function watchedGroups(state, ctx) {
  const entries = sortWatched(Object.values(state.user.watched));
  const fresh = entries.filter((e) => unreadCount(e) > 0);
  const quiet = entries.filter((e) => unreadCount(e) === 0);
  return [
    { header: (n) => header('new', 'New replies', n), items: fresh.map((t) => threadItem(t, ctx)) },
    { header: (n) => header('quiet', 'Caught up', n), items: quiet.map((t) => threadItem(t, ctx)) },
  ];
}

function feedGroups(state, labels, q) {
  const words = q.trim().toLowerCase().split(/\s+/).filter(Boolean);
  const boards = (state.boards.data ?? []).filter((b) => {
    const hay = `${b.name} ${b.board} ${labels[b.source] ?? b.source}`.toLowerCase();
    return words.every((w) => hay.includes(w));
  });
  const items = boards.map((b) => boardItem(b, state, labels));
  const order = (a, b) => Number(b.favorite) - Number(a.favorite) || a.name.localeCompare(b.name) || a.sourceLabel.localeCompare(b.sourceLabel);
  return [
    { header: (n) => header('following', 'Following', n), items: items.filter((i) => i.followed).sort(order) },
    { header: (n) => header('boards', 'Boards', n), items: items.filter((i) => !i.followed).sort(order) },
  ];
}

function clipGroups(state, labels, q) {
  const clips = state.user.clips.filter((c) => !q || matchesClip(c, q, labels));
  const tools = clips.length ? [{ id: 'x:tools', kind: 'clipsTools', count: clips.length }] : [];
  return [{ header: null, items: tools }, { header: null, items: clips.map((clip) => ({ id: `c:${clip.url}`, kind: 'clip', clip, sourceLabel: labels[clip.source] ?? clip.source })) }];
}

function settingGroups(state, q) {
  return searchSettings(q, SETTINGS_SECTIONS).map((section) => ({
    header: (n) => header(`s-${section.id}`, section.title, n),
    items: section.items.map((item) => ({
      id: `s:${item.id}`, kind: 'setting', section: section.id, item, value: state.settings[item.id],
    })),
  }));
}

// The load state that governs a lens (drives loading / stale / error lines).
export function lensLoad(lens, state) {
  if (lens === 'home') return state.feed;
  if (lens === 'feeds') return state.boards;
  if (lens === 'watched') return state.watchedLoad;
  return { status: 'ready' };
}

const EMPTY = {
  home: ['Nothing followed yet', 'Follow a board from Feeds to fill this stream.'],
  watched: ['Nothing watched yet', 'Watch a thread to track its new replies.'],
  feeds: ['No boards', 'Boards will appear here.'],
  saved: ['Nothing saved', 'Save a thread to keep it here.'],
  history: ['No history', 'Threads you open appear here.'],
  clips: ['No clips', 'Clip a thread’s link to keep it here and export it.'],
  settings: ['No settings match', 'Try another word.'],
};

export function buildStream({ lens, state, rowCtx, labels, query = '' }) {
  const q = String(query ?? '').trim();
  const threads = (list) => [{ header: null, items: list.map((t) => threadItem(t, rowCtx)) }];
  let groups;
  if (lens === 'feeds') groups = feedGroups(state, labels, q);
  else if (lens === 'settings') groups = settingGroups(state, q);
  else if (lens === 'clips') groups = clipGroups(state, labels, q);
  else {
    if (lens === 'watched') groups = watchedGroups(state, rowCtx);
    else if (lens === 'saved') {
      groups = threads(Object.values(state.user.saved).sort((a, b) => (b.savedAt ?? 0) - (a.savedAt ?? 0)));
    } else if (lens === 'history') groups = threads(state.user.history);
    else groups = homeGroups(state, rowCtx);
    if (q) {
      groups = groups.map((g) => ({ ...g, items: g.items.filter((i) => matchesQuery(i.row.thread, q, labels)) }));
    }
  }
  const items = flatten(groups);
  const load = lensLoad(lens, state);
  if (items.length === 0 && (load.status === 'ready' || load.status === 'stale')) {
    const [title, hint] = q ? ['No lines match', `Nothing for “${q}”.`] : EMPTY[lens] ?? EMPTY.home;
    items.push(note('empty', title, hint));
  }
  return items;
}

// One-line summary shown in the stream's status strip.
export function summarize(lens, state) {
  const unread = Object.values(state.user.watched).reduce((n, e) => n + unreadCount(e), 0);
  if (lens === 'watched' || lens === 'home') {
    return unread > 0 ? `${unread} new` : 'quiet';
  }
  if (lens === 'feeds') return `${state.user.follows.length} followed`;
  if (lens === 'clips') return `${state.user.clips.length} kept`;
  return '';
}
