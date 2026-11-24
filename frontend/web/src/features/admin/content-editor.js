const uuid = /^[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}$/i;
const nullable = value => value === '' ? null : value;
const positive = value => { if (!/^\d+$/.test(String(value)) || !Number.isSafeInteger(Number(value)) || Number(value) < 1 || Number(value) > 2147483647) throw new Error('ID tham chiếu phải là số nguyên dương.'); return Number(value); };
export function workingSource(kind, detail) {
  if (!detail) return { parent: null, version: null, metadata: true };
  const parent = detail[kind === 'contents' ? 'content' : kind === 'questions' ? 'question' : 'group'];
  if (parent.status === 'ARCHIVED') throw new Error('Nội dung đã archive, không thể sửa.');
  if (detail.versions.some(version => ['IN_REVIEW', 'APPROVED'].includes(version.status))) throw new Error('Phiên bản đang review/approved đã khóa sửa. Cần Admin trả sửa hoặc thu hồi duyệt.');
  const drafts = detail.versions.filter(version => version.status === 'DRAFT');
  if (drafts.length > 1) throw new Error('Có nhiều bản nháp, không tự chọn phiên bản để sửa.');
  const pointer = parent.publishedVersionId;
  const version = drafts[0] || (parent.status === 'PUBLISHED' && detail.versions.find(row => (row.contentVersionId || row.questionVersionId || row.groupVersionId) === pointer && row.status === 'PUBLISHED'));
  if (!version) throw new Error('Không xác định được bản nháp hoặc published pointer hợp lệ.');
  return { parent, version, metadata: parent.status === 'DRAFT' && (kind === 'contents' || detail.versions.length === 1) };
}
export function initialEditor(kind, detail) {
  const source = workingSource(kind, detail); const p = source.parent || {}; const v = source.version || {};
  return { title: v.title || '', summary: v.summary || '', body: v.body || '', categoryCode: p.category?.code || 'GRAMMAR',
    difficulty: v.difficulty ?? p.difficulty ?? '', partId: p.partId == null ? '' : String(p.partId), topicId: p.topicId == null ? '' : String(p.topicId),
    groupVersionId: v.groupVersionId || '', questionText: v.questionText || '', explanation: v.explanation || '',
    groupType: p.groupType || '', instruction: v.instruction || '', passageText: v.passageText || '',
    choices: v.choices?.map(row => ({ label: row.label, content: row.content, isCorrect: row.isCorrect, displayOrder: row.displayOrder })) || ['A','B','C','D'].map((label, i) => ({ label, content: '', isCorrect: false, displayOrder: i + 1 })),
    replaceAssets: false, assets: [{ mediaId: '', transcript: '' }] };
}
function fields(kind, state) {
  if (kind === 'contents') {
    if (!state.title.trim() || state.title.length > 255 || !['GRAMMAR','VOCAB','LISTENING','READING','STRATEGIES','DOCS'].includes(state.categoryCode)) throw new Error('Nhập tiêu đề hợp lệ và chọn loại học liệu.');
    if (state.difficulty && (!state.difficulty.trim() || state.difficulty.length > 20)) throw new Error('Độ khó tối đa 20 ký tự, không chỉ khoảng trắng.');
    return { title: state.title, summary: nullable(state.summary), body: state.body, categoryCode: state.categoryCode, difficulty: nullable(state.difficulty) };
  }
  if (kind === 'groups') {
    if (state.groupType && (!state.groupType.trim() || state.groupType.length > 30)) throw new Error('Loại nhóm tối đa 30 ký tự, không chỉ khoảng trắng.');
    return { groupType: nullable(state.groupType), instruction: nullable(state.instruction), passageText: nullable(state.passageText) };
  }
  if (kind !== 'questions') throw new Error('Loại nội dung không hợp lệ.');
  if (!state.questionText.trim() || !state.choices.every(row => row.content.trim()) || state.choices.filter(row => row.isCorrect).length !== 1) throw new Error('Nhập câu hỏi, mọi lựa chọn và chọn đúng một đáp án.');
  if (!['','EASY','MEDIUM','HARD'].includes(state.difficulty)) throw new Error('Độ khó không hợp lệ.');
  if (state.groupVersionId && !uuid.test(state.groupVersionId)) throw new Error('ID phiên bản nhóm phải là UUID hợp lệ.');
  return { partId: positive(state.partId), topicId: state.topicId ? positive(state.topicId) : null, groupVersionId: state.groupVersionId || null,
    questionText: state.questionText, explanation: nullable(state.explanation), difficulty: nullable(state.difficulty), choices: state.choices };
}
export function editorPayload(kind, state, original, source, partNumber) {
  const result = fields(kind, state);
  if (kind === 'questions') {
    const count = partNumber === 2 ? 3 : 4;
    if (!partNumber || state.choices.length !== count || state.choices.some((row, i) => row.label !== String.fromCharCode(65 + i) || row.displayOrder !== i + 1)) throw new Error('Lựa chọn phải đúng Part và thứ tự A–C/A–D.');
    if ([3,4,6,7].includes(partNumber) !== Boolean(state.groupVersionId)) throw new Error('Part 3/4/6/7 cần phiên bản nhóm; Part 1/2/5 là câu độc lập.');
  }
  if (source.parent) {
    const before = fields(kind, original);
    for (const key of Object.keys(result)) if (JSON.stringify(result[key]) === JSON.stringify(before[key])) delete result[key];
    const metadata = kind === 'contents' ? ['categoryCode','difficulty'] : kind === 'groups' ? ['groupType'] : ['partId','topicId'];
    if (!source.metadata && metadata.some(key => Object.hasOwn(result, key))) throw new Error('Metadata của nội dung đã publish hoặc có lịch sử không được đổi.');
  }
  if (state.replaceAssets) {
    const seen = new Set();
    result.assets = state.assets.filter(row => row.mediaId.trim() || row.transcript).map(row => {
      const id = row.mediaId.trim().toLowerCase();
      if (!uuid.test(id) || seen.has(id)) throw new Error('Media cần UUID hợp lệ và không trùng.'); seen.add(id);
      return kind === 'contents' ? { mediaId: id } : { mediaId: id, transcript: nullable(row.transcript) };
    });
  }
  if (!Object.keys(result).length) throw new Error('Chưa có thay đổi để lưu.');
  return result;
}
