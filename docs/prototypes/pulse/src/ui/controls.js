import { h, setAttr } from './dom.js';

// Shared interactive primitives. Every control is a real <button> with a label and
// state attribute, so it is keyboard- and screen-reader-operable by default.

// Radio-style choice group. Values may be strings or numbers.
export function segmented({ label, options, value, onChange }) {
  const buttons = new Map();
  const root = h('div', { class: 'seg', role: 'radiogroup', 'aria-label': label, 'data-noswipe': '' });
  for (const opt of options) {
    const btn = h(
      'button',
      {
        type: 'button', role: 'radio',
        onClick: () => onChange(opt.value),
        onKeydown: (e) => {
          const i = options.findIndex((o) => o.value === opt.value);
          const delta = e.key === 'ArrowRight' ? 1 : e.key === 'ArrowLeft' ? -1 : 0;
          if (!delta) return;
          e.preventDefault();
          const next = options[(i + delta + options.length) % options.length];
          onChange(next.value);
          buttons.get(next.value)?.focus();
        },
      },
      opt.label,
    );
    buttons.set(opt.value, btn);
    root.append(btn);
  }
  function set(next) {
    for (const [v, btn] of buttons) {
      const on = v === next;
      setAttr(btn, 'aria-checked', String(on));
      setAttr(btn, 'tabindex', on ? '0' : '-1');
    }
  }
  set(value);
  return { el: root, set };
}

export function switchControl({ label, checked, onChange }) {
  const btn = h(
    'button',
    { type: 'button', class: 'switch', role: 'switch', 'aria-label': label, 'aria-checked': String(Boolean(checked)), 'data-noswipe': '' },
    h('span', { class: 'sw-track' }, h('span', { class: 'sw-knob' })),
  );
  btn.addEventListener('click', () => onChange(btn.getAttribute('aria-checked') !== 'true'));
  return {
    el: btn,
    set(v) { setAttr(btn, 'aria-checked', String(Boolean(v))); },
    setLabel(v) { setAttr(btn, 'aria-label', v); },
  };
}

export function createToaster(host) {
  const el = h('div', { class: 'toast', role: 'status', 'aria-live': 'polite' });
  host.append(el);
  let timer = 0;
  return {
    show(message, ms = 2600) {
      el.textContent = message;
      el.classList.add('on');
      clearTimeout(timer);
      timer = setTimeout(() => el.classList.remove('on'), ms);
    },
  };
}
