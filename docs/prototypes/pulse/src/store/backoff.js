// Exponential backoff with a ceiling. Deterministic unless jitter is supplied.

export function createBackoff({ baseMs = 15000, maxMs = 300000, jitter = 0, random = Math.random } = {}) {
  let failures = 0;
  return {
    next() {
      const raw = Math.min(maxMs, baseMs * 2 ** failures);
      failures += 1;
      const spread = raw * jitter * (random() * 2 - 1);
      return Math.max(0, Math.round(raw + spread));
    },
    reset() { failures = 0; },
    failures: () => failures,
  };
}
