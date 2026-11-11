// A study session finishes only when every card has been rated EASY (4).
// This queue is independent from the backend's long-term SRS schedule.
export function restoreLearningQueue(cards, easyIds = [], storedQueue = null) {
  const ids = cards.map((card) => card.id);
  const valid = new Set(ids);
  const passed = new Set(easyIds.filter((id) => valid.has(id)));
  const seen = new Set();
  const queue = [];
  for (const id of Array.isArray(storedQueue) ? storedQueue : ids) {
    if (valid.has(id) && !passed.has(id) && !seen.has(id)) {
      queue.push(id);
      seen.add(id);
    }
  }
  // Repair sessions written by older versions that accidentally dropped cards.
  for (const id of ids) {
    if (!passed.has(id) && !seen.has(id)) queue.push(id);
  }
  return queue;
}

export function applyLearningRating(cards, easyIds, queue, cardId, rating) {
  const valid = new Set(cards.map((card) => card.id));
  if (!valid.has(cardId) || ![1, 2, 3, 4].includes(rating)) {
    throw new Error('Invalid flashcard rating');
  }
  const easy = new Set(easyIds.filter((id) => valid.has(id)));
  if (rating === 4) easy.add(cardId);
  else easy.delete(cardId);
  const remaining = restoreLearningQueue(cards, [...easy], queue)
    .filter((id) => id !== cardId);
  // Again, Hard and Good always return to the tail. No card can be skipped.
  if (rating !== 4) remaining.push(cardId);
  const nextQueue = restoreLearningQueue(cards, [...easy], remaining);
  return {
    easyIds: cards.map((card) => card.id).filter((id) => easy.has(id)),
    queue: nextQueue,
    completed: cards.length > 0 && easy.size === cards.length,
  };
}
