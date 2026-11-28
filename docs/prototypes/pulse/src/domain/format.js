export function formatAge(ms) {
  const safe = Number.isFinite(ms) && ms > 0 ? ms : 0;
  const s = Math.floor(safe / 1000);
  if (s < 45) return 'just now';
  const m = Math.round(s / 60);
  if (m < 60) return `${m} min ago`;
  const h = Math.round(m / 60);
  if (h < 24) return `${h} h ago`;
  return `${Math.round(h / 24)} d ago`;
}

export function formatAgeShort(ms) {
  const safe = Number.isFinite(ms) && ms > 0 ? ms : 0;
  const s = Math.floor(safe / 1000);
  if (s < 45) return 'now';
  const m = Math.round(s / 60);
  if (m < 60) return `${m}m`;
  const h = Math.round(m / 60);
  if (h < 24) return `${h}h`;
  return `${Math.round(h / 24)}d`;
}

export function formatCount(n) {
  const v = Number.isFinite(n) && n > 0 ? Math.floor(n) : 0;
  if (v < 1000) return String(v);
  if (v < 10000) return `${(v / 1000).toFixed(1).replace(/\.0$/, '')}k`;
  return `${Math.round(v / 1000)}k`;
}

export function plural(n, one, many) {
  return n === 1 ? one : many;
}
