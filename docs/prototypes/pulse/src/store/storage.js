// Safe persistence. localStorage can be missing, full or throw; the app must render
// well without it, so every call is guarded and falls back to memory.

const STORAGE_VERSION = 1;

export function createStorage(backend, prefix = 'pulse:') {
  const memory = new Map();
  let usable = false;
  try {
    if (backend) {
      backend.setItem(`${prefix}__probe`, '1');
      backend.removeItem(`${prefix}__probe`);
      usable = true;
    }
  } catch {
    usable = false;
  }

  return {
    persistent: usable,
    get(key, fallback) {
      try {
        const raw = usable ? backend.getItem(prefix + key) : memory.get(key);
        if (raw == null) return fallback;
        const parsed = JSON.parse(raw);
        if (!parsed || parsed.v !== STORAGE_VERSION) return fallback;
        return parsed.data;
      } catch {
        return fallback;
      }
    },
    set(key, data) {
      const raw = JSON.stringify({ v: STORAGE_VERSION, data });
      memory.set(key, raw);
      if (!usable) return true;
      try {
        backend.setItem(prefix + key, raw);
        return true;
      } catch {
        return false; // quota or privacy mode: memory copy still serves this session
      }
    },
    remove(key) {
      memory.delete(key);
      try { if (usable) backend.removeItem(prefix + key); } catch { /* ignore */ }
    },
  };
}
