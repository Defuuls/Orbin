import { h, reconcile, setText, setAttr } from './dom.js';
import { createBoundary } from './boundary.js';
import { createLines } from './lines.js';
import { captureRects, playFlip } from './flip.js';
import { buildStream, lensById, lensLoad, summarize } from '../domain/stream.js';
import { buildThreadItems, threadSummary } from '../domain/thread.js';
import { formatAge, formatAgeShort, plural } from '../domain/format.js';

const REVEAL_MS = 300;

// The single scrolling stream. It owns the filter text, which line is expanded, the
// status strip and the move animation. Lenses only change what the stream is built from.
export function createStream(app) {
  const { store, actions, now, labels } = app;
  let lens = null;
  let mode = null;
  const scrolls = new Map();
  let query = '';
  let openId = null;
  let byId = new Map();

  const ctx = {
    app, now,
    jump(no) {
      const el = lineEl(`p:${no}`);
      if (!el) { app.ui.notify(`Post ${no} is not in this view`); return; }
      el.scrollIntoView({ block: 'center', behavior: document.documentElement.dataset.motion === 'reduce' ? 'auto' : 'smooth' });
      el.classList.remove('flash');
      void el.offsetWidth;
      el.classList.add('flash');
    },
    isOpen: (id) => openId === id,
    toggle(id) {
      const prev = openId;
      openId = prev === id ? null : id;
      const lineOf = (k) => (k.startsWith('m:') ? `p:${k.split(':')[1]}` : k);
      for (const k of [prev, openId]) if (k) refreshLine(lineOf(k));
      if (openId) { const target = lineOf(openId); setTimeout(() => lineEl(target)?.scrollIntoView({ block: 'nearest', behavior: 'auto' }), REVEAL_MS); }
    },
  };
  const lines = createLines(ctx);

  const list = h('ul', { class: 'rows', role: 'list', 'aria-label': 'Stream' });
  const boundary = createBoundary({ list, now, onRetry: () => actions.refreshAll(), empty: { title: '', hint: '' } });
  const name = h('h1', { class: 'lens-name' });
  const sum = h('span', { class: 'sum' });
  const hint = h('span', { class: 'hint' }, 'hold · menu');
  const strip = h('div', { class: 'strip' }, name, sum, hint);
  const live = h('div', { class: 'sr-only', 'aria-live': 'polite', role: 'status' });
  const scroller = h('div', { class: 'scroll', 'data-view': 'stream' }, strip, boundary.el, live);

  const lineEl = (id) => [...list.children].find((c) => c.dataset.key === id);
  function refreshLine(id) {
    const el = lineEl(id);
    const item = byId.get(id);
    if (el && item) lines.update(el, item);
  }

  function render(state) {
    const thread = state.thread;
    const nextMode = thread ? `thread:${thread.key}` : state.route;
    const lensChanged = nextMode !== mode;
    if (lensChanged) {
      if (mode) scrolls.set(mode, scroller.scrollTop);
      mode = nextMode;
      query = '';
      openId = null;
    }
    lens = state.route;
    const rowCtx = app.rowContext(state);
    const items = thread
      ? (thread.load.data ? buildThreadItems({ thread: thread.load.data.thread ?? thread.snapshot, posts: thread.load.data.posts, query, now: now() }) : [])
      : buildStream({ lens, state, rowCtx, labels, query });
    byId = new Map(items.map((i) => [i.id, i]));
    const openLine = openId?.startsWith('m:') ? `p:${openId.split(':')[1]}` : openId;
    if (openId && !byId.has(openLine)) openId = null;

    const reduce = document.documentElement.dataset.motion === 'reduce';
    const before = reduce ? new Map() : captureRects(list, scroller);
    if (lensChanged) scroller.scrollTop = thread ? 0 : (scrolls.get(mode) ?? 0);
    reconcile(list, items, { key: (i) => i.id, create: lines.create, update: lines.update });
    const played = playFlip(before, list, scroller, { reduce });
    list.dataset.moved = String(played.moved);

    const count = items.filter((i) => i.kind !== 'header' && i.kind !== 'note' && i.kind !== 'threadhead' && i.kind !== 'clipsTools').length;
    const load = thread ? thread.load : lensLoad(lens, state);
    boundary.update({ status: load.status, at: load.at, error: load.error, count: thread ? count : items.length });
    const label = thread ? 'Thread' : lensById(lens).label;
    setText(name, label);
    const age = load.at ? `upd ${formatAgeShort(now() - load.at)}` : '';
    setText(sum, [thread ? threadSummary(items, thread.load.data ? (thread.load.data.thread.replyCount ?? 0) + 1 : 0) : `${count} ${plural(count, 'line', 'lines')}`, thread ? '' : summarize(lens, state), age, query ? `filter “${query}”` : ''].filter(Boolean).join(' · '));
    setAttr(sum, 'title', load.at ? `Updated ${formatAge(now() - load.at)}` : null);
    if (lensChanged || query !== live._q) {
      setText(live, `${label}: ${count} ${plural(count, thread ? 'post' : 'line', thread ? 'posts' : 'lines')}${query ? ' match' : ''}`);
      live._q = query;
    }
  }

  return {
    el: scroller, list, render,
    setQuery(q) { if (q !== query) { query = q; render(store.get()); } },
    state: () => ({ lens, query, openId }),
  };
}
