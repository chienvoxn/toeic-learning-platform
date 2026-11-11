import assert from 'node:assert/strict';
import { test } from 'node:test';
import { navigationReducer } from '../src/navigation/routes.js';

test('detail back returns to the tab that opened it', () => {
  let state = navigationReducer(['home'], { type: 'tab', screen: 'vocab' });
  state = navigationReducer(state, { type: 'navigate', screen: 'deckDetail' });
  state = navigationReducer(state, { type: 'navigate', screen: 'flashcardReview' });
  assert.deepEqual(navigationReducer(state, { type: 'back' }), ['vocab', 'deckDetail']);
});

test('returning to a tab removes a completed review or login from the back stack', () => {
  assert.deepEqual(navigationReducer(['vocab', 'flashcardReview'], { type: 'navigate', screen: 'vocab' }), ['vocab']);
  assert.deepEqual(navigationReducer(['settings', 'login'], { type: 'navigate', screen: 'home' }), ['home']);
});

test('unknown and repeated routes cannot grow the stack', () => {
  const state = ['home', 'login'];
  assert.equal(navigationReducer(state, { type: 'navigate', screen: 'missing' }), state);
  assert.equal(navigationReducer(state, { type: 'navigate', screen: 'login' }), state);
  assert.deepEqual(navigationReducer(['home'], { type: 'back' }), ['home']);
});
