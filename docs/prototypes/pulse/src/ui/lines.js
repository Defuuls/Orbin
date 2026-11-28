import { h, setText, setAttr } from './dom.js';
import { segmented, switchControl } from './controls.js';
import { SOURCES } from '../data/mock.js';
import { resolveScoped, scopeId } from '../domain/settings.js';
import { formatAge, plural } from '../domain/format.js';
import { autoplayWanted } from '../domain/media.js';
import { isClipped, hasClip, clipThread, clipOrigin } from '../domain/clips.js';
import { formatAgeShort } from '../domain/format.js';

// One factory per stream item kind. Lines are created once per id and then updated in
// place, so focus, expansion and in-flight animations survive every refresh.

const CONFIRM_MS = 4000;
const SCOPE_OPTIONS = [{ value: 'app', label: 'App' }, ...SOURCES.map((s) => ({ value: scopeId('site', s.id), label: s.label }))];

const textButton = (label, onClick, extra = {}) => h('button', { type: 'button', class: 'btn', onClick, ...extra }, label);

// Collapsible region. `inert` while closed keeps hidden controls out of the tab order.
function detailBox(...content) {
  const inner = h('div', { class: 'detail-in' }, h('div', { class: 'detail-pad' }, ...content));
  return {
    el: h('div', { class: 'detail' }, inner),
    set(open) { inner.inert = !open; },
  };
}

function setOpen(li, head, detail, open) {
  setAttr(li, 'data-open', String(open));
  setAttr(head, 'aria-expanded', String(open));
  detail.set(open);
}

function headerLine() {
  return h('li', { class: 'line hd' }, h('h2', { class: 'hd-label' }), h('span', { class: 'hd-count' }));
}

function updateHeaderLine(li, item) {
  setText(li.firstChild, item.label);
  setText(li.lastChild, item.count);
}

function noteLine() {
  return h('li', { class: 'line note', role: 'status' }, h('p', { class: 'panel-title' }), h('p', { class: 'panel-hint' }));
}

function updateNoteLine(li, item) {
  setText(li.firstChild, item.title);
  setText(li.lastChild, item.hint);
}

function thread(ctx) {
  const { actions } = ctx.app;
  const li = h('li', { class: 'line thread' });
  const age = h('span', { class: 'age' });
  const title = h('span', { class: 'title' });
  const sub = h('span', { class: 'sub' });
  const fresh = h('span', { class: 'new' });
  const count = h('span', { class: 'count' });
  const head = h('button', { type: 'button', class: 'head', 'aria-expanded': 'false', onClick: () => ctx.toggle(li.dataset.key) },
    age, h('span', { class: 'mark', 'aria-hidden': 'true' }), h('span', { class: 'body' }, title, sub), h('span', { class: 'tail' }, fresh, count));
  const facts = h('p', { class: 'facts' });
  const open = textButton('Open', () => actions.openThreadView(li._c.m.thread));
  const watch = textButton('Watch', () => actions.toggleWatch(li._c.m.thread), { 'aria-pressed': 'false' });
  const save = textButton('Save', () => actions.toggleSave(li._c.m.thread), { 'aria-pressed': 'false' });
  const clip = textButton('Clip', () => actions.toggleClip(li._c.m.thread), { 'aria-pressed': 'false' });
  const detail = detailBox(facts, h('div', { class: 'acts' }, open, watch, save, clip));
  li.append(head, detail.el);
  li._c = { head, detail, age, title, sub, fresh, count, facts, watch, save, clip };
  return li;
}

function updateThread(li, item, ctx) {
  const m = item.row;
  const c = li._c;
  c.m = m;
  li.classList.toggle('is-unread', m.unread > 0);
  li.classList.toggle('is-read', m.read);
  li.classList.toggle('is-archived', m.archived);
  setText(c.age, m.ageText);
  setText(c.title, m.title);
  setText(c.sub, `${m.sourceLabel} /${m.board}/`);
  setText(c.fresh, m.unread > 0 ? `+${m.unread}` : '');
  setText(c.count, m.repliesText);
  const when = m.archived ? 'archived' : m.thread.lastModified ? `last reply ${formatAge(ctx.now() - m.thread.lastModified)}` : '';
  setText(c.facts, `${m.sourceLabel} · /${m.board}/ · No.${m.thread.thread}\n${m.replies} ${plural(m.replies, 'reply', 'replies')}${when ? ` · ${when}` : ''}`);
  setAttr(c.head, 'aria-label', `${m.title}. ${m.sourceLabel} /${m.board}/. ${m.unread > 0 ? `${m.unread} new. ` : ''}${m.replies} ${plural(m.replies, 'reply', 'replies')}`);
  setText(c.watch, m.watched ? 'Watching' : 'Watch');
  setAttr(c.watch, 'aria-pressed', String(m.watched));
  setText(c.save, m.saved ? 'Saved' : 'Save');
  setAttr(c.save, 'aria-pressed', String(m.saved));
  setClipButton(c.clip, ctx.app.store.get().user, m.thread);
  setOpen(li, c.head, c.detail, ctx.isOpen(item.id));
}

