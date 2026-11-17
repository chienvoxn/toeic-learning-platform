import { chromium } from '@playwright/test';
import assert from 'node:assert/strict';
import { mkdir } from 'node:fs/promises';
const browser = await chromium.launch({ executablePath: 'C:/Program Files/Google/Chrome/Application/chrome.exe', headless: true });
try {
  const page = await browser.newPage();
  const errors = [];
  page.on('pageerror', error => errors.push(error.message));
  await page.addInitScript(() => localStorage.setItem('toeic.remote.session', JSON.stringify({ accessToken: 'ui-fixture', tokenType: 'Bearer', expiresAt: Date.now() + 600000 })));
  let unavailable = false;
  await page.route('**/api/**', async route => {
    const url = new URL(route.request().url());
    let data;
    if (url.pathname.endsWith('/users/me')) data = { userId: 'fixture', fullName: 'Learner', status: 'ACTIVE', roles: ['LEARNER'], targetScore: 750 };
    else if (url.pathname.endsWith('/overview')) data = { totalVocabularies: 42, dueForReview: 7, masteredVocabularies: 12, totalReviews: 120 };
    else if (url.pathname.endsWith('/progress')) {
      if (unavailable) return route.fulfill({ status: 503, json: { message: 'Unavailable' } });
      data = { dailyProgress: Array.from({length: 90}, (_, i) => ({ date: new Date(Date.UTC(2026,7,14+i)).toISOString().slice(0,10), reviewCount: i === 89 ? 7 : 0 })) };
    } else if (url.pathname.endsWith('/bookmarks')) data = {content: [], totalPages: 1};
    else data = { content: [{ contentId: 'lesson-1', title: 'Present perfect', category: { name: 'Grammar' }, difficulty: 'B1' }], totalPages: 1 };
    await route.fulfill({ json: data });
  });
  await mkdir('.tmp/dashboard', { recursive: true });
  for (const [name, width] of [['desktop',1440], ['mobile',390]]) {
    await page.setViewportSize({ width, height: 1000 });
    await page.goto(`${process.env.DASHBOARD_URL || 'http://127.0.0.1:5174'}/dashboard`);
    await page.locator('.calendar-grid button').last().waitFor();
    assert.equal(await page.locator('.calendar-grid button').count(), 90);
    assert.ok(await page.locator('.quick-review.orange').innerText().then(text => text.includes('7')));
    await page.locator('.calendar-grid button').first().click();
    await page.evaluate(() => scrollTo(0, 0));
    await page.screenshot({ path: `.tmp/dashboard/${name}.png`, fullPage: true });
    assert.ok(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth));
  }
  unavailable = true;
  await page.reload();
  await page.getByRole('button', { name: 'Thử lại' }).waitFor();
  assert.ok((await page.locator('.quick-review.orange').innerText()).includes('7'));
  await page.goto(`${process.env.DASHBOARD_URL || 'http://127.0.0.1:5174'}/catalog`);
  await page.getByRole('link', {name: /Đọc bài học|Bắt đầu học/}).first().waitFor();
  await page.screenshot({path: '.tmp/dashboard/catalog.png', fullPage: true});
  assert.equal(await page.locator('article').count(), 1);
  assert.deepEqual(errors, []);
  console.log('PASS: desktop/mobile dashboard, 90 days, day selection, partial API failure, no page errors.');
} finally { await browser.close(); }
