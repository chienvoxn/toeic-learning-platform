import { test, expect } from '@playwright/test';
import { readFileSync } from 'node:fs';
import { execFileSync } from 'node:child_process';
import { randomUUID } from 'node:crypto';

const credentials = JSON.parse(readFileSync(new URL('../../../backend/target/local-demo-credentials.json', import.meta.url), 'utf8').replace(/^\uFEFF/, ''));
const sessionKey = 'toeic.remote.session';
test.beforeEach(async ({ page }) => {
  page.setDefaultTimeout(15_000);
  page.on('dialog', dialog => dialog.accept());
});
test.afterEach(async ({ page }) => {
  await page.evaluate(() => document.querySelectorAll('input[type=password],input[autocomplete=one-time-code]').forEach(input => { input.value = ''; })).catch(() => {});
});
async function secretInput(page, selector, value) {
  await page.locator(selector).waitFor({ state: 'visible' });
  await page.evaluate(({ selector, value }) => {
    const input = document.querySelector(selector);
    Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value').set.call(input, value);
    input.dispatchEvent(new Event('input', { bubbles: true }));
  }, { selector, value });
}
async function login(page, role = 'learner') {
  await page.goto('/login');
  await page.locator('#login-email').fill(credentials[role + 'Email']);
  await secretInput(page, '#login-password', credentials.password);
  const response = page.waitForResponse(item => item.url().endsWith('/auth/login') && item.request().method() === 'POST');
  await page.getByRole('button', { name: 'Đăng nhập', exact: true }).click();
  expect((await response).status()).toBe(200);
  await page.waitForURL(url => url.pathname !== '/login');
}
async function api(page, method, path, body) {
  return page.evaluate(async ({ method, path, body, sessionKey }) => {
    const token = JSON.parse(localStorage.getItem(sessionKey) || 'null')?.accessToken;
    const response = await fetch('/api/v1' + path, { method, headers: { ...(token ? { Authorization: 'Bearer ' + token } : {}), ...(body !== undefined ? { 'Content-Type': 'application/json' } : {}) }, ...(body !== undefined ? { body: JSON.stringify(body) } : {}) });
    const text = await response.text();
    return { status: response.status, data: text ? JSON.parse(text) : null };
  }, { method, path, body, sessionKey });
}
async function start(page, section = false, minutes = 20) {
  await page.goto('/practice');
  if (section) { await page.locator('#practice-mode').selectOption('SECTION'); await page.locator('#practice-minutes').fill(String(minutes)); }
  const request = page.waitForResponse(item => item.url().endsWith('/attempts') && item.request().method() === 'POST');
  await page.getByRole('button', { name: 'Bắt đầu làm bài thật' }).click();
  expect((await request).status()).toBe(201);
  await page.waitForURL(/\/practice\/attempts\/[0-9a-f-]+$/);
  await expect(page.locator('.question-panel').first()).toBeVisible();
  return new URL(page.url()).pathname.split('/').pop();
}
async function saved(page) { await expect(page.getByText('Đã lưu', { exact: true })).toBeVisible(); }
function safeSession(dto) {
  expect(/"(?:isCorrect|correctChoiceId|explanation|transcript|storageUrl|storage_url)"/.test(JSON.stringify(dto))).toBe(false);
}
async function choose(page, questionIndex, choiceIndex) {
  const response = page.waitForResponse(item => item.url().includes('/answers/') && item.request().method() === 'PUT');
  await page.locator('.question-panel').nth(questionIndex).locator('.answer-option').nth(choiceIndex).click();
  expect((await response).status()).toBe(200); await saved(page);
}
async function submit(page) {
  const request = page.waitForRequest(item => item.url().endsWith('/submit') && item.method() === 'POST');
  await page.locator('.submit-exam-button').first().click();
  expect((await request).postData()).toBe(null);
  await page.waitForURL(/\/practice\/attempts\/[0-9a-f-]+\/result$/);
  await expect(page.getByRole('heading', { name: 'Kết quả bài thi', exact: true })).toBeVisible();
}

