import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';

// Load the Vite modules in Node without changing their browser import paths.
const mapper = await readFile(new URL('../src/features/auth/api/auth.mapper.js', import.meta.url), 'utf8');
const mapperUrl = `data:text/javascript;base64,${Buffer.from(mapper).toString('base64')}`;
const source = await readFile(new URL('../src/features/auth/repositories/auth.fake.js', import.meta.url), 'utf8');
const moduleUrl = `data:text/javascript;base64,${Buffer.from(source.replace("'../api/auth.mapper'", JSON.stringify(mapperUrl))).toString('base64')}`;
const { FakeAuthRepository } = await import(moduleUrl);

test('mock sessions require login and remain logged out after reload', async () => {
  const storage = new Map();
  globalThis.localStorage = {
    getItem: key => storage.get(key) ?? null,
    setItem: (key, value) => storage.set(key, String(value)),
    removeItem: key => storage.delete(key),
  };
  try {
    const repo = new FakeAuthRepository();
    assert.equal(await repo.getCurrentUser(), null, 'new visitor is signed out');
    assert.equal(storage.size, 0, 'visiting does not create a demo session');
    storage.set('auth_user', JSON.stringify({ id: 'old-demo-user' }));
    assert.equal(await repo.getCurrentUser(), null, 'legacy demo user without token is signed out');
    const { user } = await repo.login({ email: 'learner@example.com', password: 'test-only' });
    const restored = await new FakeAuthRepository().getCurrentUser();
    assert.equal(restored.id, user.id, 'login survives reload');
    assert.equal(restored.email, user.email);
    assert.deepEqual(restored.roles, user.roles);
    await repo.logout();
    assert.equal(await new FakeAuthRepository().getCurrentUser(), null, 'logout survives reload');
    storage.set('auth_token', 'test-session');
    storage.set('auth_user', '{invalid');
    assert.equal(await repo.getCurrentUser(), null, 'invalid cached user is signed out');
  } finally {
    delete globalThis.localStorage;
  }
});
