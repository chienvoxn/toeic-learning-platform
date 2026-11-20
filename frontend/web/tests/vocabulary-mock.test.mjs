import { test } from 'node:test';
import assert from 'node:assert/strict';
import { FakeVocabularyRepository } from '../src/features/vocabulary/repositories/vocabulary.fake.js';

test('mock library supports every initial request and detail without a synchronous crash', async () => {
  const repo = new FakeVocabularyRepository();
  const [decks, statistics, catalog, due] = await Promise.all([
    repo.getDecks(), repo.getReviewStatistics(), repo.getCatalogDecks(), repo.getDueReviews(),
  ]);
  assert.ok(decks.length);
  assert.ok(Number.isInteger(statistics.totalVocabulary));
  assert.equal(statistics.dueVocabulary, due.length);
  assert.ok(Array.isArray(catalog));
  const detail = await repo.getDeckDetail(decks[0].id);
  assert.equal(detail.id, decks[0].id);
  await assert.rejects(repo.getReviewState('new-card'), error => error.status === 404);
});