test('Practice thật: guard, chọn/thay/bỏ, reload, submit/replay/result và khóa sau nộp', async ({ page, request }) => {
  await page.goto('/practice'); await expect(page).toHaveURL(/\/login$/);
  await login(page);
  const id = await start(page);
  const initial = await api(page, 'GET', '/attempts/' + id); expect(initial.status).toBe(200); safeSession(initial.data);
  expect(initial.data.questions.length).toBe(4);
  expect((await api(page, 'GET', '/attempts/' + id + '/result')).status).toBe(409);
  await choose(page, 0, 1); await choose(page, 0, 2); await choose(page, 0, 0);
  const clear = page.waitForResponse(item => item.url().includes('/answers/') && item.request().method() === 'PUT');
  await page.locator('.question-panel').first().getByRole('button', { name: 'Bỏ chọn', exact: true }).click();
  expect((await clear).status()).toBe(200); await saved(page);
  await page.reload(); await expect(page.locator('.question-panel').first().locator('.answer-option[aria-pressed=true]')).toHaveCount(0);
  await choose(page, 0, 0); await choose(page, 1, 1);
  await page.reload(); await expect(page.locator('.question-panel').first().locator('.answer-option').first()).toHaveAttribute('aria-pressed', 'true');
  await expect(page.locator('.answer-explanation')).toHaveCount(0);
  safeSession((await api(page, 'GET', '/attempts/' + id)).data);
  await submit(page);
  const result = await api(page, 'GET', '/attempts/' + id + '/result');
  expect([result.data.correctCount, result.data.incorrectCount, result.data.unansweredCount, result.data.rawScore]).toEqual([1,1,2,1]);
  await expect(page.getByText(/Điểm thô: 1/)).toBeVisible();
  await page.getByRole('button', { name: 'Mở đáp án', exact: true }).click();
  await expect(page.locator('.answer-explanation')).toHaveCount(4);
  const replay = await api(page, 'POST', '/attempts/' + id + '/submit'); expect(replay.data).toEqual(result.data);
  expect((await api(page, 'PUT', '/attempts/' + id + '/answers/' + initial.data.questions[0].questionVersionId, { selectedChoiceId: null })).status).toBe(409);
  expect((await request.get('/api/v1/attempts/' + id)).status()).toBe(401);
  const email = 'demo-owner-' + randomUUID() + '@example.test'; const password = randomUUID() + 'aA9!';
  expect((await request.post('/api/v1/auth/register', { data: { email, password, fullName: 'Demo Ownership Check' } })).status()).toBe(201);
  const otherLogin = await request.post('/api/v1/auth/login', { data: { email, password } });
  expect(otherLogin.status()).toBe(200);
  const headers = { Authorization: 'Bearer ' + (await otherLogin.json()).accessToken };
  expect((await request.get('/api/v1/attempts/' + id, { headers })).status()).toBe(404);
  expect((await request.get('/api/v1/attempts/' + id + '/result', { headers })).status()).toBe(404);
  expect((await request.post('/api/v1/attempts/' + id + '/submit', { headers })).status()).toBe(404);
  expect(await page.evaluate(() => Object.keys(localStorage).some(key => key.startsWith('toeic_guest_attempt')))).toBe(false);
});

test('Reading Section thật: giữ nhóm/thứ tự, server deadline và thống kê đủ Part', async ({ page }) => {
  await login(page); await page.goto('/practice'); await page.locator('#practice-mode').selectOption('SECTION');
  await page.locator('#practice-count').fill('1');
  const insufficient = page.waitForResponse(item => item.url().endsWith('/attempts') && item.request().method() === 'POST');
  await page.getByRole('button', { name: 'Bắt đầu làm bài thật' }).click(); expect((await insufficient).status()).toBe(409);
  await expect(page.getByRole('alert')).toContainText('Không đủ tổ hợp');
  const id = await start(page, true);
  const session = (await api(page, 'GET', '/attempts/' + id)).data; safeSession(session);
  expect([...new Set(session.questions.map(q => q.partNumber))]).toEqual([5,6,7]);
  expect(session.questions.length).toBe(8); expect(Date.parse(session.expiresAt)-Date.parse(session.startedAt)).toBe(20*60_000);
  await expect(page.locator('.exam-passage')).toHaveCount(2);
  await choose(page, 0, 0); await submit(page);
  const result = (await api(page, 'GET', '/attempts/' + id + '/result')).data;
  expect(result.parts.map(part => part.partNumber)).toEqual([5,6,7]);
  expect(result.parts.reduce((sum,part) => sum+part.totalQuestions,0)).toBe(8);
  expect(result.rawScore).toBe(1); await expect(page.locator('.part-result-row')).toHaveCount(3);
  await page.reload(); await expect(page.getByText(/Điểm thô: 1/)).toBeVisible();
});

