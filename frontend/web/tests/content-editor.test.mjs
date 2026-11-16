import { test } from 'node:test';
import assert from 'node:assert/strict';
import { initialEditor, workingSource, editorPayload } from '../src/features/admin/content-editor.js';

const version = '11111111-1111-1111-1111-111111111111';
test('editor never clones another draft while review or approval is pending', () => {
  for (const status of ['IN_REVIEW','APPROVED']) assert.throws(() => workingSource('contents', { content: { status: 'PUBLISHED' }, versions: [{ status }] }), /khóa sửa/);
});
const content = { content: { contentId: 'content', status: 'PUBLISHED', category: { code: 'GRAMMAR' }, difficulty: 'EASY', publishedVersionId: version }, versions: [
  { contentVersionId: version, status: 'PUBLISHED', title: 'Current', body: 'Pinned', summary: 'Summary' },
  { contentVersionId: '22222222-2222-2222-2222-222222222222', status: 'PUBLISHED', title: 'Other', body: 'Not current' },
] };
test('editor uses exact pointer or unique working draft and rejects ambiguous/archived records', () => {
  assert.equal(initialEditor('contents', content).body, 'Pinned');
  const draft = { contentVersionId: 'draft', status: 'DRAFT', title: 'Draft', body: 'Work' };
  assert.equal(initialEditor('contents', { ...content, versions: [...content.versions, draft] }).body, 'Work');
  assert.throws(() => workingSource('contents', { ...content, versions: [draft, { ...draft, contentVersionId: 'second' }] }), /nhiều bản nháp/i);
  assert.throws(() => workingSource('contents', { ...content, content: { ...content.content, status: 'ARCHIVED' } }), /archive/);
});
test('partial edit preserves omitted media and immutable metadata, explicit empty assets clears links', () => {
  const original = initialEditor('contents', content); const source = workingSource('contents', content);
  assert.deepEqual(editorPayload('contents', { ...original, title: 'Updated', summary: '' }, original, source), { title: 'Updated', summary: null });
  assert.throws(() => editorPayload('contents', { ...original, difficulty: 'HARD' }, original, source), /Metadata/);
  assert.throws(() => editorPayload('contents', original, original, source), /Chưa có thay đổi/);
  assert.deepEqual(editorPayload('contents', { ...original, replaceAssets: true, assets: [] }, original, source), { assets: [] });
});
test('question creation uses reference IDs, validates Part choice count/group and requires a chosen answer', () => {
  const original = initialEditor('questions', null); const source = workingSource('questions', null);
  const filled = { ...original, partId: '960005', questionText: 'Prompt', choices: original.choices.map((row, index) => ({ ...row, content: 'Choice ' + row.label, isCorrect: index === 1 })) };
  assert.equal(editorPayload('questions', filled, original, source, 5).partId, 960005);
  assert.throws(() => editorPayload('questions', filled, original, source, 2), /thứ tự/);
  assert.throws(() => editorPayload('questions', filled, original, source, 6), /phiên bản nhóm/);
  assert.throws(() => editorPayload('questions', { ...filled, choices: filled.choices.map(row => ({ ...row, isCorrect: false })) }, original, source, 5), /đáp án/);
  assert.equal(editorPayload('questions', { ...filled, groupVersionId: version }, original, source, 6).groupVersionId, version);
});
test('nullable group metadata and explicit media transcripts keep existing request semantics', () => {
  const detail = { group: { groupId: 'g', groupType: null, status: 'PUBLISHED', publishedVersionId: version }, versions: [{ groupVersionId: version, versionNumber: 1, status: 'PUBLISHED', instruction: null, passageText: 'Old' }] };
  const source = workingSource('groups', detail); const original = initialEditor('groups', detail);
  assert.deepEqual(editorPayload('groups', { ...original, passageText: 'New' }, original, source), { passageText: 'New' });
  const state = { ...original, replaceAssets: true, assets: [{ mediaId: version, transcript: 'Explicit transcript' }] };
  assert.deepEqual(editorPayload('groups', state, original, source), { assets: [{ mediaId: version, transcript: 'Explicit transcript' }] });
  assert.throws(() => editorPayload('groups', { ...state, assets: [...state.assets, ...state.assets] }, original, source), /không trùng/);
});
