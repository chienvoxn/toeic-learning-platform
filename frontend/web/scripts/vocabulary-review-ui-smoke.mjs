import { chromium } from '@playwright/test';
import assert from 'node:assert/strict';

const browser = await chromium.launch({
  executablePath: process.env.CHROMIUM_PATH || 'C:/Program Files/Google/Chrome/Application/chrome.exe',
  headless: true,
});

let context;
const errors = [];
try {
  context = await browser.newContext();
  await context.addInitScript(() => localStorage.setItem('toeic.remote.session', JSON.stringify({
    accessToken: 'fixture',
    tokenType: 'Bearer',
    expiresAt: Date.now() + 600000,
  })));

  let snapshot = null;
  let currentReview = null;
  let appliedRatings = 0;
  let dropRatingResponse = false;
  const submittedRatings = [];
  const deck = { deckId: 'deck', name: 'Test' };
  await context.route('**/api/v1/**', async (route) => {
    const request = route.request();
    const url = new URL(request.url());
    const { pathname } = url;
    const revision = snapshot?.revision ?? 0;
    const respond = (json) => route.fulfill({ json });
    const conflict = () => route.fulfill({ status: 409, json: {} });

    if (pathname.endsWith('/users/me')) return respond({ userId: 'u', fullName: 'Learner', roles: ['LEARNER'] });
    if (pathname.endsWith('/learning-session/reviews')) {
      const rating = request.postDataJSON();
      submittedRatings.push(rating);
      if (rating.expectedRevision !== revision) return conflict();
      appliedRatings++;
      const again = rating.rating === 1;
      currentReview = {
        intervalDays: again ? 0 : 1,
        nextReviewAt: new Date(Date.now() + (again ? 600000 : 86400000)).toISOString(),
      };
      snapshot = { revision: revision + 1, data: {
        reviewedIds: ['word'],
        completedIds: again ? [] : ['word'],
        deferredUntil: again ? { word: currentReview.nextReviewAt } : {},
        roundIds: again ? ['word'] : [],
        attempts: 1,
      } };
      if (dropRatingResponse) {
        dropRatingResponse = false;
        return route.abort('failed');
      }
      return respond({ session: snapshot, review: currentReview });
    }
    if (pathname.endsWith('/learning-session')) {
      const method = request.method();
      if (method === 'GET') return respond(snapshot);
      if (url.searchParams.get('expectedRevision') !== String(revision)) return conflict();
      snapshot = { revision: revision + 1, data: method === 'PUT' ? request.postDataJSON() : {} };
      return respond(snapshot);
    }
    if (pathname.endsWith('/flashcards')) return respond([{ vocabularyId: 'word', word: 'work', meaning: 'lam viec' }]);
    if (pathname.endsWith('/review-state')) {
      return currentReview ? respond(currentReview) : route.fulfill({ status: 404, json: {} });
    }
    return respond(pathname.endsWith('/deck') ? deck : [deck]);
  });

  const base = process.env.WEB_AUTH_BASE_URL || 'http://127.0.0.1:5174';
  const path = `${base}/vocabulary/deck/review`;
  const first = await context.newPage();
  const second = await context.newPage();
  for (const page of [first, second]) page.on('pageerror', error => errors.push(error.message));
  await Promise.all([first.goto(path), second.goto(path)]);

  const good = (page) => page.getByRole('button', { name: /TỐT/ });
  const complete = (page) => page.getByRole('heading', { name: 'Bạn đã hoàn thành lượt học!' });
  const waiting = (page) => page.getByRole('heading', { name: 'Đang chờ đến lịch học lại' });
  await good(second).waitFor({ timeout: 10000 });
  await good(first).click();
  await complete(first).waitFor();
  await good(second).click();
  await complete(second).waitFor();
  assert.equal(appliedRatings, 1);

  await first.reload();
  await complete(first).waitFor();
  await first.getByRole('button', { name: 'Học lượt mới' }).click();
  dropRatingResponse = true;
  await first.getByRole('button', { name: /HỌC LẠI/ }).click();
  const retry = first.getByRole('button', { name: 'Thử gửi lại đánh giá' });
  await retry.waitFor();
  assert(await good(first).isDisabled());
  await retry.click();
  await waiting(first).waitFor();
  assert.deepEqual(submittedRatings.at(-1), submittedRatings.at(-2));
  assert.equal(appliedRatings, 2);
  await first.reload();
  await waiting(first).waitFor();
  assert.deepEqual(errors, []);
  console.log('PASS two-tab protection, reload, lost-response retry, and Again deferral.');
} catch (error) {
  console.error(errors);
  for (const page of context?.pages() ?? []) {
    console.error(page.url(), await page.getByRole('alert').allTextContents());
  }
  throw error;
} finally {
  await browser.close();
}
