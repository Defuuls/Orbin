// The internal clipboard: links you set aside, kept on this device and exportable.
// Pure functions: state in, new state or text out. A clip keeps its own copy of the title
// so it stays readable after the thread is gone.

export const MAX_CLIPS = 500;
const MAX_TITLE = 300;

const oneLine = (s) => String(s ?? '').replace(/\s+/g, ' ').trim();
export const isSafeLink = (u) => typeof u === 'string' && /^https:\/\/[^\s<>"']+$/.test(u) && u.length <= 2000;

// The data source may supply a canonical `url`; otherwise one is derived from the key parts.
export function linkFor(t) {
  if (isSafeLink(t.url)) return t.url;
  return `https://${encodeURIComponent(t.source)}.example/${encodeURIComponent(t.board)}/thread/${encodeURIComponent(t.thread)}`;
}

export function toClip(t, now) {
  return {
    url: linkFor(t), key: t.key, source: t.source, board: t.board, thread: t.thread,
    title: oneLine(t.title).slice(0, MAX_TITLE) || linkFor(t), at: now,
  };
}

// The thread a clip points to, in the shape openThreadView expects.
export const clipThread = (c) => ({ key: c.key, source: c.source, board: c.board, thread: c.thread, title: c.title });

// A link found in a post: kept with the thread and post it came from.
export function toLinkClip(url, t, post, now) {
  const shown = url.replace(/^https:\/\//, '');
  return {
    kind: 'link', url, key: t.key, source: t.source, board: t.board, thread: t.thread,
    ...(Number.isSafeInteger(post) && post >= 0 ? { post } : {}),
    title: shown.length > 80 ? `${shown.slice(0, 79)}…` : shown, at: now,
  };
}

export const hasClip = (user, url) => user.clips.some((c) => c.url === url);
export const isClipped = (user, t) => hasClip(user, linkFor(t));

// Clipping the same link again removes it, like Save. Newest first.
function toggleEntry(user, clip) {
  if (hasClip(user, clip.url)) return { user: removeClip(user, clip.url), limited: false };
  if (user.clips.length >= MAX_CLIPS) return { user, limited: true };
  return { user: { ...user, clips: [clip, ...user.clips] }, limited: false };
}
export const toggleClip = (user, t, now) => toggleEntry(user, toClip(t, now));
export function toggleLinkClip(user, url, t, post, now) {
  if (!isSafeLink(url)) return { user, limited: false };
  return toggleEntry(user, toLinkClip(url, t, post, now));
}

export const removeClip = (user, url) => ({ ...user, clips: user.clips.filter((c) => c.url !== url) });
export const clearClips = (user) => ({ ...user, clips: [] });

// Persisted or imported data is never trusted: keep well-formed, unique, https-only entries.
export function sanitizeClips(raw) {
  if (!Array.isArray(raw)) return [];
  const seen = new Set();
  const out = [];
  for (const c of raw) {
    if (!c || typeof c !== 'object' || !isSafeLink(c.url) || seen.has(c.url)) continue;
    if (![c.key, c.source, c.board, c.thread].every((v) => typeof v === 'string' && v)) continue;
    seen.add(c.url);
    out.push({
      ...(c.kind === 'link' ? { kind: 'link' } : {}),
      ...(Number.isSafeInteger(c.post) && c.post >= 0 ? { post: c.post } : {}),
      url: c.url, key: c.key, source: c.source, board: c.board, thread: c.thread,
      title: oneLine(typeof c.title === 'string' ? c.title : c.url).slice(0, MAX_TITLE) || c.url,
      at: Number.isFinite(c.at) ? c.at : 0,
    });
    if (out.length >= MAX_CLIPS) break;
  }
  return out;
}

export function matchesClip(c, query, labels) {
  const hay = `${c.title} ${labels[c.source] ?? c.source} ${c.board} ${c.url}`.toLowerCase();
  return String(query).toLowerCase().split(/\s+/).filter(Boolean).every((w) => hay.includes(w));
}

export const clipOrigin = (c, labels) => `${labels[c.source] ?? c.source} /${c.board}/`;
const where = (c, labels) => (c.kind === 'link' ? `link from ${clipOrigin(c, labels)} No.${c.post ?? c.thread}` : clipOrigin(c, labels));

export function exportText(clips, labels) {
  return clips.map((c) => `${c.title} (${where(c, labels)})\n${c.url}`).join('\n\n') + (clips.length ? '\n' : '');
}

// Titles are untrusted text: escape Markdown link syntax so a title cannot forge a link.
const mdEscape = (s) => s.replace(/([\\`*_{}[\]()#+\-.!|<>~])/g, '\\$1');
export function exportMarkdown(clips, labels) {
  return clips.map((c) => `- [${mdEscape(c.title)}](<${c.url}>) · ${mdEscape(where(c, labels))}`).join('\n') + (clips.length ? '\n' : '');
}
