// Local learning-round metadata. The backend remains the source of truth for SRS.
const prefix = 'toeic:vocabulary:review-session:v1:';
export const reviewSessionKey = (deckId) => `${prefix}${deckId}`;

export function readReviewSession(deckId, cards = []) {
  const ids = new Set(cards.map((card) => card.id));
  try {
    const raw = JSON.parse(localStorage.getItem(reviewSessionKey(deckId)) || 'null');
    const valid = (values) => [...new Set((Array.isArray(values) ? values : []).filter((id) => ids.has(id)))];
    const reviewedIds = valid(raw?.reviewedIds);
    const easyIds = valid(raw?.easyIds);
    const roundIds = Array.isArray(raw?.roundIds) ? valid(raw.roundIds).filter((id) => !easyIds.includes(id)) : null;
    const currentId = ids.has(raw?.currentId) && !easyIds.includes(raw.currentId) ? raw.currentId : null;
    return { reviewedIds, easyIds, roundIds, currentId, roundNumber: Math.max(1, Number(raw?.roundNumber) || 1), attempts: Math.max(0, Number(raw?.attempts) || 0) };
  } catch { return { reviewedIds: [], easyIds: [], roundIds: null, currentId: null, roundNumber: 1, attempts: 0 }; }
}

export function saveReviewSession(deckId, session) {
  try { localStorage.setItem(reviewSessionKey(deckId), JSON.stringify(session)); return true; } catch { return false; }
}

export function clearReviewSession(deckId) {
  try { localStorage.removeItem(reviewSessionKey(deckId)); } catch { /* unavailable */ }
}

export function reviewProgress(deckId, cards) {
  const { easyIds, reviewedIds } = readReviewSession(deckId, cards);
  const total = cards.length;
  const easy = easyIds.length;
  return { total, reviewed: easy, evaluated: reviewedIds.length, remaining: total - easy, percent: total ? Math.round(easy * 100 / total) : 0, completed: total > 0 && easy === total };
}

export function reviewAction(progress) {
  if (!progress || (!progress.reviewed && !progress.evaluated)) return 'BẮT ĐẦU HỌC';
  return progress.completed ? 'HỌC LƯỢT MỚI' : 'TIẾP TỤC HỌC';
}
