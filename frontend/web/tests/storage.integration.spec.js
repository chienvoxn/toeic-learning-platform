import { test, expect } from '@playwright/test';
import { readFileSync, writeFileSync } from 'node:fs';
import { createHash } from 'node:crypto';

// Explicit isolated test DB actor fixtures, real backend/S3. Native browser media
// rendering below is a storage harness, not the learner FE attempt workflow.
test('S3 thật: owner upload/presign, private bytes, browser playback, expiry và cấp URL mới', async ({ page, request }) => {
  const credentials = JSON.parse(readFileSync(process.env.TV2_STORAGE_ACTORS, 'utf8'));
  const base = 'http://127.0.0.1:18080/api/v1';
  async function login(role) {
    const response = await request.post(base + '/auth/login', { data: { email: credentials.actors[role].email, password: credentials.password } });
    expect(response.status()).toBe(200); return (await response.json()).accessToken;
  }
  const token = await login('teacher'), learner = await login('learner');
  const owner = { Authorization: 'Bearer ' + token }, assets = [];
  for (const [name, mime, type] of [['audio/demo-part1-q1.wav', 'audio/wav', 'AUDIO'], ['image/demo-part1-q1.svg', 'image/svg+xml', 'IMAGE']]) {
    const bytes = readFileSync(new URL('../../../docker/minio-seed/' + name, import.meta.url));
    const upload = await request.post(base + '/teacher/media', { headers: owner,
      multipart: { file: { name: name.split('/').pop(), mimeType: mime, buffer: bytes }, resourceType: type, visibility: 'AUTHENTICATED', ...(type === 'AUDIO' ? { durationSeconds: '4' } : {}) } });
    expect(upload.status()).toBe(201);
    const asset = await upload.json(); assets.push(asset);
    const read = await request.get(asset.url); expect(read.status()).toBe(200);
    expect(createHash('sha256').update(await read.body()).digest('hex')).toBe(createHash('sha256').update(bytes).digest('hex'));
    expect((await request.get(asset.url.split('?')[0])).status()).toBe(403);
    expect((await request.get(base + `/teacher/media/${asset.mediaId}/presign`)).status()).toBe(401);
    expect((await request.get(base + `/teacher/media/${asset.mediaId}/presign`, { headers: { Authorization: 'Bearer ' + learner } })).status()).toBe(403);
  }
  const audio = assets.find(a => a.resourceType === 'AUDIO'), image = assets.find(a => a.resourceType === 'IMAGE');
  await page.goto(audio.url);
  const player = page.locator('video,audio').first();
  await player.click();
  await player.evaluate(el => el.play());
  await expect.poll(() => player.evaluate(el => el.currentTime)).toBeGreaterThan(0.1);
  expect(await player.evaluate(el => new URL(el.currentSrc).origin)).toBe('http://127.0.0.1:9000');
  await page.goto(image.url); await expect(page.locator('svg')).toBeVisible();
  expect(Date.parse(audio.expiresAt) - Date.now()).toBeLessThanOrEqual(60_000);
  await expect.poll(async () => (await request.get(audio.url)).status(), { timeout: 70_000, intervals: [1000] }).toBe(403);
  const fresh = await request.get(base + `/teacher/media/${audio.mediaId}/presign`, { headers: owner });
  expect(fresh.status()).toBe(200); const newAudio = await fresh.json();
  expect((await request.get(newAudio.url)).status()).toBe(200);
  await page.goto(newAudio.url); await page.reload();
  await page.locator('video,audio').first().click();
  await page.locator('video,audio').first().evaluate(el => el.play());
  await expect.poll(() => page.locator('video,audio').first().evaluate(el => el.currentTime)).toBeGreaterThan(0.1);
  const proof = { source: process.env.TV2_STORAGE_SOURCE || 'unspecified source; record the tested tree externally', transport: 'real HTTP/backend/S3, native browser media renderer; not learner FE E2E',
    database: process.env.TV2_STORAGE_DATABASE || 'dedicated test database; record its identity externally', actorType: 'account/role fixtures', upload201: true, signedBytesMatch: true, unsigned403: true,
    anonymousPresign401: true, learnerPresign403: true, browserAudioAndImage: true, expiredUrl403: true, freshUrlAndReload: true,
    cleanupObjectKeys: assets.map(a => new URL(a.url).pathname.replace('/toeic-media/', '')) };
  writeFileSync(process.env.TV2_STORAGE_PROOF, JSON.stringify(proof, null, 2));
});
