import { formatAgeShort, plural } from './format.js';
import { normalizeMedia } from './media.js';
import { isSafeLink } from './clips.js';

// An opened thread is another lens on the same stream: its posts become the lines.
// Post text is parsed into plain segments here, so the UI never sees markup.

const TOKEN = />>(\d{1,12})(?!\d)|https:\/\/[^\s<>"'`]+/g;
const TRAILING = /[.,;:!?)\]}]+$/;

// Only https links, shown as plain text (never an anchor) so nothing navigates by accident.
function linkOf(raw) {
  const url = raw.replace(TRAILING, '');
  return url.length <= 500 && isSafeLink(url) ? url : null;
}

// ">>123" is a reference to a post; a line starting with a single ">" is a quote.
export function parsePost(text) {
  return String(text ?? '').split('\n').map((raw) => {
    const quote = raw.startsWith('>') && !raw.startsWith('>>');
    const body = quote ? raw.slice(1) : raw;
    const parts = [];
    let last = 0;
    for (const m of body.matchAll(TOKEN)) {
      const url = m[1] === undefined ? linkOf(m[0]) : null;
      if (m[1] === undefined && !url) continue; // an unusable link stays plain text
      if (m.index > last) parts.push({ t: 'text', v: body.slice(last, m.index) });
      if (url) {
        parts.push({ t: 'link', v: url });
        last = m.index + url.length;
        continue;
      }
      parts.push({ t: 'ref', v: m[0], no: Number(m[1]) });
      last = m.index + m[0].length;
    }
    if (last < body.length) parts.push({ t: 'text', v: body.slice(last) });
    return { quote, parts };
  });
}

const plain = (text) => String(text ?? '');

export function buildThreadItems({ thread, posts = [], query = '', now }) {
  const q = query.trim().toLowerCase();
  const nos = new Set(posts.map((p) => p.no));
  const backlinks = new Map();
  for (const p of posts) {
    for (const line of parsePost(p.text)) {
      for (const part of line.parts) {
        if (part.t !== 'ref' || !nos.has(part.no) || part.no === p.no) continue;
        const list = backlinks.get(part.no) ?? [];
        if (!list.includes(p.no)) list.push(p.no);
        backlinks.set(part.no, list);
      }
    }
  }
  const shown = q ? posts.filter((p) => plain(p.text).toLowerCase().includes(q)) : posts;
  const head = { id: `th:${thread.key}`, kind: 'threadhead', thread };
  const items = shown.map((p, i) => ({
    id: `p:${p.no}`, kind: 'post', no: p.no, author: p.author ?? 'Anon', op: p.no === thread.thread,
    ageText: p.at ? formatAgeShort(now - p.at) : '', media: normalizeMedia(p.media, p.no), lines: parsePost(p.text), replies: backlinks.get(p.no) ?? [], index: i,
  }));
  return [head, ...items];
}

// "latest 60 of 186" when the source only sends a window of a long thread.
export function threadSummary(items, total) {
  const n = items.filter((i) => i.kind === 'post').length;
  return total > n && n > 0 ? `latest ${n} of ${total}` : `${n} ${plural(n, 'post', 'posts')}`;
}
