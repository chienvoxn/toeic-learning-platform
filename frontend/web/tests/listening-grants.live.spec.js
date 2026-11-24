import { test, expect } from '@playwright/test';
import { readFileSync, writeFileSync } from 'node:fs';
import { execFileSync } from 'node:child_process';
import { createHash } from 'node:crypto';
const file = process.env.LISTENING_GRANT_CREDENTIALS;
const cfg = file ? JSON.parse(readFileSync(file, 'utf8')) : null;
const out = new URL('../../../backend/target/listening-grants-20261125/', import.meta.url);
const seed = cfg ? JSON.parse(readFileSync(new URL('../../../backend/target/tv2-defense-20261125/data-checkpoint.json', import.meta.url), 'utf8')) : null;
const actors = cfg ? JSON.parse(readFileSync(new URL('../../../backend/target/tv2-defense-20261125/db-proof.json', import.meta.url), 'utf8')).actors : null;
const stateFile = new URL('browser.json', out);let state;try { state = JSON.parse(readFileSync(stateFile, 'utf8')); } catch { state = { attempts: {} }; }
test.skip(!cfg, 'Private dedicated demo credentials required.');
test.beforeEach(({ page }) => { page.setDefaultTimeout(15000);page.on('dialog', d => d.accept()); });
const save = () => writeFileSync(stateFile, JSON.stringify(state, null, 2));
async function login(page, role = 'qa') {
  const a = cfg.roles[role];await page.goto('/login');await page.locator('#login-email').fill(a.email);await page.locator('#login-password').waitFor();await page.evaluate(password => { const input = document.querySelector('#login-password');Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value').set.call(input, password);input.dispatchEvent(new Event('input', { bubbles: true })); }, a.password);
  const response = page.waitForResponse(r => r.url().endsWith('/auth/login') && r.request().method() === 'POST');await page.getByRole('button', { name: 'Đăng nhập', exact: true }).click();expect((await response).status()).toBe(200);await page.waitForURL(url => url.pathname !== '/login');
}
async function api(page, method, path, body) {
  return page.evaluate(async ({ method, path, body }) => { const token = JSON.parse(localStorage.getItem('toeic.remote.session') || 'null')?.accessToken;const r = await fetch('/api/v1' + path, { method, headers: { Authorization: 'Bearer ' + token, ...(body === undefined ? {} : { 'Content-Type': 'application/json' }) }, ...(body === undefined ? {} : { body: JSON.stringify(body) }) });const text = await r.text();return { status: r.status, data: text ? JSON.parse(text) : null }; }, { method, path, body });
}
test('Owner UI grant revoke regrant and repeated requests; recipient or manage-any cannot manage another owner asset', async ({ page }) => {
  const media = seed.media['original-office.png'].id;await login(page, 'teacher');await page.goto('/teacher/media');await expect(page.getByRole('heading', { name: 'Quyền tái sử dụng media' })).toBeVisible();await page.locator('#grant-media').selectOption(media);await page.locator('#grant-user').fill(actors.admin);
  const grant = page.waitForResponse(r => r.url().endsWith(`/reuse-grants/${actors.admin}`) && r.request().method() === 'PUT');await page.getByRole('button', { name: 'Cấp quyền', exact: true }).click();expect((await grant).status()).toBe(200);await expect(page.getByRole('row').filter({ hasText: actors.admin })).toContainText('Đang có quyền');
  const repeat = await api(page, 'PUT', `/teacher/media/${media}/reuse-grants/${actors.admin}`);expect(repeat.status).toBe(200);expect(repeat.data.changed).toBe(false);
  const revoke = page.waitForResponse(r => r.url().endsWith(`/reuse-grants/${actors.admin}`) && r.request().method() === 'DELETE');await page.getByRole('row').filter({ hasText: actors.admin }).getByRole('button', { name: 'Thu hồi' }).click();expect((await revoke).status()).toBe(200);await expect(page.getByRole('row').filter({ hasText: actors.admin })).toContainText('Đã thu hồi');expect((await api(page, 'DELETE', `/teacher/media/${media}/reuse-grants/${actors.admin}`)).data.changed).toBe(false);
  await login(page, 'admin');expect((await api(page, 'PUT', `/teacher/media/${media}/reuse-grants/${actors.learner}`)).status).toBe(404);
  const q = seed.questions['p1-1'], path = `/moderation/questions/${q.id}/versions/${q.version}`;const d = await api(page, 'GET', path);
  if (d.data.status === 'APPROVED') { const rejected = await api(page, 'POST', path + '/publish', { expectedRevision: d.data.revision });expect(rejected.status).toBe(409);expect(rejected.data.code).toBe('PUBLICATION_MEDIA_UNAVAILABLE');expect((await api(page, 'GET', path)).data.revision).toBe(d.data.revision); }
  await login(page, 'teacher');await page.goto('/teacher/media');await page.locator('#grant-media').selectOption(media);const regrant = page.waitForResponse(r => r.url().endsWith(`/reuse-grants/${actors.admin}`) && r.request().method() === 'PUT');await page.getByRole('row').filter({ hasText: actors.admin }).getByRole('button', { name: 'Cấp lại' }).click();expect((await regrant).status()).toBe(200);await expect(page.getByRole('row').filter({ hasText: actors.admin })).toContainText('Đang có quyền');await page.reload();await page.locator('#grant-media').selectOption(media);await expect(page.getByRole('row').filter({ hasText: actors.admin })).toContainText('Đang có quyền');
  await login(page);expect((await api(page, 'PUT', `/teacher/media/${media}/reuse-grants/${actors.admin}`)).status).toBe(403);state.ownerUi = { grantRevokeRegrantReload: true, repeatedRequestsNoChange: true, recipientAdminAny404: true, learner403: true, actualUiAndApi: true };save();
});
async function choose(page, index) { const response = page.waitForResponse(r => r.request().method() === 'PUT' && r.url().includes('/answers/'));await page.locator('.question-panel').first().locator('.answer-option').nth(index).click();expect((await response).status()).toBe(200);await expect(page.getByText('Đã lưu', { exact: true })).toBeVisible(); }
for (const kind of ['p1','p2','p3','p4','listening']) test(`Listening flow ${kind}: real image/audio, saved answers, reload, transcript after grade and replay`, async ({ page }) => {
  await login(page);let item = state.attempts[kind];const section = kind === 'listening', count = section ? 8 : Number(kind[1]) < 3 ? 4 : 3;
  if (!item) {
    await page.goto('/practice');
    if (section) { await page.locator('#practice-mode').selectOption('SECTION');await page.locator('#section-type').selectOption('LISTENING');await page.locator('#practice-count').fill('8');await page.locator('#practice-minutes').fill('20'); }
    else { for (let p = 1;p <= 7;p++) await page.getByRole('checkbox', { name: 'Part ' + p, exact: true }).setChecked(p === Number(kind[1]));await page.locator('#practice-count').fill(String(count)); }
    const start = page.waitForResponse(r => r.request().method() === 'POST' && r.url().endsWith('/attempts'));await page.getByRole('button', { name: 'Bắt đầu làm bài thật', exact: true }).click();const r = await start;expect(r.status()).toBe(201);const d = await r.json();item = state.attempts[kind] = { id: d.attemptId, pin: d.questions, expiresAt: d.expiresAt };save();
  }
  await page.goto('/practice/attempts/' + item.id);const current = (await api(page, 'GET', '/attempts/' + item.id)).data;
  if (current.status === 'IN_PROGRESS') {
    expect(/"(?:isCorrect|correctChoiceId|explanation|transcript)"/.test(JSON.stringify(current))).toBe(false);expect((await api(page, 'GET', `/attempts/${item.id}/result`)).status).toBe(409);
    const media = (await api(page, 'GET', `/attempts/${item.id}/media`)).data;expect(JSON.stringify(media).includes('"transcript"')).toBe(false);
    if (kind === 'p3' || kind === 'p4') { expect(new Set(media.questions.map(q => q.groupVersionId)).size).toBe(1);expect(new Set(media.questions.flatMap(q => q.passageAssets.map(a => a.resourceId))).size).toBe(1);await expect(page.locator('audio')).toHaveCount(1); }
    if (kind === 'p1') { await expect(page.locator('.listening-image-box img')).toHaveCount(4);for (const image of await page.locator('.listening-image-box img').all()) await expect.poll(() => image.evaluate(el => el.complete && el.naturalWidth === 640)).toBe(true); }
    const players = page.getByTestId('listening-audio-player');await expect(players.first()).toBeVisible();const n = section ? 1 : await players.count();
    for (let i = 0;i < n;i++) { const player = players.nth(i);await expect.poll(() => player.locator('audio').evaluate(el => Number.isFinite(el.duration) && el.duration > 1)).toBe(true);await player.getByRole('button', { name: 'Phát audio', exact: true }).click();await expect.poll(() => player.locator('audio').evaluate(el => !el.paused && el.currentTime > 0.35)).toBe(true);if (!section) await player.getByRole('button', { name: 'Tạm dừng audio', exact: true }).click(); }
    await choose(page, 0);await choose(page, 1);const clear = page.waitForResponse(r => r.request().method() === 'PUT' && r.url().includes('/answers/'));await page.locator('.question-panel').first().getByRole('button', { name: 'Bỏ chọn', exact: true }).click();expect((await clear).status()).toBe(200);await page.reload();await expect(page.locator('.answer-option[aria-pressed=true]')).toHaveCount(0);await choose(page, 0);await page.reload();await expect(page.locator('.question-panel').first().locator('.answer-option').first()).toHaveAttribute('aria-pressed', 'true');
    // Reload must still decode actual storage audio/image.
    await expect.poll(() => page.locator('audio').first().evaluate(el => Number.isFinite(el.duration) && el.duration > 1)).toBe(true);item.playedRealMedia = true;item.safeBeforeSubmit = true;item.changedClearedAndReloaded = true;save();await page.locator('.submit-exam-button').first().click();
  }
  await page.waitForURL(/\/result$/);await expect(page.locator('.score-summary')).toBeVisible();const result = (await api(page, 'GET', `/attempts/${item.id}/result`)).data;expect(result.totalQuestions).toBe(count);expect((await api(page, 'POST', `/attempts/${item.id}/submit`)).data).toEqual(result);expect(result.parts.map(p => p.partNumber)).toEqual(section ? [1,2,3,4] : [Number(kind[1])]);
  const mediaAfter = (await api(page, 'GET', `/attempts/${item.id}/media`)).data;expect(mediaAfter.questions.some(q => [...q.assets, ...q.passageAssets].some(a => a.transcript?.length > 10))).toBe(true);await page.getByRole('button', { name: 'Mở đáp án', exact: true }).click();await expect(page.locator('.answer-transcript').first()).toBeVisible();await expect(page.locator('.answer-explanation').first()).toBeVisible();
  item.result = result;item.transcriptAfterSubmitOnWeb = true;save();await page.goto('/practice/history');await page.locator(`[data-attempt-id="${item.id}"]`).getByRole('link', { name: 'Xem kết quả', exact: true }).click();await expect(page.locator('.score-summary')).toBeVisible();
  await login(page, 'learner');for (const suffix of ['', '/result','/media']) expect((await api(page, 'GET', `/attempts/${item.id}${suffix}`)).status).toBe(404);item.otherLearner404 = true;save();
});
test('Reading regression: P5 P6 P7 short real Web attempts; older saved results preserved', async ({ page }) => {
  await login(page);state.reading = state.reading || {};
  for (const part of [5,6,7]) {
    let id = state.reading[part]?.id;
    if (!id) { await page.goto('/practice');for (let p = 1;p <= 7;p++) await page.getByRole('checkbox', { name: 'Part ' + p, exact: true }).setChecked(p === part);await page.locator('#practice-count').fill('4');const response = page.waitForResponse(r => r.request().method() === 'POST' && r.url().endsWith('/attempts'));await page.getByRole('button', { name: 'Bắt đầu làm bài thật', exact: true }).click();const r = await response;expect(r.status()).toBe(201);id = (await r.json()).attemptId;state.reading[part] = { id };save(); }
    await page.goto('/practice/attempts/' + id);const d = (await api(page, 'GET', '/attempts/' + id)).data;if (d.status === 'IN_PROGRESS') { await choose(page, 0);await page.reload();await expect(page.locator('.answer-option').first()).toHaveAttribute('aria-pressed', 'true');await page.locator('.submit-exam-button').first().click(); }await page.waitForURL(/\/result$/);await expect(page.locator('.score-summary')).toBeVisible();state.reading[part].result = (await api(page, 'GET', `/attempts/${id}/result`)).data;save();
  }
  const old = JSON.parse(readFileSync(new URL('../../../backend/target/tv2-defense-20261125/browser.json', import.meta.url), 'utf8'));for (const item of Object.values(old.attempts)) { expect((await api(page, 'GET', `/attempts/${item.id}/result`)).data).toEqual(item.result);expect((await api(page, 'GET', `/attempts/${item.id}`)).data.questions).toEqual(item.pin); }
  state.oldReadingPreserved = true;save();
});
test('Revoke after publication preserves published version, old pin, result and learner media; owner regrants on Web', async ({ page }) => {
  const item = state.attempts.p1, asset = seed.media['original-office.png'].id, question = seed.questions['p1-1'];expect(!!item.result).toBe(true);
  await login(page, 'teacher');await page.goto('/teacher/media');await page.locator('#grant-media').selectOption(asset);
  const response = page.waitForResponse(r => r.request().method() === 'DELETE' && r.url().endsWith(`/reuse-grants/${actors.admin}`));await page.getByRole('row').filter({ hasText: actors.admin }).getByRole('button', { name: 'Thu hồi', exact: true }).click();expect((await response).status()).toBe(200);
  const published = (await api(page, 'GET', '/teacher/questions/' + question.id)).data;expect(published.question.publishedVersionId).toBe(question.version);
  await login(page);expect((await api(page, 'GET', `/attempts/${item.id}/result`)).data).toEqual(item.result);expect((await api(page, 'GET', `/attempts/${item.id}`)).data.questions).toEqual(item.pin);expect((await api(page, 'GET', `/attempts/${item.id}/media`)).status).toBe(200);
  await page.goto(`/practice/attempts/${item.id}/result`);await expect(page.locator('.score-summary')).toBeVisible();
  await login(page, 'teacher');await page.goto('/teacher/media');await page.locator('#grant-media').selectOption(asset);const restored = page.waitForResponse(r => r.request().method() === 'PUT' && r.url().endsWith(`/reuse-grants/${actors.admin}`));await page.getByRole('row').filter({ hasText: actors.admin }).getByRole('button', { name: 'Cấp lại', exact: true }).click();expect((await restored).status()).toBe(200);state.revokePreservesOldPublicationAndResults = true;save();
});
test('Real signed URL expires, renewed URL and playback recover; storage outage returns503 instead of synthetic bytes', async ({ page }) => {
  await login(page);const item = state.attempts.p1;let media = (await api(page, 'GET', `/attempts/${item.id}/media`)).data;const asset = media.questions.flatMap(q => q.assets).find(a => a.resourceType === 'AUDIO');const expected = Object.values(seed.media).find(a => a.id === asset.resourceId).sha256;
  const initial = await fetch(asset.url);expect(initial.status).toBe(200);expect(createHash('sha256').update(Buffer.from(await initial.arrayBuffer())).digest('hex')).toBe(expected);
  const delay = Date.parse(asset.expiresAt) - Date.now() + 1800;expect(delay > 0 && delay < 65000).toBe(true);await page.waitForTimeout(delay);expect((await fetch(asset.url, { cache: 'no-store' })).status).toBe(403);
  let renewed = (await api(page, 'GET', `/attempts/${item.id}/media`)).data.questions.flatMap(q => q.assets).find(a => a.resourceId === asset.resourceId);const direct = await fetch(renewed.url);expect(direct.status).toBe(200);expect(createHash('sha256').update(Buffer.from(await direct.arrayBuffer())).digest('hex')).toBe(expected);
  const root = new URL('../../../', import.meta.url);let storageStopped = false;
  try {
    execFileSync('powershell', ['-NoProfile','-ExecutionPolicy','Bypass','-File', new URL('stop-storage-once.ps1', out).pathname.replace(/^\/([A-Za-z]:)/, '$1')], { cwd: root, stdio: 'ignore' });storageStopped = true;
    const failed = await api(page, 'GET', `/attempts/${item.id}/media/${asset.resourceId}/content`);expect(failed.status).toBe(503);expect(failed.data.code).toBe('MEDIA_STORAGE_UNAVAILABLE');
    await page.goto(`/practice/attempts/${item.id}/result`);await expect(page.locator('.score-summary')).toBeVisible();await page.getByRole('button', { name: 'Mở đáp án', exact: true }).click();await expect(page.getByRole('alert').filter({ hasText: 'Không thể phát file audio này.' }).first()).toBeVisible();
  } finally {
    if (storageStopped) execFileSync('powershell', ['-NoProfile','-ExecutionPolicy','Bypass','-File', new URL('../../../backend/target/sprint13-demo-20261124/start-demo-services.ps1', import.meta.url).pathname.replace(/^\/([A-Za-z]:)/, '$1')], { cwd: root, stdio: 'ignore' });
  }
  await expect.poll(async () => { try { const d = (await api(page, 'GET', `/attempts/${item.id}/media`)).data;renewed = d.questions.flatMap(q => q.assets).find(a => a.resourceId === asset.resourceId);return (await fetch(renewed.url)).status; } catch { return 0; } }, { timeout: 30000 }).toBe(200);
  await page.reload();await expect(page.locator('.score-summary')).toBeVisible();await page.getByRole('button', { name: 'Mở đáp án', exact: true }).click();const player = page.getByTestId('listening-audio-player').first();await expect.poll(() => player.locator('audio').evaluate(el => Number.isFinite(el.duration) && el.duration > 1)).toBe(true);await player.getByRole('button', { name: 'Phát audio', exact: true }).click();await expect.poll(() => player.locator('audio').evaluate(el => !el.paused && el.currentTime > 0.35)).toBe(true);
  await login(page, 'learner');expect((await api(page, 'GET', `/attempts/${item.id}/media/${asset.resourceId}/content`)).status).toBe(404);state.signedExpiryAndOutage = { expired403: true, refreshedSameRealBytes: true, storageDown503NoSyntheticMedia: true, webShowsAudioErrorAndKeepsResult: true, sameStorageRestartAndWebPlaybackRecovered: true, otherLearnerContent404: true, noNewAudio: true };save();
});