test('Autosave lỗi mạng không báo đã lưu hoặc nộp giả; retry phục hồi lựa chọn trên server', async ({ page, context }) => {
  await login(page); const id = await start(page);
  await context.setOffline(true);
  await page.locator('.question-panel').first().locator('.answer-option').first().click();
  await expect(page.getByRole('button', { name: /Chưa lưu được/ })).toBeVisible();
  await page.locator('.submit-exam-button').first().click();
  await expect(page.getByRole('alert').filter({ hasText: 'Còn đáp án chưa lưu' })).toBeVisible();
  expect(new URL(page.url()).pathname).toBe('/practice/attempts/' + id);
  await context.setOffline(false); await page.getByRole('button', { name: /Chưa lưu được/ }).click(); await saved(page);
  await page.reload(); await expect(page.locator('.question-panel').first().locator('.answer-option').first()).toHaveAttribute('aria-pressed','true');
});

test('Section hết hạn: không tự nộp, khóa ghi và cho nộp các đáp án đã lưu trước hạn', async ({ page }) => {
  await login(page); const id = await start(page, true, 1);
  await choose(page, 0, 0);
  await expect(page.getByRole('status').filter({ hasText: 'Đã hết thời gian' })).toBeVisible({ timeout: 75_000 });
  const session = (await api(page, 'GET', '/attempts/' + id)).data;
  expect(session.status).toBe('IN_PROGRESS'); expect(session.expired).toBe(true);
  await expect(page.locator('.question-panel').first().locator('.answer-option').first()).toBeDisabled();
  expect((await api(page, 'PUT', '/attempts/' + id + '/answers/' + session.questions[0].questionVersionId, { selectedChoiceId: null })).status).toBe(409);
  await submit(page); expect((await api(page, 'GET', '/attempts/' + id + '/result')).data.correctCount).toBe(1);
});

test('Học liệu và vocabulary qua UI/API thật; bookmark, enroll, SRS và quyền Teacher', async ({ page }) => {
  await login(page); await page.goto('/catalog');
  await expect(page.getByRole('heading', { name: 'Workplace grammar: present simple', exact: true })).toBeVisible();
  const bookmark = page.getByRole('button', { name: 'Lưu bài học', exact: true });
  if (await bookmark.count()) { await bookmark.click(); await expect(page.getByRole('button', { name: 'Bỏ lưu bài học', exact: true })).toBeVisible(); }
  await page.getByRole('link', { name: 'Đọc bài học' }).click(); await expect(page.getByText(/Use the present simple for routines/)).toBeVisible();
  await page.goto('/vocabulary');
  await page.getByRole('button', { name: 'Xem tất cả', exact: true }).click();
  const card = page.locator('article.vocab-deck').filter({ hasText: 'TOEIC Workplace Essentials' })
    .filter({ has: page.getByRole('button', { name: 'BẮT ĐẦU HỌC', exact: true }) });
  await expect(card).toBeVisible(); await card.getByRole('button', { name: 'BẮT ĐẦU HỌC', exact: true }).click();
  await page.waitForURL(/\/vocabulary\/[0-9a-f-]+$/);
  await page.locator('a[href$="/review"]').click();
  await page.waitForURL(/\/vocabulary\/[0-9a-f-]+\/review/);
  await page.getByRole('button', { name: 'Lật thẻ xem nghĩa', exact: true }).click();
  const review = page.waitForResponse(item => /\/vocabularies\/[^/]+\/reviews$/.test(item.url()) && item.request().method() === 'POST');
  await page.getByRole('button', { name: /^TỐT/ }).click(); expect((await review).status()).toBe(200);
  expect((await api(page, 'GET', '/teacher/questions')).status).toBe(403);
  const overview = await api(page, 'GET', '/learning-profile/overview'); expect(overview.status).toBe(200); expect(overview.data.totalReviews).toBeGreaterThan(0);
});

