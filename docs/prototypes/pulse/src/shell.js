import { createApp } from './app.js';
import { h } from './ui/dom.js';
import { createStream } from './ui/stream.js';
import { createPalette } from './ui/palette.js';
import { createViewer } from './ui/viewer.js';
import { createToaster } from './ui/controls.js';
import { copyText, downloadText } from './ui/export.js';
import { watchSelect } from './store/store.js';
import { swipeDecision, clampIndex } from './domain/swipe.js';
import { LENSES, lensById, lensFromHash } from './domain/stream.js';

function bindTheme(store) {
  const mq = (q) => (typeof window.matchMedia === 'function' ? window.matchMedia(q) : { matches: false, addEventListener() {} });
  const dark = mq('(prefers-color-scheme: dark)');
  const reduce = mq('(prefers-reduced-motion: reduce)');
  const meta = document.querySelector('meta[name="theme-color"]');
  function apply() {
    const s = store.get().settings;
    const root = document.documentElement;
    // "System" follows the host viewer's data-theme when it sets one, then the OS.
    const host = root.getAttribute('data-theme');
    const system = host === 'dark' || host === 'light' ? host : dark.matches ? 'dark' : 'light';
    const scheme = s.theme === 'system' ? system : s.theme;
    root.dataset.scheme = scheme;
    root.dataset.amoled = String(s.amoled && scheme === 'dark');
    root.dataset.motion = s.reduceMotion || reduce.matches ? 'reduce' : 'full';
    root.dataset.density = s.density;
    if (meta) meta.content = scheme === 'dark' ? (s.amoled ? '#000000' : '#0b0c0e') : '#f4f2ec';
  }
  watchSelect(store, (s) => [s.settings.theme, s.settings.amoled, s.settings.reduceMotion, s.settings.density], apply);
  new MutationObserver(apply).observe(document.documentElement, { attributes: true, attributeFilter: ['data-theme'] });
  dark.addEventListener?.('change', apply);
  reduce.addEventListener?.('change', apply);
  apply();
}

export function start(root, env = {}) {
  const app = createApp(env);
  const { store, actions } = app;
  const ids = LENSES.map((l) => l.id);

  // ---- layout: one stream, one bar ----
  const stream = createStream(app);
  const shell = h('div', { class: 'shell' }, stream.el);
  const palette = createPalette(app, { onFilter: (q) => stream.setQuery(q), inertTarget: shell });
  const viewer = createViewer(app, { inertTarget: shell });
  const toaster = createToaster(root);
  // First in the tab order, visible only when focused: the keyboard route to the controls.
  const skip = h('button', { type: 'button', class: 'skip', onClick: () => palette.open() }, 'Controls');
  root.append(skip, shell, palette.el, viewer.el);
  app.ui.notify = (m) => toaster.show(m);
  app.ui.copy = copyText;
  app.ui.download = downloadText;

  bindTheme(store);

  // ---- rendering: any change the stream or bar depends on re-renders, nothing else ----
  const deps = (s) => [s.route, s.thread, s.viewer, s.user, s.settings, s.overrides, s.feed, s.boards, s.watchedLoad, s.clock];
  watchSelect(store, deps, (s) => { stream.render(s); palette.render(s); viewer.render(s); });

  // ---- routing: the lens lives in the hash ----
  const hashOf = (id) => lensById(id).hash;
  watchSelect(store, (s) => [s.route, s.thread?.key ?? null], (s) => {
    palette.reset();
    if (location.hash.replace(/^#/, '') !== hashOf(s.route)) location.hash = hashOf(s.route);
  });
  window.addEventListener('hashchange', () => {
    const id = lensFromHash(location.hash);
    if (id && id !== store.get().route) actions.setRoute(id);
  });
  const initial = lensFromHash(location.hash);
  if (initial) store.set({ route: initial });
  stream.render(store.get());
  palette.render(store.get());
  viewer.render(store.get());
  if (location.hash.replace(/^#/, '') !== hashOf(store.get().route)) location.hash = hashOf(store.get().route);

  // ---- leaving a thread: Escape (when not typing) or the Back button ----
  document.addEventListener('keydown', (e) => {
    if (e.key === 'Escape' && store.get().thread && !viewer.isOpen() && !palette.isOpen()) actions.closeThread();
  });

  // ---- opening the controls: long press, right-click, or "/" ----
  const NO_MENU = '.pl, .media-holder, video, img, input, textarea, a';
  let hold = null;
  let swallowClick = false;
  const cancelHold = () => { clearTimeout(hold?.timer); hold = null; };
  stream.el.addEventListener('pointerdown', (e) => {
    if (e.target.closest(NO_MENU) || (e.pointerType === 'mouse' && e.button !== 0)) return;
    hold = { x: e.clientX, y: e.clientY, timer: setTimeout(() => { hold = null; swallowClick = true; getSelection()?.removeAllRanges(); palette.open(); }, 450) };
  });
  stream.el.addEventListener('pointermove', (e) => { if (hold && Math.hypot(e.clientX - hold.x, e.clientY - hold.y) > 10) cancelHold(); });
  for (const type of ['pointerup', 'pointercancel']) stream.el.addEventListener(type, cancelHold);
  stream.el.addEventListener('scroll', cancelHold, { passive: true });
  // The tap that ends a long press must not also toggle the line under the finger.
  stream.el.addEventListener('click', (e) => { if (swallowClick) { swallowClick = false; e.stopPropagation(); e.preventDefault(); } }, true);
  stream.el.addEventListener('contextmenu', (e) => {
    if (e.target.closest(NO_MENU)) return;
    e.preventDefault();
    palette.open();
  });
  document.addEventListener('keydown', (e) => {
    if (e.key !== '/' || e.ctrlKey || e.metaKey || e.altKey || palette.isOpen() || viewer.isOpen()) return;
    if (e.target.closest?.('input, textarea, [contenteditable]')) return;
    e.preventDefault();
    palette.open({ command: true });
  });

  // ---- swipe between adjacent lenses (touch/pen only; strict thresholds) ----
  let gesture = null;
  stream.el.addEventListener('pointerdown', (e) => {
    if (e.pointerType === 'mouse' || e.target.closest('[data-noswipe],input,textarea,select')) { gesture = null; return; }
    gesture = { x: e.clientX, y: e.clientY, t: e.timeStamp };
  });
  stream.el.addEventListener('pointerup', (e) => {
    if (!gesture) return;
    const dir = swipeDecision({ dx: e.clientX - gesture.x, dy: e.clientY - gesture.y, dt: e.timeStamp - gesture.t });
    gesture = null;
    if (dir === 0) return;
    if (store.get().thread) { if (dir === -1) actions.closeThread(); return; }
    actions.setRoute(ids[clampIndex(ids.indexOf(store.get().route) + dir, ids.length)]);
  });
  stream.el.addEventListener('pointercancel', () => { gesture = null; });

  app.stream = stream;
  app.viewer = viewer;
  app.palette = palette;
  app.start();
  return app;
}
