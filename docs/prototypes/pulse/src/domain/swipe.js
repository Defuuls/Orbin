// Decide whether a pointer gesture is a deliberate horizontal swipe between tabs.
// Returns +1 (next tab), -1 (previous tab) or 0 (not a swipe). Strict on purpose:
// vertical scrolling and taps must never be mistaken for navigation.

export const SWIPE_MIN_DX = 72;
export const SWIPE_RATIO = 2.5;
export const SWIPE_MAX_MS = 600;

export function swipeDecision({ dx, dy, dt }) {
  const ax = Math.abs(dx);
  const ay = Math.abs(dy);
  if (!Number.isFinite(ax) || !Number.isFinite(ay) || !Number.isFinite(dt)) return 0;
  if (dt > SWIPE_MAX_MS || ax < SWIPE_MIN_DX || ax < SWIPE_RATIO * ay) return 0;
  return dx < 0 ? 1 : -1;
}

export function clampIndex(index, length) {
  return Math.min(Math.max(index, 0), length - 1);
}

// A deliberate downward drag (used to dismiss the media viewer).
export const DISMISS_MIN_DY = 96;
export function dismissDecision({ dx, dy, dt }) {
  if (![dx, dy, dt].every(Number.isFinite)) return false;
  return dt <= SWIPE_MAX_MS && dy >= DISMISS_MIN_DY && dy >= SWIPE_RATIO * Math.abs(dx);
}
