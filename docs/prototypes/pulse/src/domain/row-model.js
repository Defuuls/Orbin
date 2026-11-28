import { unreadCount } from './unread.js';
import { formatAge, formatAgeShort, formatCount, plural } from './format.js';

// One view-model for every thread row in the app (Home, Watched, Search, Library).
// Screens never format their own rows, so they cannot drift apart.

export function makeRowContext({ user, settings, now, labels }) {
  return {
    user,
    settings,
    now,
    labels,
    readSet: new Set(user.history.map((h) => h.key)),
  };
}

export function toRowModel(t, ctx) {
  const watchedEntry = ctx.user.watched[t.key];
  const unread = watchedEntry ? unreadCount(watchedEntry) : 0;
  const replies = watchedEntry ? watchedEntry.latestReplyCount : t.replyCount ?? 0;
  const archived = t.status === 'archived';
  const sourceLabel = ctx.labels[t.source] ?? t.source;
  const parts = [`/${t.board}/`, sourceLabel, `${formatCount(replies)} ${plural(replies, 'reply', 'replies')}`];
  if (archived) parts.push('Archived');
  else if (t.lastModified) parts.push(formatAge(ctx.now - t.lastModified));
  return {
    key: t.key,
    thread: t,
    title: t.title,
    hue: Number.isFinite(t.hue) ? t.hue : 210,
    board: t.board,
    sourceLabel,
    replies,
    repliesText: formatCount(replies),
    ageText: archived ? 'arch' : t.lastModified ? formatAgeShort(ctx.now - t.lastModified) : '',
    meta: parts.join(' · '),
    unread,
    watched: Boolean(watchedEntry),
    saved: Boolean(ctx.user.saved[t.key]),
    read: ctx.settings.dimRead && ctx.readSet.has(t.key) && unread === 0,
    archived,
  };
}

export function matchesQuery(t, query, labels) {
  const q = String(query ?? '').trim().toLowerCase();
  if (!q) return false;
  const hay = `${t.title} ${t.board} ${labels[t.source] ?? t.source}`.toLowerCase();
  return q.split(/\s+/).every((word) => hay.includes(word));
}
