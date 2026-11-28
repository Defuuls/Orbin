// Browser side of export. Both return whether they worked, so the caller can say so.

export async function copyText(text) {
  try {
    if (navigator.clipboard?.writeText) { await navigator.clipboard.writeText(text); return true; }
  } catch { /* fall through to the selection method */ }
  const ta = document.createElement('textarea');
  ta.value = text;
  ta.setAttribute('readonly', '');
  ta.style.cssText = 'position:fixed;top:0;left:0;opacity:0;';
  document.body.append(ta);
  ta.select();
  let ok = false;
  try { ok = document.execCommand('copy'); } catch { ok = false; }
  ta.remove();
  return ok;
}

// In the hosted viewer, pages may not trigger downloads themselves: the platform's
// `downloads` capability saves the file (and may be declined). Elsewhere, a blob link works.
export async function downloadText(name, text, mime) {
  try {
    const downloads = await window.claude?.use?.('downloads');
    if (downloads) { await downloads.save({ filename: name, data: text }); return true; }
  } catch { return false; }
  try {
    const url = URL.createObjectURL(new Blob([text], { type: `${mime};charset=utf-8` }));
    const a = document.createElement('a');
    a.href = url;
    a.download = name;
    document.body.append(a);
    a.click();
    a.remove();
    setTimeout(() => URL.revokeObjectURL(url), 10000);
    return true;
  } catch { return false; }
}
