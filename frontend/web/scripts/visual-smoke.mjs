import { spawn } from 'node:child_process';
import { access, mkdtemp, readFile, mkdir, writeFile } from 'node:fs/promises';
import { join, resolve, dirname } from 'node:path';
import { tmpdir } from 'node:os';
import { fileURLToPath } from 'node:url';
import { checkLayout } from './check-layout.mjs';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const layoutMode = process.argv.includes('--layout');
const output = resolve(root, `docs/visual/reports/${layoutMode ? '2026-11-01-layout' : '2026-11-01-browser'}`);
const base = process.env.VISUAL_BASE_URL || 'http://127.0.0.1:5173';
const candidates = [process.env.CHROME_PATH, 'C:/Program Files/Google/Chrome/Application/chrome.exe', 'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe'].filter(Boolean);
let executable;
for (const path of candidates) { if (await access(path).then(() => true, () => false)) { executable = path; break; } }
if (!executable) throw new Error('Set CHROME_PATH to Chrome/Edge.');
await fetch(base).then(r => { if (!r.ok) throw new Error(`Dev server: ${r.status}`); });
await mkdir(output, { recursive: true });
const profile = await mkdtemp(join(tmpdir(), 'toeic-visual-'));
const browser = spawn(executable, ['--headless=new', '--remote-debugging-port=0', `--user-data-dir=${profile}`, '--no-first-run', '--no-default-browser-check', '--disable-background-networking', 'about:blank'], { windowsHide: true, stdio: 'ignore' });
let launchError;
browser.on('error', error => { launchError = error; });
const pause = ms => new Promise(r => setTimeout(r, ms));
let socket;
const pending = new Map();
let sequence = 0;
const errors = [];
const results = [];
let route = '';
function cdp(method, params = {}) {
  const id = ++sequence;
  return new Promise((resolveCall, reject) => {
    const timeout = setTimeout(() => { pending.delete(id); reject(new Error(`${method} timed out`)); }, 20000);
    pending.set(id, { resolve: value => { clearTimeout(timeout); resolveCall(value); }, reject });
    socket.send(JSON.stringify({ id, method, params }));
  });
}
async function evaluate(expression) {
  const result = await cdp('Runtime.evaluate', { expression, returnByValue: true, awaitPromise: true });
  if (result.exceptionDetails) throw new Error(result.exceptionDetails.text + ': ' + result.exceptionDetails.exception?.description);
  return result.result.value;
}
async function waitFor(expression) {
  for (let i = 0; i < 100; i++) { if (await evaluate(`Boolean(${expression})`)) return; await pause(100); }
  throw new Error(`Not ready at ${route}: ${expression}`);
}
async function navigate(path, ready = 'document.querySelector("h1")') {
  route = path;
  await cdp('Page.navigate', { url: base + path });
  await waitFor('document.readyState === "complete" && !!document.querySelector("#root").children.length');
  await waitFor(ready);
  await pause(500);
  await evaluate('document.fonts.ready.then(() => true)');
}
async function capture(name, selector = null) {
  await evaluate('Promise.all([...document.images].map(i => i.complete ? Promise.resolve(true) : new Promise(r => { i.onload = () => r(true); i.onerror = () => r(true); })))');
  const metrics = await evaluate(`({ path: location.pathname + location.search, width: innerWidth, clientWidth: document.documentElement.clientWidth, height: innerHeight, documentWidth: document.documentElement.scrollWidth, fontFamily: getComputedStyle(document.querySelector('h1') || document.body).fontFamily, fontLoaded: document.fonts.check('800 20px ' + getComputedStyle(document.querySelector('h1') || document.body).fontFamily), images: [...document.images].map(i => ({ src: i.getAttribute('src'), loaded: i.complete && i.naturalWidth > 0 })), h1: document.querySelector('h1')?.innerText })`);
  const clip = selector ? await evaluate(`{ const r = document.querySelector(${JSON.stringify(selector)}).getBoundingClientRect(); ({ x: r.x, y: r.y, width: r.width, height: r.height, scale: 1 }); }`) : null;
  const { data } = await cdp('Page.captureScreenshot', { format: 'png', captureBeyondViewport: !!clip, ...(clip ? { clip } : {}) });
  await writeFile(join(output, `${name}.png`), Buffer.from(data, 'base64'));
  results.push({ name, ...metrics, ...(clip ? { clip } : {}) });
}
function click(selector) { return evaluate(`document.querySelector(${JSON.stringify(selector)}).click()`); }
async function setInput(selector, value) {
  await evaluate(`{ const input = document.querySelector(${JSON.stringify(selector)}); Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value').set.call(input, ${JSON.stringify(value)}); input.dispatchEvent(new Event('input', { bubbles: true })); }`);
}
try {
  let port;
  for (let i = 0; i < 100; i++) {
    if (launchError) throw launchError;
    const text = await readFile(join(profile, 'DevToolsActivePort'), 'utf8').catch(() => '');
    if (text) { port = text.split('\n')[0]; break; }
    await pause(100);
  }
  if (!port) throw new Error('Headless browser did not start.');
  const pages = await fetch(`http://127.0.0.1:${port}/json/list`).then(r => r.json());
  socket = new WebSocket(pages.find(p => p.type === 'page').webSocketDebuggerUrl);
  await new Promise((resolveOpen, reject) => { socket.onopen = resolveOpen; socket.onerror = reject; });
  socket.onmessage = event => {
    const message = JSON.parse(event.data);
    if (message.id) {
      const call = pending.get(message.id); if (!call) return; pending.delete(message.id);
      if (message.error) call.reject(new Error(JSON.stringify(message.error))); else call.resolve(message.result);
    } else if (message.method === 'Runtime.exceptionThrown') errors.push({ route, description: message.params.exceptionDetails.exception?.description || message.params.exceptionDetails.text });
  };
  await cdp('Page.enable'); await cdp('Runtime.enable');
  await cdp('Page.setInterceptFileChooserDialog', { enabled: true });
  let layoutReport = null;
  if (layoutMode) {
    layoutReport = await checkLayout({ cdp, evaluate, waitFor, navigate, capture });
  } else {
  await cdp('Emulation.setDeviceMetricsOverride', { width: 1920, height: 1200, deviceScaleFactor: 1, mobile: false });
  for (const [name,path,ready] of [
    ['dashboard','/'], ['login','/login'], ['register','/register'], ['test-catalog','/tests','.test-list-card'],
    ['test-overview','/tests/test_ets_2024_part5_mini/overview','.part-selection'], ['vocabulary','/vocabulary','.vocab-deck'],
    ['flashcard-front','/vocabulary/deck_common_1000/review','.flashcard'], ['dictation','/listening'],
    ['video-library','/listening?mode=shadowing'], ['shadowing','/listening?mode=shadowing&view=exercise&lesson=kiki'], ['profile','/profile','.profile-card'],
  ]) {
    await navigate(path, ready ? `!!document.querySelector(${JSON.stringify(ready)})` : undefined); await capture(name);
    if (name === 'flashcard-front') { await click('.flashcard-flip'); await capture('flashcard-revealed'); await click('.rating-good'); await waitFor('!!document.querySelector(".review-complete")'); }
    if (name === 'dictation') {
      await evaluate(`{ const t=document.querySelector('#dictation-input'); Object.getOwnPropertyDescriptor(HTMLTextAreaElement.prototype,'value').set.call(t,'They also have backyards.'); t.dispatchEvent(new Event('input',{bubbles:true})); }`);
      await click('.dictation-submit'); await waitFor('!!document.querySelector(".dictation-feedback.correct")'); await capture('dictation-correct');
    }
    if (name === 'profile') { await click('.profile-edit-button'); await capture('profile-edit'); await setInput('#profile-name','Visual Test Learner'); await click('.profile-form button[type="submit"]'); await waitFor('document.querySelector(".profile-card h1")?.innerText.includes("Visual Test Learner")'); }
  }
  await navigate('/login'); await click('.login-options .navy-button');
  await setInput('#login-email','visual@example.com'); await setInput('#login-password','Password123');
  await click('.email-login button[type="submit"]'); await waitFor('location.pathname === "/"');
  await navigate('/tests/test_ets_2024_part5_mini/overview','!!document.querySelector(".part-selection")');
  await click('.start-exam-button'); await waitFor('!!document.querySelector(".question-panel")');
  await click('.answer-option'); await pause(400);
  if (!await evaluate('!!document.querySelector(".answer-option.selected")')) throw new Error('Answer selection failed');
  await capture('test-attempt');
  const attemptPath = await evaluate('location.pathname');
  await navigate(attemptPath, '!!document.querySelector(".question-panel")');
  if (!await evaluate('!!document.querySelector(".answer-option.selected")')) throw new Error('Answer was not restored after reload');
  await cdp('Page.handleJavaScriptDialog', { accept: true }).catch(() => {});
  const dialogHandler = event => { const m = JSON.parse(event.data); if (m.method === 'Page.javascriptDialogOpening') cdp('Page.handleJavaScriptDialog', { accept: true }).catch(() => {}); };
  socket.addEventListener('message', dialogHandler);
  await click('.exam-toolbar .submit-exam-button');
  await waitFor('!!document.querySelector(".score-summary")'); await capture('result');
  await click('.result-filters button'); await waitFor('!!document.querySelector(".result-answer-list article")');
  await cdp('Emulation.setDeviceMetricsOverride', { width: 390, height: 844, deviceScaleFactor: 1, mobile: false });
  for (const [name,path,ready] of [
    ['dashboard-mobile','/'], ['login-mobile','/login'], ['register-mobile','/register'],
    ['tests-mobile','/tests','.test-list-card'], ['overview-mobile','/tests/test_ets_2024_part5_mini/overview','.part-selection'],
    ['vocabulary-mobile','/vocabulary','.vocab-deck'], ['flashcard-mobile','/vocabulary/deck_common_1000/review','.flashcard'],
    ['dictation-mobile','/listening'], ['videos-mobile','/listening?mode=shadowing'],
    ['profile-mobile','/profile','.profile-card'], ['attempt-mobile',attemptPath,'.question-panel'],
  ]) { await navigate(path, ready ? `!!document.querySelector(${JSON.stringify(ready)})` : undefined); await capture(name); }
  // Natural frame sizes: compare these captures with the corresponding Figma artboards.
  await cdp('Emulation.setDeviceMetricsOverride', { width: 1297, height: 582, deviceScaleFactor: 1, mobile: false });
  await navigate('/register'); await capture('register-figma-size');
  await cdp('Emulation.setDeviceMetricsOverride', { width: 1280, height: 1024, deviceScaleFactor: 1, mobile: false });
  await navigate('/profile', '!!document.querySelector(".profile-card")'); await capture('profile-figma-size');
  await click('.profile-edit-button'); await capture('profile-edit-figma-size');
  await navigate('/login'); await capture('login-card', '.auth-dialog');
  }
  const failures = [...(layoutReport?.failures || []), ...results.flatMap(r => [
    ...(r.documentWidth > r.clientWidth ? [`${r.name}: horizontal overflow ${r.documentWidth} > ${r.width}`] : []),
    ...r.images.filter(i => !i.loaded).map(i => `${r.name}: missing image ${i.src}`),
    ...(!r.fontLoaded ? [`${r.name}: font missing`] : []),
  ])];
  if (errors.length) failures.push(...errors.map(e => `${e.route}: ${e.description}`));
  const report = { browser: executable, baseUrl: base, date: new Date().toISOString(), results, errors, failures, layout: layoutReport, interactions: layoutReport?.interactions || ['Email login', 'Flashcard reveal/rating', 'Dictation correct answer', 'Profile save', 'Start attempt/select/autosave/reload/submit', 'Result review'], note: 'Browser smoke checks do not establish pixel equality with Figma. Real data and incomplete backend features differ.' };
  await writeFile(join(output,'smoke-results.json'), JSON.stringify(report,null,2));
  const gallery = '# Actual UI — browser capture\n\nChrome headless, DPR 1. Các ảnh này kiểm tra UI chạy thật; không phải chứng nhận giống Figma từng pixel.\n\n' + results.map(r => `## ${r.name}\n\nRoute: ${r.path} · Viewport: ${r.width}×${r.height}${r.clip ? ` · Crop: ${r.clip.width}×${r.clip.height}` : ''}\n\n![${r.name}](${r.name}.png)\n`).join('\n');
  await writeFile(join(output,'GALLERY.md'), gallery);
  console.log(JSON.stringify({ captures: results.length, failures, output },null,2));
  if (failures.length) process.exitCode = 1;
} finally {
  if (socket?.readyState === WebSocket.OPEN) { await cdp('Browser.close').catch(() => {}); socket.close(); }
  browser.kill();
}
