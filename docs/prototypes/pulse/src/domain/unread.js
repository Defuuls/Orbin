// Unread math for watched threads. Behaviour mirrors a simple, safe rule:
// unread = latest known replies - replies seen at last open, never below zero.

function whole(v) {
  return Number.isFinite(v) && v > 0 ? Math.floor(v) : 0;
}

export function unreadCount(entry) {
  return Math.max(0, whole(entry.latestReplyCount) - whole(entry.lastSeenReplyCount));
}

export function hasUnread(entry) {
  return unreadCount(entry) > 0;
}

export function watchedNewReplies(entries) {
  return entries.filter((e) => e.watched).reduce((sum, e) => sum + unreadCount(e), 0);
}

// Opening a thread resets the baseline to the latest count. It is an exact
// reset (not a max) so that replies deleted upstream cannot hide future ones.
export function markSeen(entry) {
  return { ...entry, lastSeenReplyCount: whole(entry.latestReplyCount) };
}

export function applyRefresh(entry, update) {
  const latest = whole(update.latestReplyCount);
  const lastSeen = whole(entry.lastSeenReplyCount);
  return {
    ...entry,
    latestReplyCount: latest,
    // Replies were deleted upstream: rebase so new replies still count.
    lastSeenReplyCount: latest < lastSeen ? latest : lastSeen,
    status: update.status ?? entry.status,
  };
}

// Unread first (most new replies first), then most recently added; stable and total.
export function sortWatched(entries) {
  return [...entries].sort((a, b) => {
    const ua = unreadCount(a);
    const ub = unreadCount(b);
    if ((ua > 0) !== (ub > 0)) return ua > 0 ? -1 : 1;
    if (ua !== ub) return ub - ua;
    if ((b.addedAt ?? 0) !== (a.addedAt ?? 0)) return (b.addedAt ?? 0) - (a.addedAt ?? 0);
    return String(a.key).localeCompare(String(b.key));
  });
}
