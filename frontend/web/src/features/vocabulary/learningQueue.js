// A card finishes this round when the server schedules it at least one day ahead.
export function restoreLearningQueue(cards, completedIds = [], storedQueue = null) {
  const ids = cards.map(card => card.id);
  const valid = new Set(ids);
  const passed = new Set(completedIds.filter(id => valid.has(id)));
  const queue = [];
  for (const id of [...(Array.isArray(storedQueue) ? storedQueue : []), ...ids]) {
    if (valid.has(id) && !passed.has(id) && !queue.includes(id)) queue.push(id);
  }
  return queue;
}

export function readyLearningQueue(queue, deferredUntil = {}, now = Date.now()) {
  return queue.filter(id => !deferredUntil[id] || Date.parse(deferredUntil[id]) <= now);
}

export function restoreLearningSession(cards, stored = {}) {
  const valid = values => [...new Set((Array.isArray(values) ? values : []).filter(id => cards.some(card => card.id === id)))];
  const reviewedIds = valid(stored.reviewedIds);
  const completed = new Set(valid(stored.completedIds ?? stored.easyIds));
  const deferredUntil = {};
  for (const card of cards) {
    const saved = stored.deferredUntil?.[card.id];
    if (typeof saved === 'string' && Number.isFinite(Date.parse(saved))) deferredUntil[card.id] = saved;
    // Migrate old EASY-only sessions using the SRS states of cards actually rated in that round.
    if (!Array.isArray(stored.completedIds) && reviewedIds.includes(card.id) && card.reviewState) {
      if (card.reviewState.intervalDays >= 1) completed.add(card.id);
      else if (Number.isFinite(Date.parse(card.reviewState.nextReviewAt))) deferredUntil[card.id] = card.reviewState.nextReviewAt;
    }
  }
  const completedIds = [...completed];
  return { ...stored, reviewedIds, completedIds, deferredUntil,
    roundIds: restoreLearningQueue(cards, completedIds, stored.roundIds),
    roundNumber: Math.max(1, Number(stored.roundNumber) || 1), attempts: Math.max(0, Number(stored.attempts) || 0) };
}

export function applyLearningRating(cards, completedIds, queue, cardId, rating, schedule, deferredUntil = {}) {
  const valid = new Set(cards.map(card => card.id));
  if (!valid.has(cardId) || ![1, 2, 3, 4].includes(rating)
      || !Number.isInteger(schedule?.intervalDays) || schedule.intervalDays < 0
      || !Number.isFinite(Date.parse(schedule.nextReviewAt))) throw new Error('Invalid review schedule');
  const passed = new Set(completedIds.filter(id => valid.has(id)));
  const waiting = { ...deferredUntil };
  if (schedule.intervalDays >= 1) { passed.add(cardId); delete waiting[cardId]; }
  else { passed.delete(cardId); waiting[cardId] = schedule.nextReviewAt; }
  const remaining = restoreLearningQueue(cards, [...passed], queue).filter(id => id !== cardId);
  if (!passed.has(cardId)) remaining.push(cardId);
  return { completedIds: [...passed], deferredUntil: waiting,
    queue: restoreLearningQueue(cards, [...passed], remaining),
    completed: cards.length > 0 && passed.size === cards.length };
}
