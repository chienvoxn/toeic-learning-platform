export function remainingSeconds(expiresAt, now = Date.now()) {
  return !expiresAt ? 0 : Math.max(0, Math.ceil((expiresAt - now) / 1000));
}
