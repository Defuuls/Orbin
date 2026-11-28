// Data-driven settings. The registry is the single source of truth: it renders the
// page, supplies defaults, validates persisted values and powers settings search.

export const SETTINGS_SECTIONS = [
  {
    id: 'appearance',
    title: 'Appearance',
    items: [
      {
        id: 'theme', type: 'choice', label: 'Theme', hint: 'Follow the system or pick a fixed look.',
        default: 'system',
        options: [{ value: 'system', label: 'System' }, { value: 'light', label: 'Light' }, { value: 'dark', label: 'Dark' }],
      },
      { id: 'amoled', type: 'toggle', label: 'AMOLED black', hint: 'Pure black backgrounds in dark mode.', default: false },
      {
        id: 'density', type: 'choice', label: 'Line density', hint: 'Compact puts each thread on one line.',
        default: 'regular',
        options: [{ value: 'regular', label: 'Regular' }, { value: 'compact', label: 'Compact' }],
      },
      { id: 'reduceMotion', type: 'toggle', label: 'Reduce motion', hint: 'Use fades instead of slides.', default: false },
    ],
  },
  {
    id: 'reading',
    title: 'Reading',
    items: [
      { id: 'dimRead', type: 'toggle', label: 'Dim read threads', hint: 'De-emphasize threads you have opened.', default: true },
      {
        id: 'refreshSec', type: 'choice', label: 'Refresh interval', hint: 'How often watched threads update while the app is open.',
        default: 30,
        options: [{ value: 30, label: '30 s' }, { value: 60, label: '1 min' }, { value: 300, label: '5 min' }],
      },
    ],
  },
  {
    id: 'media',
    title: 'Media',
    items: [
      {
        id: 'autoplay', type: 'scoped', label: 'Autoplay previews',
        hint: 'Set for the whole app, or override for one source.', default: false,
      },
    ],
  },
  {
    id: 'privacy',
    title: 'Privacy',
    items: [
      { id: 'saveHistory', type: 'toggle', label: 'Save browsing history', hint: 'Record opened threads on this device only.', default: true },
      {
        id: 'clearActivity', type: 'action', label: 'Clear local activity',
        hint: 'Removes browsing history and cached data. Watched and saved threads stay.',
        actionLabel: 'Clear', confirmLabel: 'Tap again to confirm',
      },
    ],
  },
  {
    id: 'demo',
    title: 'Test conditions',
    items: [
      {
        id: 'connection', type: 'choice', label: 'Simulated connection',
        hint: 'See how every screen behaves when the network degrades.',
        keywords: ['network', 'offline', 'stale', 'error', 'slow', 'retry'],
        default: 'normal',
        options: [{ value: 'normal', label: 'Normal' }, { value: 'slow', label: 'Slow' }, { value: 'offline', label: 'Offline' }],
      },
    ],
  },
];

export function defaultSettings(sections = SETTINGS_SECTIONS) {
  const out = {};
  for (const s of sections) {
    for (const item of s.items) {
      if (item.type !== 'action') out[item.id] = item.default;
    }
  }
  return out;
}

// Merge persisted values over defaults, dropping unknown keys and invalid values.
export function validateSettings(raw, sections = SETTINGS_SECTIONS) {
  const out = defaultSettings(sections);
  if (!raw || typeof raw !== 'object') return out;
  for (const s of sections) {
    for (const item of s.items) {
      if (!(item.id in raw)) continue;
      const v = raw[item.id];
      if (item.type === 'toggle' || item.type === 'scoped') {
        if (typeof v === 'boolean') out[item.id] = v;
      } else if (item.type === 'choice') {
        if (item.options.some((o) => o.value === v)) out[item.id] = v;
      }
    }
  }
  return out;
}

// Section-aware search over labels, hints, option labels, keywords and section titles.
export function searchSettings(query, sections = SETTINGS_SECTIONS) {
  const q = String(query ?? '').trim().toLowerCase();
  if (!q) return sections;
  const words = q.split(/\s+/);
  const out = [];
  for (const section of sections) {
    const items = section.items.filter((item) => {
      const hay = [section.title, item.label, item.hint, ...(item.keywords ?? []), ...(item.options ?? []).map((o) => o.label)]
        .filter(Boolean).join(' ').toLowerCase();
      return words.every((w) => hay.includes(w));
    });
    if (items.length) out.push({ ...section, items });
  }
  return out;
}

// ---- scoped overrides: board -> site -> app -> default ----

export function scopeId(kind, source, board) {
  if (kind === 'board') return `board:${source}/${board}`;
  if (kind === 'site') return `site:${source}`;
  return 'app';
}

export function resolveScoped(overrides, id, { source, board } = {}, appValue) {
  const chain = [];
  if (source && board) chain.push(scopeId('board', source, board));
  if (source) chain.push(scopeId('site', source));
  for (const scope of chain) {
    const o = overrides[scope];
    if (o && Object.prototype.hasOwnProperty.call(o, id)) return { value: o[id], from: scope };
  }
  return { value: appValue, from: 'app' };
}

export function setScoped(overrides, scope, id, value) {
  return { ...overrides, [scope]: { ...(overrides[scope] ?? {}), [id]: value } };
}

export function resetScoped(overrides, scope, id) {
  const current = overrides[scope];
  if (!current || !(id in current)) return overrides;
  const { [id]: _removed, ...rest } = current;
  const next = { ...overrides };
  if (Object.keys(rest).length) next[scope] = rest;
  else delete next[scope];
  return next;
}

export function sanitizeOverrides(raw) {
  const out = {};
  if (!raw || typeof raw !== 'object') return out;
  for (const [scope, vals] of Object.entries(raw)) {
    if (!(scope === 'app' || scope.startsWith('site:') || scope.startsWith('board:'))) continue;
    if (!vals || typeof vals !== 'object') continue;
    const clean = {};
    for (const [id, v] of Object.entries(vals)) if (typeof v === 'boolean') clean[id] = v;
    if (Object.keys(clean).length) out[scope] = clean;
  }
  return out;
}