function setClipButton(btn, user, t) {
  const on = isClipped(user, t);
  setText(btn, on ? 'Clipped' : 'Clip');
  setAttr(btn, 'aria-pressed', String(on));
  setAttr(btn, 'aria-label', on ? 'Remove clipped link' : 'Clip link');
}

// ---- clips: the internal clipboard ----

function clipLine(ctx) {
  const { actions } = ctx.app;
  const li = h('li', { class: 'line clip' });
  const age = h('span', { class: 'age' });
  const title = h('span', { class: 'title' });
  const sub = h('span', { class: 'sub' });
  const head = h('button', { type: 'button', class: 'head', 'aria-expanded': 'false', onClick: () => ctx.toggle(li.dataset.key) },
    age, h('span', { class: 'mark', 'aria-hidden': 'true' }), h('span', { class: 'body' }, title, sub), h('span', { class: 'tail' }));
  const url = h('p', { class: 'facts' });
  const copy = textButton('Copy link', async () => {
    const ok = await ctx.app.ui.copy(li._c.clip.url);
    ctx.app.ui.notify(ok ? 'Link copied' : 'Copy not available here');
  });
  const open = textButton('Open thread', () => actions.openThreadView(clipThread(li._c.clip)));
  const remove = textButton('Remove', () => actions.removeClip(li._c.clip.url));
  const detail = detailBox(url, h('div', { class: 'acts' }, open, copy, remove));
  li.append(head, detail.el);
  li._c = { head, detail, age, title, sub, url };
  return li;
}

function updateClip(li, item, ctx) {
  const c = li._c;
  c.clip = item.clip;
  setText(c.age, formatAgeShort(ctx.now() - item.clip.at));
  setText(c.title, item.clip.title);
  setText(c.sub, item.clip.kind === 'link' ? `link · ${clipOrigin(item.clip, ctx.app.labels)}` : `${item.sourceLabel} /${item.clip.board}/`);
  setText(c.url, item.clip.url);
  setAttr(c.head, 'aria-label', `${item.clip.title}. ${item.sourceLabel} /${item.clip.board}/. Clipped link`);
  setOpen(li, c.head, c.detail, ctx.isOpen(item.id));
}

function clipsToolsLine(ctx) {
  const { actions } = ctx.app;
  const label = h('span', { class: 'facts grow' });
  const copy = textButton('Copy list', () => actions.exportClips('text'));
  const md = textButton('Download .md', () => actions.exportClips('markdown'));
  const li = h('li', { class: 'line clip-tools' }, h('div', { class: 'acts' }, label, copy, md));
  li._c = { label };
  return li;
}

function updateClipsTools(li, item) {
  setText(li._c.label, `Export ${item.count} ${item.count === 1 ? 'link' : 'links'}`);
}

function board(ctx) {
  const { actions } = ctx.app;
  const li = h('li', { class: 'line board' });
  const title = h('span', { class: 'title' });
  const sub = h('span', { class: 'sub' });
  const fav = textButton('Fav', () => actions.toggleFavorite(li.dataset.key.slice(2)), { 'aria-pressed': 'false' });
  const follow = textButton('Follow', () => actions.toggleFollow(li.dataset.key.slice(2)), { 'aria-pressed': 'false' });
  li.append(h('div', { class: 'head static' }, h('span', { class: 'age' }), h('span', { class: 'mark', 'aria-hidden': 'true' }), h('span', { class: 'body' }, title, sub), h('span', { class: 'tail acts-inline' }, fav, follow)));
  li._c = { title, sub, fav, follow };
  return li;
}

