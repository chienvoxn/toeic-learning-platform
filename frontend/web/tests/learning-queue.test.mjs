import { test } from 'node:test';
import assert from 'node:assert/strict';
import { applyLearningRating, readyLearningQueue, restoreLearningSession } from '../src/features/vocabulary/learningQueue.js';
import { reviewProgress } from '../src/features/vocabulary/reviewProgress.js';
const cards = [{id:'a'}, {id:'b'}];
const schedule = days => ({intervalDays: days, nextReviewAt: '2026-11-12T10:00:00Z'});
for (const rating of [2,3,4]) test(`rating ${rating} finishes the card when server interval is at least one day`, () => {
  const result = applyLearningRating(cards, [], ['a','b'], 'a', rating, schedule(1));
  assert.deepEqual(result.completedIds, ['a']);
  assert.deepEqual(result.queue, ['b']);
  const end = applyLearningRating(cards, result.completedIds, result.queue, 'b', rating, schedule(3));
  assert.equal(end.completed, true);
  assert.deepEqual(end.queue, []);
});
test('server schedule controls completion rather than the button label', () => {
  const result = applyLearningRating(cards, [], ['a','b'], 'a', 4, schedule(0));
  assert.deepEqual(result.completedIds, []);
  assert.deepEqual(readyLearningQueue(result.queue, result.deferredUntil, Date.parse('2026-11-12T09:59:59Z')), ['b']);
  assert.deepEqual(readyLearningQueue(result.queue, result.deferredUntil, Date.parse('2026-11-12T10:00:00Z')), ['b','a']);
});
test('again waits across reload and completes only after a later successful review', () => {
  const result = applyLearningRating(cards, [], ['a','b'], 'a', 1, schedule(0));
  const restored = restoreLearningSession(cards, JSON.parse(JSON.stringify({...result, roundIds: result.queue, reviewedIds:['a']})));
  assert.deepEqual(readyLearningQueue(restored.roundIds, restored.deferredUntil, 0), ['b']);
  const next = applyLearningRating(cards, [], restored.roundIds, 'a', 3, schedule(1), restored.deferredUntil);
  assert.equal(next.deferredUntil.a, undefined);
});
test('old EASY-only progress upgrades reviewed Good/Hard using SRS without completing untouched cards', () => {
  const items = [{id:'a',reviewState:schedule(1)}, {id:'b',reviewState:schedule(3)}];
  const old = {easyIds:[],reviewedIds:['a'],roundIds:['a','b']};
  const restored = restoreLearningSession(items,old);
  assert.deepEqual(restored.completedIds,['a']);
  assert.equal(reviewProgress('deck',items,old,true).percent,50);
  assert.deepEqual(restoreLearningSession(items,{completedIds:[],reviewedIds:[]}).completedIds,[]);
});
test('invalid API schedule does not advance the queue', () => {
  assert.throws(() => applyLearningRating(cards,[],['a'],'a',3,{}));
});
