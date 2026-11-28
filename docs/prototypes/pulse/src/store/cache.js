// Last-good-result cache with age and bounded size. Memory is primary; persistence
// is best effort (large payloads may not fit storage, and that is fine).

const DAY = 86400000;

export function createCache({ storage, now = Date.now, maxEntries = 20, maxAgeMs = 7 * DAY } = {}) {
  const entries = new Map(Object.entries(storage?.get('cache', {}) ?? {}));

  function evict() {
    const t = now();
    for (const [k, v] of entries) if (!v || typeof v.at !== 'number' || t - v.at > maxAgeMs) entries.delete(k);
    while (entries.size > maxEntries) {
      let oldestKey = null;
      let oldestAt = Infinity;
      for (const [k, v] of entries) if (v.at < oldestAt) { oldestAt = v.at; oldestKey = k; }
      entries.delete(oldestKey);
    }
  }

  function persist() {
    storage?.set('cache', Object.fromEntries(entries));
  }

  evict();

  return {
    get(key) {
      const e = entries.get(key);
      if (!e) return null;
      if (now() - e.at > maxAgeMs) { entries.delete(key); return null; }
      return e;
    },
    set(key, value) {
      entries.set(key, { value, at: now() });
      evict();
      persist();
    },
    clear() {
      entries.clear();
      persist();
    },
    size: () => entries.size,
  };
}
