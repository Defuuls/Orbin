import { h, setText } from './dom.js';
import { formatAge } from '../domain/format.js';

// StateBoundary: one component for loading, refreshing, stale, error and empty, so no
// screen ever shows a blank page or a spinner-only page, and every screen states how
// old its data is. Existing content always stays visible during refresh and on failure.

export function createBoundary({ list, empty, onRetry, now, skeletonCount = 6 }) {
  const bannerText = h('span', { class: 'banner-text' });
  const retry = h('button', { type: 'button', class: 'btn text', onClick: () => onRetry?.() }, 'Retry');
  const banner = h('div', { class: 'banner', role: 'status', 'aria-live': 'polite', hidden: true }, bannerText, retry);
  const bar = h('div', { class: 'refresh-bar', hidden: true });

  const skeleton = h('ul', { class: 'rows skeleton', 'aria-hidden': 'true', hidden: true });
  for (let i = 0; i < skeletonCount; i++) {
    skeleton.append(h('li', { class: 'sk-row' }, h('i'), h('i')));
  }

  const emptyTitle = h('p', { class: 'panel-title' });
  const emptyHint = h('p', { class: 'panel-hint' });
  const emptyAction = h('button', { type: 'button', class: 'btn', hidden: true });
  const emptyEl = h('div', { class: 'panel', hidden: true }, emptyTitle, emptyHint, emptyAction);

  const errTitle = h('p', { class: 'panel-title' }, "Can't load right now");
  const errHint = h('p', { class: 'panel-hint' });
  const errEl = h('div', { class: 'panel', role: 'alert', hidden: true }, errTitle, errHint, h('button', { type: 'button', class: 'btn', onClick: () => onRetry?.() }, 'Try again'));

  let emptyConfig = empty;
  let actionHandler = null;
  emptyAction.addEventListener('click', () => actionHandler?.());

  function applyEmpty(cfg) {
    setText(emptyTitle, cfg.title);
    setText(emptyHint, cfg.hint);
    actionHandler = cfg.onAction ?? null;
    emptyAction.hidden = !cfg.actionLabel;
    if (cfg.actionLabel) setText(emptyAction, cfg.actionLabel);
  }
  applyEmpty(emptyConfig);

  const el = h('div', { class: 'boundary' }, bar, banner, skeleton, emptyEl, errEl, list);

  return {
    el,
    setEmpty(cfg) { emptyConfig = cfg; applyEmpty(cfg); },
    // state: { status, at, error, count }
    update({ status, at, error, count }) {
      const hasContent = count > 0;
      const showSkeleton = status === 'loading' && !hasContent;
      const showError = status === 'error' && !hasContent;
      const showEmpty = !hasContent && !showSkeleton && !showError;
      const refreshing = status === 'refreshing' || (status === 'loading' && hasContent);
      // A failed load that still has lines to show (saved or watched) behaves like a stale one:
      // keep the lines, say what went wrong and offer Retry. Never fail silently.
      const stale = status === 'stale' || (status === 'error' && hasContent);

      skeleton.hidden = !showSkeleton;
      errEl.hidden = !showError;
      emptyEl.hidden = !showEmpty;
      list.hidden = !hasContent;
      bar.hidden = !(refreshing || showSkeleton);

      if (showError) setText(errHint, error?.message ?? 'Something went wrong.');

      if (stale) {
        const age = at ? ` · updated ${formatAge(now() - at)}` : '';
        setText(bannerText, `${error?.message ?? "Couldn't refresh."} Showing saved data${age}`);
        banner.hidden = false;
        retry.hidden = false;
      } else if (refreshing && hasContent) {
        setText(bannerText, 'Refreshing…');
        banner.hidden = false;
        retry.hidden = true;
      } else {
        banner.hidden = true;
      }
    },
  };
}
