import { createStore } from './store/store.js';
import { createStorage } from './store/storage.js';
import { createCache } from './store/cache.js';
import { createLoader, mergeLoad } from './store/loader.js';
import { createBackoff } from './store/backoff.js';
import { createMockDataSource, SOURCES } from './data/mock.js';
import { boardKey } from './domain/keys.js';
import { sortWatched } from './domain/unread.js';
import {
  sanitizeUser, snapshot, toggleKey, toggleWatch, toggleSave, recordHistory, markWatchedSeen,
  markAllSeen, applyRefreshAll, clearActivity,
} from './domain/collections.js';
import { toggleClip, toggleLinkClip, removeClip, clearClips, exportText, exportMarkdown } from './domain/clips.js';
import {
  validateSettings, sanitizeOverrides, setScoped, resetScoped,
} from './domain/settings.js';
import { makeRowContext } from './domain/row-model.js';
import { LENSES } from './domain/stream.js';
import { collectMedia, neighbor } from './domain/media.js';

// State, actions, polling and persistence. No DOM in here except guarded feature checks,
// so all of it can be driven by tests.
export function createApp({ backend, now = Date.now, dataSource, autoPoll = true } = {}) {
  const storage = createStorage(backend);
  const ds = dataSource ?? createMockDataSource({ now });
  const labels = Object.fromEntries(SOURCES.map((s) => [s.id, s.label]));
  const cache = createCache({ storage, now });
  const loader = createLoader({ cache, now });
  const backoff = createBackoff({ baseMs: 15000, maxMs: 300000 });

  const storedUser = storage.get('user', null);
  const store = createStore({
    route: 'home',
    user: storedUser ? sanitizeUser(storedUser) : ds.demoUser(now()),
    settings: validateSettings(storage.get('settings', null)),
    overrides: sanitizeOverrides(storage.get('overrides', null)),
    thread: null,
    viewer: null,
    feed: { status: 'loading' },
    boards: { status: 'loading' },
    watchedLoad: { status: 'ready', at: 0 },
    clock: now(),
  });

  // Side effects that need a DOM are injected by the shell; tests can stub them.
  const ui = { notify: () => {}, copy: async () => false, download: async () => false };
  let feedToken = 0;
  let watchToken = 0;

  // ---- derived data ----
  const selectors = {
    followed: (s) => new Set(s.user.follows),
    homeThreads(s) {
      const followed = new Set(s.user.follows);
      return (s.feed.data ?? []).filter((t) => followed.has(boardKey(t.source, t.board)));
    },
    watchedList: (s) => sortWatched(Object.values(s.user.watched)),
    savedList: (s) => Object.values(s.user.saved).sort((a, b) => (b.savedAt ?? 0) - (a.savedAt ?? 0)),
    historyList: (s) => s.user.history,
  };
  const rowContext = (s) => makeRowContext({ user: s.user, settings: s.settings, now: now(), labels });

  // ---- loading ----
  async function loadFeed({ background = false } = {}) {
    const follows = [...store.get().user.follows];
    const token = ++feedToken;
    if (follows.length === 0) {
      store.set((s) => ({ ...s, feed: { status: 'ready', data: [], at: now() } }));
      return true;
    }
    const key = `feed:${[...follows].sort().join(',')}`;
    return loader.load(key, () => ds.getFeed(follows), (st) => {
      if (token !== feedToken) return;
      const quiet = background && (st.status === 'loading' || st.status === 'refreshing');
      if (quiet && store.get().feed.data !== undefined) return;
      store.set((s) => ({ ...s, feed: mergeLoad(s.feed, st) }));
    });
  }

  async function loadBoards() {
    return loader.load('boards', () => ds.listBoards(), (st) => {
      store.set((s) => ({ ...s, boards: mergeLoad(s.boards, st) }));
    });
  }

  async function refreshWatched({ background = false } = {}) {
    const keys = Object.keys(store.get().user.watched);
    const token = ++watchToken;
    if (keys.length === 0) {
      store.set((s) => ({ ...s, watchedLoad: { status: 'ready', at: now() } }));
      return true;
    }
    if (!background) store.set((s) => ({ ...s, watchedLoad: { ...s.watchedLoad, status: 'refreshing' } }));
    const res = await ds.refreshThreads(keys);
    if (token !== watchToken) return null;
    if (res.ok) {
      store.set((s) => ({ ...s, user: applyRefreshAll(s.user, res.value), watchedLoad: { status: 'ready', at: now() } }));
      return true;
    }
    store.set((s) => ({ ...s, watchedLoad: { status: 'stale', at: s.watchedLoad.at, error: res.error } }));
    return false;
  }

  // The open thread (if any) loads like everything else: cached, stale-while-revalidate.
  async function loadThread() {
    const open = store.get().thread;
    if (!open) return true;
    const key = open.key;
    return loader.load(`thread:${key}`, () => ds.getThread(key), (st) => {
      store.set((s) => (s.thread && s.thread.key === key ? { ...s, thread: { ...s.thread, load: mergeLoad(s.thread.load, st) } } : s));
    });
  }

  const refreshAll = () => Promise.all([loadFeed(), refreshWatched(), loadBoards(), loadThread()]);

  // ---- user actions ----
  function applyConnection(value) {
    ds.setFaults?.({ offline: value === 'offline', delayMs: value === 'slow' ? 1800 : 0 });
  }

  const actions = {
    // Unknown lenses are ignored, so a bad hash or command can never blank the stream.
    setRoute(route) {
      if (LENSES.some((l) => l.id === route)) store.set({ route, thread: null, viewer: null });
    },
    // Opening a thread records history and marks it seen, then makes its posts the stream.
    openThreadView(thread) {
      actions.openThread(thread);
      store.set({ thread: { key: thread.key, snapshot: snapshot(thread), load: { status: 'loading' } } });
      return loadThread();
    },
    closeThread() {
      if (store.get().thread) store.set({ thread: null, viewer: null });
    },
    // The viewer pages through the open thread's media; it is closed by anything that
    // removes that media from view.
    openViewer(id) {
      const list = collectMedia(store.get().thread?.load.data?.posts);
      if (list.some((m) => m.id === id)) store.set({ viewer: { id } });
    },
    stepViewer(dir) {
      const s = store.get();
      const next = s.viewer ? neighbor(collectMedia(s.thread?.load.data?.posts), s.viewer.id, dir) : null;
      if (next) store.set({ viewer: { id: next.id } });
    },
    closeViewer() {
      if (store.get().viewer) store.set({ viewer: null });
    },
    loadFeed, loadBoards, refreshWatched, refreshAll, loadThread,

    toggleFollow(bk) {
      store.set((s) => ({ ...s, user: { ...s.user, follows: toggleKey(s.user.follows, bk) } }));
      loadFeed();
    },
    toggleFavorite(bk) {
      store.set((s) => ({ ...s, user: { ...s.user, favorites: toggleKey(s.user.favorites, bk) } }));
    },
    toggleWatch(thread) {
      const res = toggleWatch(store.get().user, thread, now());
      if (res.limited) { ui.notify('Watch limit reached. Stop watching a thread to add another.'); return; }
      store.set((s) => ({ ...s, user: res.user }));
    },
    toggleSave(thread) {
      store.set((s) => ({ ...s, user: toggleSave(s.user, thread, now()) }));
    },
    toggleClip(thread) {
      const res = toggleClip(store.get().user, thread, now());
      if (res.limited) { ui.notify('Clip limit reached. Remove a clip to add another.'); return; }
      const added = res.user.clips.length > store.get().user.clips.length;
      store.set((s) => ({ ...s, user: res.user }));
      ui.notify(added ? 'Link clipped' : 'Clip removed');
    },
    // A link typed inside a post: kept with the thread and post it came from.
    toggleLinkClip(url, thread, post) {
      const before = store.get().user.clips.length;
      const res = toggleLinkClip(store.get().user, url, thread, post, now());
      if (res.limited) { ui.notify('Clip limit reached. Remove a clip to add another.'); return; }
      store.set((s) => ({ ...s, user: res.user }));
      ui.notify(res.user.clips.length > before ? 'Link clipped' : 'Clip removed');
    },
    removeClip(url) {
      store.set((s) => ({ ...s, user: removeClip(s.user, url) }));
    },
    clearClips() {
      store.set((s) => ({ ...s, user: clearClips(s.user) }));
    },
    // Export what is on the internal clipboard: as text to the system clipboard, or a file.
    async exportClips(format) {
      const clips = store.get().user.clips;
      if (!clips.length) { ui.notify('Nothing to export'); return false; }
      if (format === 'markdown') {
        const ok = await ui.download('clips.md', exportMarkdown(clips, labels), 'text/markdown');
        ui.notify(ok ? `Downloaded ${clips.length} ${clips.length === 1 ? 'link' : 'links'}` : 'Download not available here');
        return ok;
      }
      const ok = await ui.copy(exportText(clips, labels));
      ui.notify(ok ? `Copied ${clips.length} ${clips.length === 1 ? 'link' : 'links'}` : 'Copy not available here');
      return ok;
    },
    openThread(thread) {
      store.set((s) => {
        let user = s.settings.saveHistory ? recordHistory(s.user, thread, now()) : s.user;
        user = markWatchedSeen(user, thread.key);
        return user === s.user ? s : { ...s, user };
      });
    },
    markAllRead() {
      store.set((s) => ({ ...s, user: markAllSeen(s.user) }));
    },
    setSetting(id, value) {
      store.set((s) => ({ ...s, settings: { ...s.settings, [id]: value } }));
      if (id === 'connection') { applyConnection(value); refreshAll(); }
    },
    setScopedValue(scope, id, value) {
      store.set((s) => ({ ...s, overrides: setScoped(s.overrides, scope, id, value) }));
    },
    resetScopedValue(scope, id) {
      store.set((s) => ({ ...s, overrides: resetScoped(s.overrides, scope, id) }));
    },
    clearActivity() {
      cache.clear();
      store.set((s) => ({ ...s, user: clearActivity(s.user) }));
    },
  };

  // ---- persistence (debounced; flushed when the page hides) ----
  let saveTimer = 0;
  let lastSaved = {};
  function flush() {
    clearTimeout(saveTimer);
    const s = store.get();
    if (s.user !== lastSaved.user) storage.set('user', s.user);
    if (s.settings !== lastSaved.settings) storage.set('settings', s.settings);
    if (s.overrides !== lastSaved.overrides) storage.set('overrides', s.overrides);
    lastSaved = { user: s.user, settings: s.settings, overrides: s.overrides };
  }
  lastSaved = { user: store.get().user, settings: store.get().settings, overrides: store.get().overrides };
  if (!storedUser) flush();
  store.subscribe((s) => {
    if (s.user === lastSaved.user && s.settings === lastSaved.settings && s.overrides === lastSaved.overrides) return;
    clearTimeout(saveTimer);
    saveTimer = setTimeout(flush, 150);
  });

  // ---- polling: visible + online only, exponential backoff on failure ----
  let pollTimer = 0;
  let lastPoll = 0;
  const intervalMs = () => store.get().settings.refreshSec * 1000;
  const paused = () =>
    (typeof document !== 'undefined' && document.hidden) || (typeof navigator !== 'undefined' && navigator.onLine === false);

  async function pollOnce() {
    clearTimeout(pollTimer);
    let delay = intervalMs();
    if (!paused()) {
      lastPoll = now();
      const results = await Promise.all([loadFeed({ background: true }), refreshWatched({ background: true })]);
      if (results.some((r) => r === false)) delay = Math.max(backoff.next(), delay);
      else backoff.reset();
    }
    pollTimer = setTimeout(pollOnce, delay);
  }

  let clockTimer = 0;
  function start() {
    // Restore a persisted test condition, but never override a data source's own state.
    if (store.get().settings.connection !== 'normal') applyConnection(store.get().settings.connection);
    refreshAll();
    if (!autoPoll) return;
    pollTimer = setTimeout(pollOnce, intervalMs());
    clockTimer = setInterval(() => store.set({ clock: now() }), 15000);
    if (typeof document !== 'undefined') {
      document.addEventListener('visibilitychange', () => {
        if (document.hidden) { flush(); return; }
        if (now() - lastPoll >= intervalMs()) pollOnce();
        store.set({ clock: now() });
      });
      window.addEventListener('online', () => pollOnce());
      window.addEventListener('pagehide', flush);
    }
  }
  function stop() {
    clearTimeout(pollTimer);
    clearInterval(clockTimer);
    flush();
  }

  return { store, actions, selectors, rowContext, ui, ds, labels, now, storage, cache, flush, start, stop, pollOnce };
}
