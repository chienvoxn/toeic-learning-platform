// UI hints only. The backend exam guard remains authoritative on every read/ask/replay.
export const EXAM_KEY = 'toeic.active.exam';
export const EXAM_CHANGED = 'toeic:exam-state-changed';
const empty = () => ({ version: 1, attempts: [], restrictions: [] });
function read(storage) {
  try {
    const state = JSON.parse(storage.getItem(EXAM_KEY));
    if (state?.version !== 1 || !Array.isArray(state.attempts) || !Array.isArray(state.restrictions)) return empty();
    return { ...state, attempts: state.attempts.filter(a => a && typeof a === 'object'), restrictions: state.restrictions.filter(r => r && typeof r === 'object') };
  } catch { return empty(); } // Old unscoped strings cannot identify an actor or deadline.
}
function write(storage, state) {
  try { storage.setItem(EXAM_KEY, JSON.stringify(state)); } catch { return; }
  if (typeof window !== 'undefined') window.dispatchEvent(new Event(EXAM_CHANGED));
}
export function examTutorState(userId, storage = localStorage, now = Date.now()) {
  const state = read(storage);
  const restriction = state.restrictions.find(r => r.userId === userId);
  const active = state.attempts.some(a => a.userId === userId && ['SECTION_SNAPSHOT', 'TEST_VERSION'].includes(a.sourceType) && Number.isFinite(a.expiresAt) && a.expiresAt > now);
  return { active: !!userId && (active || !!restriction), conversationId: restriction?.conversationId };
}
export function rememberExam(attempt, userId, storage = localStorage, now = Date.now()) {
  if (!userId || !attempt?.id) return;
  const state = read(storage);
  state.attempts = state.attempts.filter(a => a.expiresAt > now && !(a.userId === userId && a.id === attempt.id));
  if (attempt.remote && ['SECTION_SNAPSHOT', 'TEST_VERSION'].includes(attempt.sourceType) && attempt.status === 'IN_PROGRESS' && !attempt.expired && attempt.expiresAt > now)
    state.attempts.push({ id: attempt.id, userId, sourceType: attempt.sourceType, expiresAt: attempt.expiresAt });
  write(storage, state); // Do not clear on route unmount: leaving an exam does not end it.
}
export function forgetExam(id, userId, storage = localStorage) {
  const state = read(storage);state.attempts = state.attempts.filter(a => !(a.userId === userId && a.id === id));write(storage, state);
}
export function restrictExam(userId, conversationId, storage = localStorage) {
  if (!userId) return;
  const state = read(storage);state.restrictions = state.restrictions.filter(r => r.userId !== userId);
  state.restrictions.push({ userId, conversationId });write(storage, state);
}
export function confirmAiAllowed(userId, storage = localStorage) {
  // Call only after a successful server-guarded messages read, never on transport error or navigation.
  const state = read(storage);state.attempts = state.attempts.filter(a => a.userId !== userId);
  state.restrictions = state.restrictions.filter(r => r.userId !== userId);write(storage, state);
}
