// FLIP for the stream: measure where lines are, let the DOM reorder, then animate each
// line from its old position to its new one with transform only. Only lines near the
// viewport are measured (capped), so a list of thousands costs the same as a list of 30.

export const MAX_ANIMATED = 40;

// Visible children of `list`, found by binary search on offsetTop (monotonic in a flow list).
export function visibleLines(list, scroller) {
  const kids = list.children;
  const n = kids.length;
  if (!n) return [];
  const top = scroller.scrollTop;
  const bottom = top + scroller.clientHeight;
  let lo = 0;
  let hi = n - 1;
  while (lo < hi) {
    const mid = (lo + hi) >> 1;
    const k = kids[mid];
    if (k.offsetTop + k.offsetHeight < top) lo = mid + 1;
    else hi = mid;
  }
  const out = [];
  for (let i = lo; i < n && out.length < MAX_ANIMATED; i++) {
    if (kids[i].offsetTop > bottom) break;
    out.push(kids[i]);
  }
  return out;
}

export function captureRects(list, scroller) {
  const rects = new Map();
  for (const el of visibleLines(list, scroller)) rects.set(el, el.getBoundingClientRect().top);
  return rects;
}

// Returns how many lines moved, for diagnostics and tests.
export function playFlip(before, list, scroller, { reduce = false } = {}) {
  if (reduce || before.size === 0 || typeof list.animate !== 'function') return { moved: 0, entered: 0 };
  const limit = scroller.clientHeight * 3;
  let moved = 0;
  for (const [el, oldTop] of before) {
    if (!el.isConnected) continue;
    const delta = oldTop - el.getBoundingClientRect().top;
    if (Math.abs(delta) < 1 || Math.abs(delta) > limit) continue;
    el.animate([{ transform: `translateY(${delta}px)` }, { transform: 'translateY(0)' }], { duration: 280, easing: 'cubic-bezier(.2,.8,.2,1)' });
    moved += 1;
  }
  let entered = 0;
  for (const el of visibleLines(list, scroller)) {
    if (before.has(el)) continue;
    el.animate([{ opacity: 0 }, { opacity: 1 }], { duration: 200, easing: 'ease-out' });
    entered += 1;
  }
  return { moved, entered };
}
