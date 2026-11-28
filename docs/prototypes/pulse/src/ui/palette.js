import { h, reconcile, setText, setAttr } from './dom.js';
import { buildCommands, rankCommands, parseInput, executeCommand } from '../domain/commands.js';
import { LENSES } from '../domain/stream.js';

const LIMIT = 12;
const idOf = (cmd) => `opt-${cmd.id.replace(/[^a-z0-9]/gi, '-')}`;

// The controls live in one centered pop-up, opened by a long press (or right-click, or "/").
// Nothing is pinned to the top or bottom of the screen. Inside: actions, the six views,
// and the command input, which is a combobox so it works from a keyboard or a screen reader.
export function createPalette(app, { onFilter, inertTarget }) {
  const { store, actions, ui } = app;
  let open = false;
  let listOpen = false;
  let active = 0;
  let options = [];
  let restore = null;

  const close = h('button', { type: 'button', class: 'btn quiet', onClick: () => hide() }, 'Close');
  const refresh = h('button', { type: 'button', class: 'btn', onClick: () => { actions.refreshAll(); hide(); } }, 'Refresh');
  const back = h('button', { type: 'button', class: 'btn', onClick: () => { actions.closeThread(); hide(); } }, 'Back');
  const lensButtons = LENSES.map((l) => h('button', { type: 'button', class: 'btn pal-lens', 'data-lens': l.id, onClick: () => { actions.setRoute(l.id); hide(); } }, l.label));

  const list = h('ul', { class: 'suggest', role: 'listbox', id: 'suggest', 'aria-label': 'Commands', hidden: true });
  const input = h('input', {
    type: 'text', class: 'cmd', role: 'combobox', 'aria-expanded': 'false', 'aria-controls': 'suggest', 'aria-autocomplete': 'list',
    autocomplete: 'off', autocapitalize: 'off', spellcheck: 'false', enterkeyhint: 'go', 'aria-label': 'Filter or command', placeholder: 'filter · / for commands',
    onInput: () => update(),
    onKeydown: onKey,
  });
  const clear = h('button', { type: 'button', class: 'clear', 'aria-label': 'Clear', hidden: true, onClick: () => { reset(); input.focus(); } }, '×');

  const card = h('div', { class: 'palette', role: 'dialog', 'aria-modal': 'true', 'aria-label': 'Controls' },
    h('div', { class: 'pal-head' }, h('h2', { class: 'pal-title' }, 'Controls'), close),
    h('div', { class: 'pal-actions' }, refresh, back),
    h('div', { class: 'pal-lenses', role: 'group', 'aria-label': 'Views' }, ...lensButtons),
    h('div', { class: 'pal-cmd' }, input, clear),
    list);
  const scrim = h('div', { class: 'scrim', onClick: () => hide() });
  const el = h('div', { class: 'palette-layer', hidden: true }, scrim, card);

  // ---- suggestions ----
  function paintOptions() {
    reconcile(list, options, {
      key: (c) => c.id,
      create: (c) => h('li', { class: 'opt', role: 'option', id: idOf(c), onClick: () => run(c) }, h('span', { class: 'opt-label' }), h('span', { class: 'opt-hint' })),
      update(li, c) {
        setText(li.firstChild, c.label);
        setText(li.lastChild, c.hint ?? '');
      },
    });
    [...list.children].forEach((li, i) => setAttr(li, 'aria-selected', String(i === active)));
    setAttr(input, 'aria-activedescendant', listOpen && options[active] ? idOf(options[active]) : null);
  }
  function showList() {
    const { query } = parseInput(input.value);
    options = rankCommands(buildCommands(store.get()), query, LIMIT);
    active = 0;
    listOpen = options.length > 0;
    list.hidden = !listOpen;
    setAttr(input, 'aria-expanded', String(listOpen));
    paintOptions();
  }
  function hideList() {
    listOpen = false;
    list.hidden = true;
    setAttr(input, 'aria-expanded', 'false');
    setAttr(input, 'aria-activedescendant', null);
  }
  function update() {
    const { mode, query } = parseInput(input.value);
    clear.hidden = input.value === '';
    if (mode === 'command') { onFilter(''); showList(); return; }
    hideList();
    onFilter(query);
  }
  function reset() {
    input.value = '';
    clear.hidden = true;
    hideList();
    onFilter('');
  }

  function run(cmd) {
    if (executeCommand(cmd, actions)) ui.notify(cmd.label);
    reset();
    hide();
  }

  function onKey(e) {
    if (e.key === 'ArrowDown' || e.key === 'ArrowUp') {
      if (!listOpen) showList();
      if (!options.length) return;
      e.preventDefault();
      active = (active + (e.key === 'ArrowDown' ? 1 : -1) + options.length) % options.length;
      paintOptions();
      list.children[active]?.scrollIntoView({ block: 'nearest' });
    } else if (e.key === 'Enter') {
      if (listOpen && options[active]) { e.preventDefault(); run(options[active]); }
      else if (input.value) hide(); // a filter is applied; show the result
    }
  }

  // ---- open / close ----
  function show({ command = false } = {}) {
    if (open) return;
    open = true;
    restore = document.activeElement;
    el.hidden = false;
    inertTarget.inert = true;
    if (command) { input.value = '/'; clear.hidden = false; update(); input.focus(); }
    else refresh.focus({ preventScroll: true });
    render(store.get());
  }
  function hide() {
    if (!open) return;
    open = false;
    hideList();
    el.hidden = true;
    inertTarget.inert = false;
    restore?.focus?.({ preventScroll: true });
    restore = null;
  }

  document.addEventListener('keydown', (e) => {
    if (!open) return;
    if (e.key === 'Escape') {
      e.preventDefault();
      e.stopPropagation();
      if (listOpen || parseInput(input.value).mode === 'command') reset(); // first Escape drops the command
      else hide(); // a plain filter stays applied
    } else if (e.key === 'Tab') {
      const items = [...card.querySelectorAll('button:not([disabled]):not([hidden]), input')].filter((n) => n.offsetParent !== null);
      const first = items[0];
      const last = items.at(-1);
      if (e.shiftKey && document.activeElement === first) { e.preventDefault(); last.focus(); }
      else if (!e.shiftKey && document.activeElement === last) { e.preventDefault(); first.focus(); }
    }
  }, true);

  function render(state) {
    LENSES.forEach((l, i) => {
      const current = !state.thread && state.route === l.id;
      setAttr(lensButtons[i], 'aria-current', current ? 'true' : null);
      lensButtons[i].classList.toggle('is-current', current);
    });
    back.hidden = !state.thread;
    setAttr(back, 'aria-label', `Back to ${LENSES.find((l) => l.id === state.route)?.label ?? 'stream'}`);
    setAttr(input, 'placeholder', state.thread ? 'filter posts · / for commands' : 'filter · / for commands');
    if (open && listOpen) showList();
  }

  return { el, input, render, reset, open: show, close: hide, isOpen: () => open };
}
