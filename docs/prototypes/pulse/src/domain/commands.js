import { SETTINGS_SECTIONS } from './settings.js';
import { LENSES } from './stream.js';
import { unreadCount } from './unread.js';
import { collectMedia } from './media.js';
import { isClipped } from './clips.js';

// Everything the command bar can do is data. Commands are built from state (so only
// useful ones appear), ranked by a small explicit fuzzy score, and executed through the
// same `actions` the rest of the app uses.

export function buildCommands(state) {
  const cmds = [];
  for (const l of LENSES) {
    cmds.push({ id: `go:${l.id}`, group: 'Go', label: `Go to ${l.label}`, hint: l.hint, keywords: [l.label, l.id], run: { type: 'lens', lens: l.id } });
  }
  if (state.thread) {
    const t = state.thread.snapshot;
    cmds.unshift({ id: 'do:closeThread', group: 'Do', label: 'Back to stream', hint: 'Close thread', keywords: ['close', 'back', 'exit'], run: { type: 'closeThread' } });
    const media = collectMedia(state.thread.load.data?.posts);
    if (media.length) cmds.splice(1, 0, { id: 'do:viewMedia', group: 'Do', label: 'View media', hint: `${media.length} ${media.length === 1 ? 'file' : 'files'}`, keywords: ['photos', 'images', 'videos', 'gallery'], run: { type: 'viewMedia', id: media[0].id } });
    cmds.splice(1, 0,
      { id: 'do:watchThread', group: 'Do', label: state.user.watched[t.key] ? 'Stop watching thread' : 'Watch thread', hint: `No.${t.thread}`, keywords: ['follow', 'track'], run: { type: 'watchThread', thread: t } },
      { id: 'do:clipThread', group: 'Do', label: isClipped(state.user, t) ? 'Remove clipped link' : 'Clip link', hint: `No.${t.thread}`, keywords: ['copy', 'url', 'link', 'clipboard'], run: { type: 'clipThread', thread: t } },
      { id: 'do:saveThread', group: 'Do', label: state.user.saved[t.key] ? 'Unsave thread' : 'Save thread', hint: `No.${t.thread}`, keywords: ['keep', 'bookmark'], run: { type: 'saveThread', thread: t } });
  }
  const unread = Object.values(state.user.watched).reduce((n, e) => n + unreadCount(e), 0);
  if (unread > 0) {
    cmds.push({ id: 'do:markAllRead', group: 'Do', label: 'Mark all read', hint: `${unread} new`, keywords: ['seen', 'clear', 'unread'], run: { type: 'markAllRead' } });
  }
  if (state.user.clips.length) {
    const n = state.user.clips.length;
    cmds.push(
      { id: 'do:exportClipsText', group: 'Do', label: 'Export clips: copy as text', hint: `${n} ${n === 1 ? 'link' : 'links'}`, keywords: ['clipboard', 'links', 'export', 'copy'], run: { type: 'exportClips', format: 'text' } },
      { id: 'do:exportClipsMd', group: 'Do', label: 'Export clips: download Markdown', hint: `${n} ${n === 1 ? 'link' : 'links'}`, keywords: ['clipboard', 'links', 'export', 'download', 'file'], run: { type: 'exportClips', format: 'markdown' } },
    );
  }
  cmds.push({ id: 'do:refresh', group: 'Do', label: 'Refresh everything', hint: 'Feeds and watched', keywords: ['reload', 'update', 'retry'], run: { type: 'refresh' } });
  for (const section of SETTINGS_SECTIONS) {
    for (const item of section.items) {
      const current = state.settings[item.id];
      if (item.type === 'choice') {
        for (const o of item.options) {
          if (o.value === current) continue;
          cmds.push({
            id: `set:${item.id}:${o.value}`, group: 'Set', label: `${item.label}: ${o.label}`, hint: section.title,
            keywords: [item.id, ...(item.keywords ?? [])], run: { type: 'setting', id: item.id, value: o.value },
          });
        }
      } else if (item.type === 'toggle') {
        cmds.push({
          id: `set:${item.id}`, group: 'Set', label: `${current ? 'Turn off' : 'Turn on'} ${item.label}`, hint: section.title,
          keywords: [item.id, ...(item.keywords ?? [])], run: { type: 'setting', id: item.id, value: !current },
        });
      }
    }
  }
  return cmds;
}

// 0 = no match. Higher is better: exact prefix > word prefix > substring > in-order letters.
export function scoreText(text, query) {
  const t = String(text).toLowerCase();
  const q = String(query ?? '').trim().toLowerCase();
  if (!q) return 1;
  if (t.startsWith(q)) return 100 - Math.min(t.length, 50) / 100;
  const words = t.split(/[^a-z0-9]+/);
  if (words.some((w) => w.startsWith(q))) return 80;
  if (t.includes(q)) return 60;
  let i = 0;
  for (const ch of t) if (ch === q[i]) i += 1;
  return i === q.length ? 30 : 0;
}

export function rankCommands(commands, query, limit = 8) {
  const q = String(query ?? '').trim();
  const scored = [];
  commands.forEach((c, index) => {
    const best = Math.max(scoreText(c.label, q), ...c.keywords.map((k) => scoreText(k, q) * 0.5));
    if (best > 0) scored.push({ c, best, index });
  });
  scored.sort((a, b) => b.best - a.best || a.index - b.index);
  return scored.slice(0, limit).map((s) => s.c);
}

// "/theme" is command mode; anything else filters the current lens.
export function parseInput(text) {
  const raw = String(text ?? '');
  const trimmed = raw.trimStart();
  if (trimmed.startsWith('/')) return { mode: 'command', query: trimmed.slice(1).trim() };
  return { mode: 'filter', query: raw.trim() };
}

export function executeCommand(cmd, actions) {
  const r = cmd.run;
  if (r.type === 'lens') actions.setRoute(r.lens);
  else if (r.type === 'closeThread') actions.closeThread();
  else if (r.type === 'viewMedia') actions.openViewer(r.id);
  else if (r.type === 'watchThread') actions.toggleWatch(r.thread);
  else if (r.type === 'saveThread') actions.toggleSave(r.thread);
  else if (r.type === 'clipThread') actions.toggleClip(r.thread);
  else if (r.type === 'exportClips') actions.exportClips(r.format);
  else if (r.type === 'markAllRead') actions.markAllRead();
  else if (r.type === 'refresh') actions.refreshAll();
  else if (r.type === 'setting') actions.setSetting(r.id, r.value);
  else return false;
  return true;
}
