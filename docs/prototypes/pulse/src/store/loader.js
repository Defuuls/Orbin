// Stale-while-revalidate loading with a per-key request token, so a slow, old
// response can never overwrite a newer one.

export function createLoader({ cache, now = Date.now }) {
  const tokens = new Map();
  return {
    async load(key, fetcher, onState) {
      const token = (tokens.get(key) ?? 0) + 1;
      tokens.set(key, token);
      const cached = cache.get(key);
      if (cached) onState({ status: 'refreshing', data: cached.value, at: cached.at });
      else onState({ status: 'loading' });
      const res = await fetcher();
      if (tokens.get(key) !== token) return null; // superseded
      if (res.ok) {
        cache.set(key, res.value);
        onState({ status: 'ready', data: res.value, at: now() });
        return true;
      }
      if (cached) onState({ status: 'stale', data: cached.value, at: cached.at, error: res.error });
      else onState({ status: 'error', error: res.error });
      return false;
    },
  };
}

// Fold a loader update into the previous view state. Existing content stays visible
// when a new load starts or fails; only a first-ever failure becomes an error panel.
export function mergeLoad(prev, update) {
  const hasData = update.data !== undefined;
  const data = hasData ? update.data : prev?.data;
  const at = hasData ? update.at : prev?.at;
  let status = update.status;
  if (status === 'error' && data !== undefined) status = 'stale';
  return { status, data, at, error: update.error };
}
