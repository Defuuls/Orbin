// DOM helpers. Content is only ever inserted as text nodes or textContent, never as
// markup, so untrusted feed text cannot become HTML. Inline handler attributes are refused.

export function h(tag, props, ...children) {
  const el = document.createElement(tag);
  for (const [k, v] of Object.entries(props ?? {})) {
    if (v == null || v === false) continue;
    if (k === 'class') el.className = v;
    else if (k === 'style') for (const [sk, sv] of Object.entries(v)) el.style.setProperty(sk, sv);
    else if (k.startsWith('on')) {
      if (typeof v !== 'function') throw new Error(`Handler for ${k} must be a function`);
      el.addEventListener(k.slice(2).toLowerCase(), v);
    } else el.setAttribute(k, v === true ? '' : String(v));
  }
  appendAll(el, children);
  return el;
}

export function appendAll(el, children) {
  for (const c of children.flat(Infinity)) {
    if (c == null || c === false) continue;
    el.append(c instanceof Node ? c : document.createTextNode(String(c)));
  }
  return el;
}

export function setText(el, text) {
  const s = String(text ?? '');
  if (el.textContent !== s) el.textContent = s;
}

export function setAttr(el, name, value) {
  if (value == null || value === false) {
    if (el.hasAttribute(name)) el.removeAttribute(name);
  } else {
    const s = value === true ? '' : String(value);
    if (el.getAttribute(name) !== s) el.setAttribute(name, s);
  }
}

export function setVar(el, name, value) {
  const s = String(value);
  if (el.style.getPropertyValue(name) !== s) el.style.setProperty(name, s);
}

// Keyed reconciliation: reuse, update and reorder existing nodes instead of rebuilding
// them, so scroll position, focus and in-flight CSS transitions survive a re-render.
// `parent` must contain only keyed children owned by this call.
export function reconcile(parent, items, { key, create, update }) {
  const existing = new Map();
  for (const el of parent.children) {
    if (el.dataset.key !== undefined) existing.set(el.dataset.key, el);
  }
  const seen = new Set();
  let ref = parent.firstElementChild;
  for (const item of items) {
    const k = String(key(item));
    if (seen.has(k)) continue; // duplicate keys are ignored, first one wins
    seen.add(k);
    let el = existing.get(k);
    if (el) {
      update(el, item);
    } else {
      el = create(item);
      el.dataset.key = k;
      update(el, item);
    }
    if (el === ref) ref = ref.nextElementSibling;
    else parent.insertBefore(el, ref);
  }
  while (ref) {
    const next = ref.nextElementSibling;
    ref.remove();
    ref = next;
  }
}
