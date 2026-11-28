import { ok, err } from './result.js';
import { boardKey, threadKey, parseThreadKey } from '../domain/keys.js';
import { emptyUser, toggleKey, toggleWatch } from '../domain/collections.js';
import { markSeen } from '../domain/unread.js';
import { DEMO_VIDEO } from './demo-video.js';

// Deterministic mock DataSource with fault injection. A real source implements the
// same four methods; nothing else in the app knows this file exists.

export const SOURCES = [
  { id: 'lake', label: 'Lakeside' },
  { id: 'ridge', label: 'Ridgeline' },
];

// Two sources deliberately share a board name AND thread numbers.
export const BOARDS = [
  { source: 'lake', board: 'gdn', name: 'Gardening' },
  { source: 'lake', board: 'pho', name: 'Photography' },
  { source: 'lake', board: 'ret', name: 'Retro Computing' },
  { source: 'lake', board: 'wx', name: 'Weather Watching' },
  { source: 'ridge', board: 'gdn', name: 'Gardening' },
  { source: 'ridge', board: 'trn', name: 'Transit' },
  { source: 'ridge', board: 'ckg', name: 'Cooking' },
  { source: 'ridge', board: 'aud', name: 'Audio' },
].map((b) => ({ ...b, key: boardKey(b.source, b.board) }));

const TOPICS = {
  gdn: ['Tomato blight', 'Raised bed layout', 'Compost ratios', 'Pruning roses', 'Seed saving', 'Frost dates', 'Container herbs', 'Soil pH testing', 'Pollinator strip', 'Greenhouse vents'],
  pho: ['Lens for low light', 'Film or digital', 'Street composition', 'Golden hour tips', 'Tripod picks', 'Editing workflow', 'Black and white', 'Macro setup', 'Filter stacking', 'Print sizes'],
  ret: ['Restoring a 486', 'CRT calibration', 'Floppy recovery', 'Capacitor swap', 'Boot disk archive', 'Serial null modem', 'Sound card jumpers', 'BIOS battery', 'Keyboard cleaning', 'Tape drives'],
  wx: ['Squall line tracking', 'Reading radar', 'Barometer log', 'Backyard station', 'Fog formation', 'Lightning distance', 'Pressure trends', 'Snow load', 'Wind chill', 'Cloud atlas'],
  trn: ['Timetable changes', 'Track gauge', 'Night service', 'Bikes on trains', 'Station access', 'Signal systems', 'Fare capping', 'Tram routes', 'Ferry link', 'Platform design'],
  ckg: ['Sourdough starter', 'Cast iron care', 'Knife sharpening', 'Stock from scraps', 'Fermentation', 'Pressure cooking', 'Pantry staples', 'Spice blends', 'Meal prep', 'Pickling'],
  aud: ['Room treatment', 'Vinyl cleaning', 'Headphone amps', 'Cable myths', 'Tube swaps', 'DAC comparison', 'Speaker placement', 'Tape hiss', 'Mic placement', 'Mastering levels'],
};
const OPENERS = ['Has anyone tried this before?', 'Looking for advice on this.', 'Sharing what worked for me.', 'Quick question for the regulars.'];
const REPLIES = ['Same here, worked fine.', 'Not sure that is right.', 'Try it once more and report back.', 'Thanks, that helped a lot.', 'Source?', 'Works for me after the update.', 'Might be seasonal.', 'Did you check the basics first?'];
const MAX_POSTS = 60;
const SIZES = [[800, 600], [600, 800], [1280, 720], [1000, 1000]];

