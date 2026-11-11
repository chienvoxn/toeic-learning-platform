import { restoreLearningSession } from './learningQueue.js';
const prefix = 'toeic:vocabulary:review-session:v1:';
export const reviewSessionKey = deckId => `${prefix}${deckId}`;
export function readReviewSession(deckId, cards = []) {
  try { return restoreLearningSession(cards, JSON.parse(localStorage.getItem(reviewSessionKey(deckId)) || '{}')); }
  catch { return restoreLearningSession(cards); }
}
export function saveReviewSession(deckId, session) {
  try { localStorage.setItem(reviewSessionKey(deckId), JSON.stringify(session)); return true; } catch { return false; }
}
export function clearReviewSession(deckId) {
  try { localStorage.removeItem(reviewSessionKey(deckId)); } catch { /* unavailable */ }
}
export function reviewProgress(deckId, cards, remote = null, authoritative = false) {
  const session = authoritative ? restoreLearningSession(cards, remote ?? {}) : readReviewSession(deckId, cards);
  const total = cards.length;
  const count = session.completedIds.length;
  return { total, reviewed: count, evaluated: session.reviewedIds.length, remaining: total - count,
    percent: total ? Math.round(count * 100 / total) : 0, completed: total > 0 && count === total };
}
export function reviewAction(progress) {
  if (!progress || (!progress.reviewed && !progress.evaluated)) return 'BẮT ĐẦU HỌC';
  return progress.completed ? 'HỌC LƯỢT MỚI' : 'TIẾP TỤC HỌC';
}