function updateBoard(li, item) {
  const c = li._c;
  setText(c.title, item.name);
  setText(c.sub, `${item.sourceLabel} /${item.board}/`);
  setAttr(c.fav, 'aria-label', `Favorite ${item.name} on ${item.sourceLabel}`);
  setAttr(c.follow, 'aria-label', `Follow ${item.name} on ${item.sourceLabel}`);
  setAttr(c.fav, 'aria-pressed', String(item.favorite));
  setAttr(c.follow, 'aria-pressed', String(item.followed));
  setText(c.follow, item.followed ? 'Following' : 'Follow');
}

// ---- settings ----

function settingShell(ctx, onHead, detail) {
  const li = h('li', { class: 'line setting' });
  const title = h('span', { class: 'title' });
  const sub = h('span', { class: 'sub plain' });
  const value = h('span', { class: 'count' });
  const head = h('button', { type: 'button', class: 'head', onClick: onHead },
    h('span', { class: 'age' }), h('span', { class: 'mark', 'aria-hidden': 'true' }), h('span', { class: 'body' }, title, sub), h('span', { class: 'tail' }, value));
  li.append(head);
  if (detail) li.append(detail.el);
  li._c = { head, detail, title, sub, value };
  return li;
}

function updateSettingText(li, item) {
  const c = li._c;
  c.item = item.item;
  setText(c.title, item.item.label);
  setText(c.sub, item.item.hint);
}

function settingToggle(ctx) {
  const li = settingShell(ctx, () => ctx.app.actions.setSetting(li._c.item.id, !li._c.on));
  li._c.head.setAttribute('role', 'switch');
  return li;
}

function updateSettingToggle(li, item) {
  const c = li._c;
  updateSettingText(li, item);
  c.on = Boolean(item.value);
  setText(c.value, c.on ? 'ON' : 'OFF');
  setAttr(c.head, 'aria-checked', String(c.on));
  setAttr(c.head, 'aria-label', item.item.label);
  li.classList.toggle('is-on', c.on);
}

function settingChoice(ctx) {
  const holder = h('div');
  const detail = detailBox(holder);
  const li = settingShell(ctx, () => ctx.toggle(li.dataset.key), detail);
  setAttr(li._c.head, 'aria-expanded', 'false');
  li._c.holder = holder;
  return li;
}

function updateSettingChoice(li, item, ctx) {
  const c = li._c;
  updateSettingText(li, item);
  if (!c.seg) {
    c.seg = segmented({ label: item.item.label, options: item.item.options, value: item.value, onChange: (v) => ctx.app.actions.setSetting(item.item.id, v) });
    c.holder.append(c.seg.el);
  }
  c.seg.set(item.value);
  const current = item.item.options.find((o) => o.value === item.value);
  setText(c.value, current ? current.label : '');
  setOpen(li, c.head, c.detail, ctx.isOpen(item.id));
}

function settingScoped(ctx) {
  const holder = h('div');
  const detail = detailBox(holder);
  const li = settingShell(ctx, () => ctx.toggle(li.dataset.key), detail);
  li._c.holder = holder;
  li._c.scope = 'app';
  return li;
}

function updateSettingScoped(li, item, ctx) {
  const c = li._c;
  const { app } = ctx;
  const state = app.store.get();
  updateSettingText(li, item);
  if (!c.scopeSeg) {
    const id = item.item.id;
    c.scopeSeg = segmented({ label: `${item.item.label} scope`, options: SCOPE_OPTIONS, value: 'app', onChange: (v) => { c.scope = v; updateSettingScoped(li, c.last, ctx); } });
    c.sw = switchControl({
      label: item.item.label, checked: false,
      onChange: (v) => (c.scope === 'app' ? app.actions.setSetting(id, v) : app.actions.setScopedValue(c.scope, id, v)),
    });
    c.note = h('span', { class: 'facts grow' });
    c.reset = textButton('Reset', () => app.actions.resetScopedValue(c.scope, id));
    c.holder.append(c.scopeSeg.el, h('div', { class: 'scoped-line' }, c.sw.el, c.note, c.reset));
  }
  c.last = item;
  c.scopeSeg.set(c.scope);
  const source = c.scope === 'app' ? undefined : c.scope.slice('site:'.length);
  const r = resolveScoped(state.overrides, item.item.id, { source }, state.settings[item.item.id]);
  c.sw.set(r.value);
  const overridden = c.scope !== 'app' && r.from === c.scope;
  c.reset.hidden = !overridden;
  setText(c.note, c.scope === 'app' ? 'App default' : overridden ? `Overridden for ${app.labels[source]}` : 'Using app default');
  setText(c.value, state.settings[item.item.id] ? 'ON' : 'OFF');
  setOpen(li, c.head, c.detail, ctx.isOpen(item.id));
}