// Generated test pictures as data URIs, so the prototype needs no network.
function svgImage(label, w, h, hue) {
  const svg = `<svg xmlns="http://www.w3.org/2000/svg" width="${w}" height="${h}" viewBox="0 0 ${w} ${h}"><defs><linearGradient id="g" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="hsl(${hue},45%,38%)"/><stop offset="1" stop-color="hsl(${(hue + 60) % 360},50%,18%)"/></linearGradient></defs><rect width="100%" height="100%" fill="url(#g)"/><circle cx="${w * 0.7}" cy="${h * 0.32}" r="${Math.min(w, h) * 0.12}" fill="hsla(${hue},80%,80%,.5)"/><path d="M0 ${h} L${w * 0.35} ${h * 0.55} L${w * 0.55} ${h * 0.8} L${w * 0.75} ${h * 0.5} L${w} ${h} Z" fill="hsla(${hue},30%,10%,.55)"/><text x="${w * 0.05}" y="${h * 0.94}" font-family="monospace" font-size="${Math.round(Math.min(w, h) * 0.05)}" fill="#fff">${label}</text></svg>`;
  return `data:image/svg+xml;charset=utf-8,${encodeURIComponent(svg)}`;
}
const SUFFIX = ['', ' - general', ' (part 2)', ' thread', ': what works?', ' tips', ' help'];
const RATES = [0, 0, 0.2, 0.5, 1];
const HOUR = 3600000;

