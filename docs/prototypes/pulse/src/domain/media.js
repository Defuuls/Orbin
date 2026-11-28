import { resolveScoped } from './settings.js';

// Media attached to posts. Everything a source sends is normalized here: unknown kinds
// and unsafe addresses are dropped, so the UI only ever sees media it can show safely.

const SAFE_SRC = /^(data:(image|video)\/[a-z0-9.+-]+[;,]|https:\/\/|blob:)/i;
const KINDS = new Set(['image', 'video']);

export const safeMediaSrc = (src) => (typeof src === 'string' && SAFE_SRC.test(src) ? src : '');

export function formatBytes(n) {
  const v = Number.isFinite(n) && n > 0 ? n : 0;
  if (v < 1024) return `${Math.round(v)} B`;
  if (v < 1024 * 1024) return `${Math.round(v / 1024)} KB`;
  return `${(v / (1024 * 1024)).toFixed(1)} MB`;
}

export function formatDuration(sec) {
  const s = Number.isFinite(sec) && sec > 0 ? Math.floor(sec) : 0;
  return `${Math.floor(s / 60)}:${String(s % 60).padStart(2, '0')}`;
}

const dim = (v) => (Number.isFinite(v) && v > 0 && v <= 20000 ? Math.round(v) : 0);

export function normalizeMedia(list, postNo) {
  if (!Array.isArray(list)) return [];
  const out = [];
  for (const raw of list) {
    if (!raw || !KINDS.has(raw.kind)) continue;
    const src = safeMediaSrc(raw.src);
    const name = String(raw.name ?? '').trim();
    if (!src || !name) continue;
    const m = { id: `${postNo}:${out.length}`, key: `m:${postNo}:${out.length}`, postNo, kind: raw.kind, name, src, bytes: raw.bytes, w: dim(raw.w), h: dim(raw.h), duration: raw.duration };
    const bits = [name, formatBytes(raw.bytes)];
    if (m.kind === 'image' && m.w && m.h) bits.push(`${m.w}×${m.h}`);
    if (m.kind === 'video' && Number.isFinite(raw.duration)) bits.push(formatDuration(raw.duration));
    out.push({ ...m, tag: m.kind === 'image' ? 'IMG' : 'VID', text: bits.join(' · ') });
  }
  return out;
}

// Every media item of a thread in reading order: what the viewer pages through.
export function collectMedia(posts = []) {
  return posts.flatMap((p) => normalizeMedia(p.media, p.no));
}

export function neighbor(list, id, dir) {
  const i = list.findIndex((m) => m.id === id);
  if (i < 0) return null;
  return list[i + dir] ?? null;
}

// Autoplay is a scoped setting: board overrides site overrides app.
export function autoplayWanted(overrides, settings, thread) {
  return Boolean(resolveScoped(overrides, 'autoplay', { source: thread?.source, board: thread?.board }, settings.autoplay).value);
}
