import { h, setText, setAttr } from './dom.js';
import { collectMedia, neighbor, autoplayWanted } from '../domain/media.js';
import { swipeDecision, dismissDecision } from '../domain/swipe.js';

// Full-screen media viewer. It is a modal over the stream: the app behind it is inert,
// focus is held inside, and Escape, a downward drag or Close all return to the same place.

const ZOOM = 2;

export function createViewer(app, { inertTarget }) {
  const { store, actions } = app;
  const count = h('span', { class: 'v-count' });
  const name = h('span', { class: 'v-name' });
  const close = h('button', { type: 'button', class: 'btn quiet', onClick: () => actions.closeViewer() }, 'Close');
  const stage = h('div', { class: 'v-stage', 'data-noswipe': '' });
  const prev = h('button', { type: 'button', class: 'btn', 'aria-label': 'Previous media', onClick: () => actions.stepViewer(-1) }, 'Prev');
  const next = h('button', { type: 'button', class: 'btn', 'aria-label': 'Next media', onClick: () => actions.stepViewer(1) }, 'Next');
  const zoom = h('button', { type: 'button', class: 'btn', 'aria-pressed': 'false', onClick: () => toggleZoom() }, 'Zoom');
  const live = h('div', { class: 'sr-only', 'aria-live': 'polite' });
  const el = h('div', { class: 'viewer', role: 'dialog', 'aria-modal': 'true', 'aria-label': 'Media viewer', hidden: true },
    h('div', { class: 'v-bar' }, count, name, close), stage, h('div', { class: 'v-nav' }, prev, zoom, next), live);

  let shownId = null;
  let restore = null;
  let media = null;
  let view = { z: 1, x: 0, y: 0 };

  function paintView() {
    if (media?.tagName === 'IMG') media.style.transform = `translate(${view.x}px, ${view.y}px) scale(${view.z})`;
    setAttr(zoom, 'aria-pressed', String(view.z > 1));
  }
  function toggleZoom() {
    if (media?.tagName !== 'IMG') return;
    view = view.z > 1 ? { z: 1, x: 0, y: 0 } : { z: ZOOM, x: 0, y: 0 };
    paintView();
  }

  function mount(m, state) {
    media?.pause?.();
    view = { z: 1, x: 0, y: 0 };
    media = m.kind === 'image'
      ? h('img', { src: m.src, alt: m.name, class: 'v-media', draggable: 'false' })
      : h('video', { src: m.src, controls: true, playsinline: true, class: 'v-media', 'aria-label': m.name });
    stage.replaceChildren(media);
    if (m.kind === 'video' && autoplayWanted(state.overrides, state.settings, state.thread?.snapshot)) { media.muted = true; media.play?.().catch(() => {}); }
    zoom.hidden = m.kind !== 'image';
  }

  function render(state) {
    const list = collectMedia(state.thread?.load.data?.posts);
    const cur = state.viewer ? list.find((m) => m.id === state.viewer.id) : null;
    if (!cur) {
      if (!el.hidden) {
        media?.pause?.();
        el.hidden = true;
        inertTarget.inert = false;
        shownId = null;
        restore?.focus?.();
        restore = null;
      }
      if (state.viewer) actions.closeViewer(); // the file left the thread
      return;
    }
    if (el.hidden) {
      restore = document.activeElement;
      el.hidden = false;
      inertTarget.inert = true;
      close.focus({ preventScroll: true });
    }
    const i = list.indexOf(cur);
    setText(count, `${i + 1}/${list.length}`);
    setText(name, cur.name);
    prev.disabled = !neighbor(list, cur.id, -1);
    next.disabled = !neighbor(list, cur.id, 1);
    if (shownId !== cur.id) {
      shownId = cur.id;
      mount(cur, state);
      setText(live, `${cur.tag === 'IMG' ? 'Image' : 'Video'} ${i + 1} of ${list.length}: ${cur.name}`);
    }
  }

  // ---- gestures: swipe sideways to page, down to dismiss, tap or drag a zoomed image ----
  let g = null;
  stage.addEventListener('pointerdown', (e) => { g = { x: e.clientX, y: e.clientY, t: e.timeStamp, ox: view.x, oy: view.y, moved: false }; });
  stage.addEventListener('pointermove', (e) => {
    if (!g || view.z <= 1) return;
    view.x = g.ox + (e.clientX - g.x);
    view.y = g.oy + (e.clientY - g.y);
    g.moved = true;
    paintView();
  });
  stage.addEventListener('pointerup', (e) => {
    if (!g) return;
    const d = { dx: e.clientX - g.x, dy: e.clientY - g.y, dt: e.timeStamp - g.t };
    const wasPan = g.moved;
    g = null;
    if (view.z > 1) { if (!wasPan && Math.hypot(d.dx, d.dy) < 6 && media?.tagName === 'IMG') toggleZoom(); return; }
    if (dismissDecision(d)) { actions.closeViewer(); return; }
    const dir = swipeDecision(d);
    if (dir) { actions.stepViewer(dir); return; }
    if (Math.hypot(d.dx, d.dy) < 6 && media?.tagName === 'IMG') toggleZoom();
  });
  stage.addEventListener('pointercancel', () => { g = null; });

  // Capture phase, so Escape and arrows are handled here before anything behind the viewer.
  document.addEventListener('keydown', (e) => {
    if (el.hidden) return;
    if (e.key === 'Escape') { e.preventDefault(); e.stopPropagation(); actions.closeViewer(); }
    else if (e.key === 'ArrowRight' && document.activeElement?.tagName !== 'VIDEO') actions.stepViewer(1);
    else if (e.key === 'ArrowLeft' && document.activeElement?.tagName !== 'VIDEO') actions.stepViewer(-1);
    else if (e.key === 'Tab') {
      const items = [...el.querySelectorAll('button:not([disabled]):not([hidden]), video[controls]')];
      const first = items[0];
      const last = items.at(-1);
      if (e.shiftKey && document.activeElement === first) { e.preventDefault(); last.focus(); }
      else if (!e.shiftKey && document.activeElement === last) { e.preventDefault(); first.focus(); }
    }
  }, true);

  return { el, render, isOpen: () => !el.hidden };
}