function rng(seed) {
  let a = seed >>> 0;
  return () => {
    a = (a + 0x6d2b79f5) >>> 0;
    let t = a;
    t = Math.imul(t ^ (t >>> 15), t | 1);
    t ^= t + Math.imul(t ^ (t >>> 7), t | 61);
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}

function hashString(s) {
  let h = 2166136261;
  for (let i = 0; i < s.length; i++) h = Math.imul(h ^ s.charCodeAt(i), 16777619);
  return h >>> 0;
}

function buildThreads(def, count, seed, t0) {
  const rand = rng(seed ^ hashString(def.key));
  const topics = TOPICS[def.board] ?? ['Open discussion'];
  const out = [];
  for (let i = 0; i < count; i++) {
    const thread = 1000 + i * 37; // same numbers across sources on purpose
    const topic = topics[i % topics.length];
    const suffix = SUFFIX[Math.floor(i / topics.length) % SUFFIX.length];
    const archived = rand() < 0.08;
    out.push({
      key: threadKey(def.source, def.board, thread),
      source: def.source,
      board: def.board,
      thread,
      title: `${topic}${suffix}`,
      baseReplies: Math.floor(rand() * 400),
      rate: archived ? 0 : RATES[Math.floor(rand() * RATES.length)],
      lastModified: t0 - Math.floor(rand() * 72 * HOUR),
      status: archived ? 'archived' : 'live',
      hue: hashString(`${def.key}/${thread}`) % 360,
    });
  }
  return out;
}

export function createMockDataSource({ now = Date.now, seed = 7, perBoard = 14, faults = {}, extraThreads = [] } = {}) {
  const t0 = now();
  const f = { delayMs: 0, failNext: 0, empty: false, offline: false, hugePerBoard: 0, ...faults };
  const memo = new Map();

  function worldThreads(def) {
    const count = f.hugePerBoard > 0 ? f.hugePerBoard : perBoard;
    const id = `${def.key}:${count}`;
    if (!memo.has(id)) memo.set(id, buildThreads(def, count, seed, t0));
    return memo.get(id);
  }

  function materialize(raw) {
    const grown = raw.status === 'archived' ? 0 : Math.floor(((now() - t0) / 60000) * raw.rate);
    const replyCount = raw.baseReplies + grown;
    return {
      key: raw.key, source: raw.source, board: raw.board, thread: raw.thread, title: raw.title,
      replyCount, lastModified: grown > 0 ? now() : raw.lastModified, status: raw.status, hue: raw.hue,
    };
  }

  function allThreads(boardKeys) {
    const wanted = new Set(boardKeys);
    const out = [];
    for (const def of BOARDS) if (wanted.has(def.key)) for (const raw of worldThreads(def)) out.push(materialize(raw));
    for (const x of extraThreads) if (wanted.has(boardKey(x.source, x.board))) out.push(x);
    return out;
  }

  // Deterministic posts for one thread: the opener plus the most recent replies.
  function buildPosts(t) {
    const rand = rng(seed ^ hashString(t.key));
    const total = Math.min((t.replyCount ?? 0) + 1, MAX_POSTS);
    const posts = [];
    for (let i = 0; i < total; i++) {
      const no = t.thread + i;
      const lines = [];
      if (i === 0) lines.push(t.title, OPENERS[Math.floor(rand() * OPENERS.length)]);
      else {
        if (i > 1 && rand() < 0.45) lines.push(`>>${t.thread + Math.floor(rand() * i)}`);
        if (rand() < 0.2) lines.push(`>${REPLIES[Math.floor(rand() * REPLIES.length)]}`);
        lines.push(REPLIES[Math.floor(rand() * REPLIES.length)]);
      }
      const media = [];
      const roll = rand();
      if (i === 0 || roll < 0.22) {
        const [w, h] = SIZES[Math.floor(rand() * SIZES.length)];
        const name = `IMG_${String(no % 10000).padStart(4, '0')}.jpg`;
        media.push({ kind: 'image', name, bytes: 40000 + Math.floor(rand() * 3400000), w, h, src: svgImage(name, w, h, hashString(`${t.key}/${no}`) % 360) });
      } else if (roll > 0.93) {
        media.push({ kind: 'video', name: `clip_${no % 100}.webm`, bytes: 9090, w: 192, h: 108, duration: 2, src: DEMO_VIDEO });
      }
      if (i > 0 && i % 7 === 3) lines.push(`ref: https://pics.example/${t.board}/${no}.`);
      posts.push({ no, at: (t.lastModified ?? t0) - (total - 1 - i) * 7 * 60000, author: 'Anon', text: lines.join('\n'), media });
    }
    return posts;
  }

  async function gate() {
    if (f.delayMs > 0) await new Promise((r) => setTimeout(r, f.delayMs));
    if (f.offline) return err('offline');
    if (f.failNext > 0) { f.failNext -= 1; return err('server'); }
    return null;
  }

  return {
    faults: f,
    setFaults(patch) { Object.assign(f, patch); },

    async listBoards() {
      const failure = await gate();
      if (failure) return failure;
      return ok(BOARDS.map((b) => ({ ...b, threadCount: f.hugePerBoard || perBoard })));
    },

    async getFeed(boardKeys) {
      const failure = await gate();
      if (failure) return failure;
      if (f.empty || boardKeys.length === 0) return ok([]);
      const list = allThreads(boardKeys);
      list.sort((a, b) => b.lastModified - a.lastModified || a.key.localeCompare(b.key));
      return ok(list);
    },

    async refreshThreads(keys) {
      const failure = await gate();
      if (failure) return failure;
      const wanted = new Set(keys);
      const boardKeysNeeded = new Set(keys.map((k) => k.split('/').slice(0, 2).join('/')));
      const out = {};
      for (const t of allThreads([...boardKeysNeeded])) {
        if (wanted.has(t.key)) out[t.key] = { latestReplyCount: t.replyCount, status: t.status };
      }
      return ok(out);
    },

    async getThread(key) {
      const failure = await gate();
      if (failure) return failure;
      let ref;
      try { ref = parseThreadKey(key); } catch { return err('notfound'); }
      const found = allThreads([boardKey(ref.source, ref.board)]).find((t) => t.key === key);
      if (!found) return err('notfound');
      return ok({ thread: found, posts: found.posts ?? buildPosts(found) });
    },

    // First-run state so every screen has something real to show.
    demoUser(at = now()) {
      let user = emptyUser();
      for (const k of ['lake/gdn', 'lake/pho', 'ridge/trn', 'ridge/gdn']) user = { ...user, follows: toggleKey(user.follows, k) };
      user = { ...user, favorites: ['lake/pho'] };
      const picks = [['lake/gdn', 1, 3], ['lake/gdn', 4, 7], ['ridge/trn', 2, 1]];
      for (const [bk, index, unread] of picks) {
        const def = BOARDS.find((b) => b.key === bk);
        const t = materialize(worldThreads(def)[index]);
        user = toggleWatch(user, t, at).user;
        const e = user.watched[t.key];
        user = { ...user, watched: { ...user.watched, [t.key]: { ...markSeen(e), latestReplyCount: t.replyCount, lastSeenReplyCount: Math.max(0, t.replyCount - unread) } } };
      }
      return user;
    },
  };
}
