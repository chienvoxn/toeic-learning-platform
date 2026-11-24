import { test, expect } from '@playwright/test';

const sessionKey = 'toeic.remote.session';
const deckId = '11111111-1111-4111-8111-111111111111';
const vocabularyId = '22222222-2222-4222-8222-222222222222';

test('SRS lưu lịch ôn sau tải lại và Dashboard phản ánh lượt ôn', async ({ page }) => {
  let totalReviews = 0;
  let learningSession = { data: {}, revision: 0 };
  await page.addInitScript(({ key }) => {
    localStorage.setItem(key, JSON.stringify({ accessToken: 'browser-fixture-token', tokenType: 'Bearer', expiresAt: Date.now() + 60_000 }));
  }, { key: sessionKey });
  await page.route('**/api/v1/**', async route => {
    const request = route.request();
    const path = new URL(request.url()).pathname;
    const json = body => route.fulfill({ contentType: 'application/json', body: JSON.stringify(body) });

    if (path.endsWith('/users/me')) {
      return json({ userId: 'learner-fixture', email: 'learner@example.test', fullName: 'Learner Fixture', roles: ['LEARNER'], status: 'ACTIVE' });
    }
    if (path.endsWith(`/vocabulary-decks/${deckId}`) && request.method() === 'GET') {
      return json({ deckId, name: 'SRS Fixture Deck', description: 'Deck kiểm thử' });
    }
    if (path.endsWith(`/vocabulary-decks/${deckId}/flashcards`)) {
      return json([{ vocabularyId, word: 'review', meaning: 'ôn tập', partOfSpeech: 'verb', pronunciation: '/rɪˈvjuː/', example: 'Review the lesson.' }]);
    }
    if (path.endsWith(`/vocabularies/${vocabularyId}/review-state`)) {
      return route.fulfill({ status: 404, contentType: 'application/json', body: JSON.stringify({ code: 'REVIEW_STATE_NOT_FOUND' }) });
    }
    if (path.endsWith(`/vocabulary-decks/${deckId}/learning-session`) && request.method() === 'GET') return json(learningSession);
    if (path.endsWith(`/vocabulary-decks/${deckId}/learning-session/reviews`) && request.method() === 'POST') {
      const body = request.postDataJSON();
      expect(body).toEqual({ vocabularyId, rating: 3, expectedRevision: 0 });
      totalReviews = 1;
      learningSession = {
        data: { reviewedIds: [vocabularyId], completedIds: [vocabularyId], deferredUntil: {}, roundIds: [], roundNumber: 1, attempts: 1, currentId: null },
        revision: 1,
      };
      return json({ session: learningSession, review: { vocabularyId, intervalDays: 1, nextReviewAt: '2030-01-01T00:00:00Z' } });
    }
    if (path.endsWith('/learning-profile/overview')) {
      return json({ totalVocabularies: 1, reviewedVocabularies: totalReviews, unreviewedVocabularies: 1 - totalReviews, dueForReview: 0, masteredVocabularies: 0, totalReviews });
    }
    if (path.endsWith('/learning-profile/progress')) {
      return json({ days: 90, dailyProgress: [{ date: '2026-11-24', reviewCount: totalReviews }] });
    }
    if (path.endsWith('/contents')) return json({ content: [], totalPages: 1 });
    return route.fulfill({ status: 500, contentType: 'application/json', body: JSON.stringify({ code: 'UNEXPECTED_REQUEST', message: path }) });
  });

  await page.goto(`/vocabulary/${deckId}/review`);
  await expect(page.getByRole('heading', { name: 'SRS Fixture Deck', exact: true })).toBeVisible();
  await page.getByRole('button', { name: 'Lật thẻ xem nghĩa', exact: true }).click();
  await page.getByRole('button', { name: /^TỐT/ }).click();
  await expect(page.getByRole('heading', { name: 'Bạn đã hoàn thành lượt học!', exact: true })).toBeVisible();
  await page.reload();
  await expect(page.getByRole('heading', { name: 'Bạn đã hoàn thành lượt học!', exact: true })).toBeVisible();

  await page.goto('/dashboard');
  const reviewsMetric = page.locator('.dashboard-metric').filter({ hasText: 'Lượt ôn tập' });
  await expect(reviewsMetric.getByText('1', { exact: true })).toBeVisible();
});
