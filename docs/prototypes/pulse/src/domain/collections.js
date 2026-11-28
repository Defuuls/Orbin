import { markSeen, applyRefresh } from './unread.js';
import { sanitizeClips } from './clips.js';

// User collections. Follow, favorite, watch and save are four independent facts.
// Everything here is pure: state in, new state out.

export const MAX_WATCHED = 500;
export const MAX_HISTORY = 100;

export function emptyUser() {
  return { follows: [], favorites: [], watched: {}, saved: {}, history: [], clips: [] };
}

export function toggleKey(list, key) {
  return list.includes(key) ? list.filter((k) => k !== key) : [...list, key];
}

export function snapshot(t) {
  const { key, source, board, thread, title, replyCount, lastModified, status, hue } = t;
  return { key, source, board, thread, title, replyCount, lastModified, status, hue };
}

export function toggleWatch(user, t, now) {
  if (user.watched[t.key]) {
    const { [t.key]: _removed, ...rest } = user.watched;
    return { user: { ...user, watched: rest }, limited: false };
  }
  if (Object.keys(user.watched).length >= MAX_WATCHED) return { user, limited: true };
  const replies = t.replyCount ?? 0;
  const entry = {
    ...snapshot(t),
    watched: true,
    addedAt: now,
    lastSeenReplyCount: replies,
    latestReplyCount: replies,
  };
  return { user: { ...user, watched: { ...user.watched, [t.key]: entry } }, limited: false };
}

export function toggleSave(user, t, now) {
  if (user.saved[t.key]) {
    const { [t.key]: _removed, ...rest } = user.saved;
    return { ...user, saved: rest };
  }
  return { ...user, saved: { ...user.saved, [t.key]: { ...snapshot(t), savedAt: now } } };
}

export function recordHistory(user, t, now, cap = MAX_HISTORY) {
  const entry = { ...snapshot(t), at: now };
  const rest = user.history.filter((h) => h.key !== t.key);
  return { ...user, history: [entry, ...rest].slice(0, cap) };
}

export function markWatchedSeen(user, key) {
  const entry = user.watched[key];
  if (!entry) return user;
  return { ...user, watched: { ...user.watched, [key]: markSeen(entry) } };
}

export function markAllSeen(user) {
  const watched = {};
  for (const [k, v] of Object.entries(user.watched)) watched[k] = markSeen(v);
  return { ...user, watched };
}

export function applyRefreshAll(user, updates) {
  const watched = {};
  for (const [k, v] of Object.entries(user.watched)) {
    watched[k] = updates[k] ? applyRefresh(v, updates[k]) : v;
  }
  return { ...user, watched };
}

// Clears local activity only: history. Watched, saved and follows stay.
export function clearActivity(user) {
  return { ...user, history: [] };
}

// Defensive: persisted data may be old or corrupt. Keep only what is well-formed.
export function sanitizeUser(raw) {
  const base = emptyUser();
  if (!raw || typeof raw !== 'object') return base;
  const strings = (v) => (Array.isArray(v) ? v.filter((x) => typeof x === 'string') : []);
  const dict = (v) => {
    const out = {};
    if (v && typeof v === 'object' && !Array.isArray(v)) {
      for (const [k, e] of Object.entries(v)) {
        if (e && typeof e === 'object' && typeof e.key === 'string' && typeof e.title === 'string') out[k] = e;
      }
    }
    return out;
  };
  return {
    follows: strings(raw.follows),
    favorites: strings(raw.favorites),
    watched: dict(raw.watched),
    saved: dict(raw.saved),
    clips: sanitizeClips(raw.clips),
    history: (Array.isArray(raw.history) ? raw.history : [])
      .filter((e) => e && typeof e.key === 'string' && typeof e.title === 'string')
      .slice(0, MAX_HISTORY),
  };
}
