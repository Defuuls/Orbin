// One result shape for every data call. A failure is never an empty list.

export function ok(value) {
  return { ok: true, value };
}

export function err(reason, message) {
  return { ok: false, error: { reason, message: message ?? REASON_TEXT[reason] ?? 'Something went wrong.' } };
}

export const REASON_TEXT = {
  offline: "You're offline.",
  server: "The source didn't respond.",
  timeout: 'The request took too long.',
  notfound: 'That thread is no longer available.',
};
