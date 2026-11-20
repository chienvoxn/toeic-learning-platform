import test from 'node:test';
import assert from 'node:assert/strict';
import { loadBrowserModule } from './load-browser-module.mjs';

globalThis.localStorage = { getItem: () => null, removeItem() {} };
const { chatApi } = await loadBrowserModule('features/ai/chat.api.js');
test('chat sends only question and replay identifier to Spring with extended timeout', async () => {
  const original = globalThis.fetch;
  globalThis.fetch = async (url, options) => {
    assert.equal(url, '/api/v1/ai/conversations/owned/messages');
    assert.equal(options.method, 'POST');
    assert.deepEqual(JSON.parse(options.body), { question: 'Explain present perfect', requestId: 'request-1' });
    assert.ok(options.signal);
    return new Response(JSON.stringify({ answer: 'Reference answer', grounded: true, citations: [] }));
  };
  try { assert.equal((await chatApi.ask('owned', 'Explain present perfect', 'request-1')).grounded, true); }
  finally { globalThis.fetch = original; }
});
test('AI unavailable stays an error without synthetic answer', async () => {
  const original = globalThis.fetch;
  globalThis.fetch = async () => new Response(JSON.stringify({ code: 'AI_UNAVAILABLE', message: 'Unavailable' }), { status: 503 });
  try { await assert.rejects(chatApi.ask('owned', 'Question', 'request-2'), error => error.code === 'AI_UNAVAILABLE'); }
  finally { globalThis.fetch = original; }
});
test('chat deletes only the selected encoded conversation', async () => {
  const original = globalThis.fetch;
  globalThis.fetch = async (url, options) => {
    assert.equal(url, '/api/v1/ai/conversations/conversation%2F1');
    assert.equal(options.method, 'DELETE');
    return new Response(null, { status: 204 });
  };
  try { await chatApi.remove('conversation/1'); }
  finally { globalThis.fetch = original; }
});
