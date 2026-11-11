import { test } from 'node:test';
import assert from 'node:assert/strict';
import { loadBrowserModule } from './load-browser-module.mjs';

test('Google SDK loads only the official URL, reports errors and can retry', async () => {
  const scripts = [];
  globalThis.window = {};
  globalThis.document = {
    createElement: () => ({ remove() {} }),
    head: { append: element => scripts.push(element) },
  };
  try {
    const { loadGoogleSdk } = await loadBrowserModule('features/auth/api/google-sdk.js');
    const failure = loadGoogleSdk();
    assert.equal(scripts.length, 1);
    assert.equal(scripts[0].src, 'https://accounts.google.com/gsi/client?hl=vi');
    scripts[0].onerror();
    await assert.rejects(failure, /Không tải được Google/);
    const first = loadGoogleSdk(), second = loadGoogleSdk();
    assert.equal(scripts.length, 2, 'concurrent loads share one SDK request');
    const sdk = { initialize() {}, renderButton() {} };
    window.google = { accounts: { id: sdk } };
    scripts[1].onload();
    assert.equal(await first, sdk); assert.equal(await second, sdk);
  } finally { delete globalThis.window; delete globalThis.document; }
});
