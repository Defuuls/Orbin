// A tiny store: one state object, subscribers, no magic.

export function createStore(initial) {
  let state = initial;
  const subscribers = new Set();
  return {
    get: () => state,
    set(patch) {
      const next = typeof patch === 'function' ? patch(state) : { ...state, ...patch };
      if (next === state) return;
      state = next;
      for (const fn of [...subscribers]) fn(state);
    },
    subscribe(fn) {
      subscribers.add(fn);
      return () => subscribers.delete(fn);
    },
  };
}

// Calls `fn` only when one of the selected values changed (strict equality).
export function watchSelect(store, select, fn) {
  let prev = select(store.get());
  return store.subscribe((state) => {
    const next = select(state);
    if (next.length === prev.length && next.every((v, i) => v === prev[i])) return;
    prev = next;
    fn(state);
  });
}