function settingAction(ctx) {
  const btn = textButton('', () => {
    const c = li._c;
    if (!c.item.confirmLabel || c.confirming) {
      c.confirming = false;
      clearTimeout(c.timer);
      ctx.app.actions[c.item.id]();
      ctx.app.ui.notify('Local activity cleared');
    } else {
      c.confirming = true;
      c.timer = setTimeout(() => { c.confirming = false; updateSettingAction(li, c.last, ctx); }, CONFIRM_MS);
    }
    updateSettingAction(li, c.last, ctx);
  });
  const detail = detailBox(btn);
  const li = settingShell(ctx, () => ctx.toggle(li.dataset.key), detail);
  li._c.btn = btn;
  return li;
}

function updateSettingAction(li, item, ctx) {
  const c = li._c;
  c.last = item;
  updateSettingText(li, item);
  setText(c.btn, c.confirming ? item.item.confirmLabel : item.item.actionLabel);
  c.btn.classList.toggle('danger', Boolean(c.confirming));
  setOpen(li, c.head, c.detail, ctx.isOpen(item.id));
}

const SETTING_BY_TYPE = {
  toggle: [settingToggle, updateSettingToggle],
  choice: [settingChoice, updateSettingChoice],
  scoped: [settingScoped, updateSettingScoped],
  action: [settingAction, updateSettingAction],
};

// ---- opened thread: header and posts ----

function threadHeadLine(ctx) {
  const { actions } = ctx.app;
  const title = h('h2', { class: 'th-title' });
  const facts = h('p', { class: 'facts' });
  const watch = textButton('Watch', () => actions.toggleWatch(li._c.thread), { 'aria-pressed': 'false' });
  const save = textButton('Save', () => actions.toggleSave(li._c.thread), { 'aria-pressed': 'false' });
  const clip = textButton('Clip', () => actions.toggleClip(li._c.thread), { 'aria-pressed': 'false' });
  const li = h('li', { class: 'line thread-head' }, title, facts, h('div', { class: 'acts' }, watch, save, clip));
  li._c = { title, facts, watch, save, clip };
  return li;
}

function updateThreadHead(li, item, ctx) {
  const c = li._c;
  const t = item.thread;
  const user = ctx.app.store.get().user;
  c.thread = t;
  setText(c.title, t.title);
  setText(c.facts, `${ctx.app.labels[t.source] ?? t.source} · /${t.board}/ · No.${t.thread}`);
  setText(c.watch, user.watched[t.key] ? 'Watching' : 'Watch');
  setAttr(c.watch, 'aria-pressed', String(Boolean(user.watched[t.key])));
  setText(c.save, user.saved[t.key] ? 'Saved' : 'Save');
  setAttr(c.save, 'aria-pressed', String(Boolean(user.saved[t.key])));
  setClipButton(c.clip, user, t);
}

// "Clip" sits right after a link typed in a post; the link itself stays plain text.
function linkClipButton(url, post, ctx) {
  const btn = h('button', { type: 'button', class: 'clipbtn inline', onClick: () => ctx.app.actions.toggleLinkClip(url, ctx.app.store.get().thread.snapshot, post) }, 'Clip');
  btn._url = url;
  return btn;
}

function setLinkClipState(btn, on) {
  setText(btn, on ? 'Clipped' : 'Clip');
  setAttr(btn, 'aria-pressed', String(on));
  setAttr(btn, 'aria-label', on ? `Remove clipped link ${btn._url}` : `Clip link ${btn._url}`);
}

const refButton = (no, jump, extra = '') => h('button', { type: 'button', class: `ref ${extra}`.trim(), onClick: () => jump(no) }, `>>${no}`);