test('Teacher CRUD APIs + RBAC; Admin có API quản lý người dùng riêng', async ({ page }) => {
  await login(page, 'teacher');
  const list = await api(page, 'GET', '/teacher/questions'); expect(list.status).toBe(200);
  const metadata = JSON.parse(execFileSync('C:/Program Files/PostgreSQL/17/bin/psql.exe', ['-X','-w','-h','127.0.0.1','-p','55459','-U','toeic_demo','-d','toeic_learning_demo','-Atc', "SELECT json_build_object('partId',(SELECT part_id FROM toeic_parts WHERE part_number=5),'questionTypeId',(SELECT question_type_id FROM question_types WHERE code='SINGLE_CHOICE'));"], { encoding:'utf8' }));
  const question = await api(page, 'POST', '/teacher/questions', { ...metadata, questionText: 'Teacher API draft', explanation: 'Draft explanation', choices: ['A','B','C','D'].map((label,index) => ({ label,content:'Draft '+label,isCorrect:index===0,displayOrder:index+1 })) });
  expect(question.status).toBe(201); const qid = question.data.question.questionId;
  expect((await api(page,'PATCH','/teacher/questions/'+qid,{ questionText:'Teacher API updated draft' })).status).toBe(200);
  expect((await api(page,'GET','/teacher/questions/'+qid+'/history')).status).toBe(200);
  expect((await api(page,'DELETE','/teacher/questions/'+qid)).status).toBe(204);
  const group = await api(page,'POST','/teacher/question-groups',{groupType:'READING_PASSAGE',instruction:'Read the draft.',passageText:'Draft passage.'});
  expect(group.status).toBe(201); const gid = group.data.group.groupId;
  expect((await api(page,'PATCH','/teacher/question-groups/'+gid,{passageText:'Updated draft passage.'})).status).toBe(200);
  expect((await api(page,'GET','/teacher/question-groups/'+gid+'/history')).status).toBe(200);
  expect((await api(page,'DELETE','/teacher/question-groups/'+gid)).status).toBe(204);
  const content = await api(page,'POST','/teacher/contents',{title:'Teacher API draft lesson',body:'Draft lesson body',categoryCode:'READING'});
  expect(content.status).toBe(201); const cid = content.data.content.contentId;
  expect((await api(page,'PATCH','/teacher/contents/'+cid,{summary:'Updated draft summary'})).status).toBe(200);
  expect((await api(page,'DELETE','/teacher/contents/'+cid)).status).toBe(204);
  const attempt = await api(page,'POST','/attempts',{sourceType:'PRACTICE_SNAPSHOT',practice:{partNumbers:[5],questionCount:1}}); expect(attempt.status).toBe(403);
  await page.locator('.sidebar-link.logout-link').click(); await page.waitForURL(/\/login$/);
  await login(page,'admin'); expect((await api(page,'GET','/teacher/questions')).status).toBe(200);
  expect((await api(page,'GET','/admin/users')).status).toBe(200);
});

test('Guest vẫn là mẫu local được ghi rõ, không gọi API tạo attempt tài khoản', async ({ page }) => {
  let serverCreates = 0;
  page.on('request', request => { if (request.method()==='POST' && request.url().endsWith('/api/v1/attempts')) serverCreates++; });
  await page.goto('/tests'); await expect(page.getByRole('note')).toContainText('không đồng bộ tài khoản');
  await page.getByRole('link', { name:'XEM CHI TIẾT',exact:true }).first().click();
  await page.getByRole('button',{name:'BẮT ĐẦU LÀM BÀI',exact:true}).click();
  await expect(page.locator('.question-panel').first()).toBeVisible(); expect(serverCreates).toBe(0);
});

test('OTP reset qua Mailpit và UI thật trên tài khoản demo mới, không sửa timestamp DB', async ({ page, request }) => {
  const email = 'demo-otp-' + randomUUID() + '@example.test';
  const password = randomUUID() + 'aA9!'; const nextPassword = randomUUID() + 'bB8!';
  const registered = await request.post('/api/v1/auth/register', { data: { email, password, fullName: 'Demo OTP Check' } });
  expect(registered.status()).toBe(201);
  await page.goto('/forgot-password'); await page.locator('#recovery-email').fill(email);
  const requested = page.waitForResponse(item => item.url().endsWith('/auth/forgot-password') && item.request().method() === 'POST');
  await page.getByRole('button', { name: 'Gửi mã đặt lại mật khẩu', exact: true }).click();
  expect((await requested).status()).toBe(200);
  let message;
  await expect.poll(async () => {
    const response = await request.get('http://127.0.0.1:8025/api/v1/messages?limit=100');
    message = (await response.json()).messages.find(item => item.To?.some(to => to.Address === email));
    return !!message;
  }).toBe(true);
  const mail = await request.get('http://127.0.0.1:8025/api/v1/message/' + message.ID);
  const otp = (await mail.json()).Text.match(/\b[0-9]{6}\b/)[0];
  await secretInput(page, '#recovery-otp', otp);
  await secretInput(page, '#recovery-password', nextPassword); await secretInput(page, '#recovery-confirm', nextPassword);
  await page.getByRole('button', { name: 'Đặt lại mật khẩu', exact: true }).click(); await page.waitForURL(/\/login$/);
  await page.locator('#login-email').fill(email); await secretInput(page, '#login-password', nextPassword);
  await page.getByRole('button', { name: 'Đăng nhập', exact: true }).click(); await page.waitForURL(/\/dashboard$/);
});