// One media item under a post: a single text line that opens the file in place.
function mediaBlock(m, ctx) {
  const holder = h('div', { class: 'media-holder' });
  if (m.w && m.h) holder.style.setProperty('aspect-ratio', `${m.w} / ${m.h}`);
  const full = textButton('Fullscreen', () => ctx.app.actions.openViewer(m.id));
  const detail = detailBox(holder, h('div', { class: 'acts' }, full));
  const head = h('button', { type: 'button', class: 'media-line', 'aria-expanded': 'false', onClick: () => ctx.toggle(m.key) },
    h('span', { class: 'media-tag' }, m.tag), h('span', { class: 'media-text' }, m.text));
  const li = h('div', { class: 'media' }, head, detail.el);
  let el = null;
  return {
    el: li,
    set(open) {
      setAttr(li, 'data-open', String(open));
      setAttr(head, 'aria-expanded', String(open));
      detail.set(open);
      if (open && !el) {
        // The file is only requested the first time it is opened.
        el = m.kind === 'image'
          ? h('img', { src: m.src, alt: m.name, decoding: 'async' })
          : h('video', { src: m.src, controls: true, playsinline: true, preload: 'metadata', 'aria-label': m.name });
        holder.append(el);
      }
      if (el && m.kind === 'video') {
        const state = ctx.app.store.get();
        if (open && autoplayWanted(state.overrides, state.settings, state.thread?.snapshot)) { el.muted = true; el.play?.().catch(() => {}); }
        if (!open) el.pause?.();
      }
    },
  };
}

function postLine(ctx) {
  const head = h('div', { class: 'post-head' });
  const body = h('div', { class: 'post-body' });
  const backs = h('div', { class: 'backs' });
  const medias = h('div', { class: 'medias' });
  const li = h('li', { class: 'line post' }, head, body, medias, backs);
  li._c = { head, body, backs, medias, blocks: [] };
  return li;
}

function updatePost(li, item, ctx) {
  const c = li._c;
  li.classList.toggle('is-op', item.op);
  setText(c.head, `${item.op ? 'OP' : 'No.'}${item.op ? ' ' : ''}${item.no} · ${item.author}${item.ageText ? ` · ${item.ageText}` : ''}`);
  const sig = JSON.stringify([item.lines, item.replies, item.media.map((m) => m.src.length + m.name)]);
  if (c.sig !== sig) { // posts rarely change while open; only rebuild when they do
    c.sig = sig;
    c.clipBtns = [];
    const partNode = (part) => {
      if (part.t === 'ref') return refButton(part.no, ctx.jump, 'inline');
      if (part.t !== 'link') return part.v;
      const btn = linkClipButton(part.v, item.no, ctx);
      c.clipBtns.push(btn);
      return h('span', { class: 'url' }, part.v, btn);
    };
    c.body.replaceChildren(...item.lines.map((line) => h('p', { class: line.quote ? 'pl quote' : 'pl' }, ...line.parts.map(partNode))));
    c.backs.replaceChildren(...item.replies.map((no) => refButton(no, ctx.jump)));
    c.backs.hidden = item.replies.length === 0;
    c.blocks = item.media.map((m) => mediaBlock(m, ctx));
    c.medias.replaceChildren(...c.blocks.map((b) => b.el));
  }
  c.blocks.forEach((b, i) => b.set(ctx.isOpen(item.media[i].key)));
  const user = ctx.app.store.get().user;
  for (const btn of c.clipBtns) setLinkClipState(btn, hasClip(user, btn._url));
}

// ---- dispatch ----

export function createLines(ctx) {
  return {
    create(item) {
      switch (item.kind) {
        case 'header': return headerLine();
        case 'note': return noteLine();
        case 'thread': return thread(ctx);
        case 'board': return board(ctx);
        case 'clip': return clipLine(ctx);
        case 'clipsTools': return clipsToolsLine(ctx);
        case 'threadhead': return threadHeadLine(ctx);
        case 'post': return postLine(ctx);
        default: return SETTING_BY_TYPE[item.item.type][0](ctx);
      }
    },
    update(li, item) {
      switch (item.kind) {
        case 'header': return updateHeaderLine(li, item);
        case 'note': return updateNoteLine(li, item);
        case 'thread': return updateThread(li, item, ctx);
        case 'board': return updateBoard(li, item);
        case 'clip': return updateClip(li, item, ctx);
        case 'clipsTools': return updateClipsTools(li, item);
        case 'threadhead': return updateThreadHead(li, item, ctx);
        case 'post': return updatePost(li, item, ctx);
        default: return SETTING_BY_TYPE[item.item.type][1](li, item, ctx);
      }
    },
  };
}
