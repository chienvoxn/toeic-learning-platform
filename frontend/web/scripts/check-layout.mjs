export async function checkLayout({ cdp, evaluate, waitFor, navigate, capture }) {
  const catalogOnly = process.argv.includes('--catalog-only');
  const checks = [];
  const failures = [];
  const routes = [
    ['home', '/', '.dashboard-page'],
    ['tests', '/tests', '.test-list-card'],
    ['overview', '/tests/test_ets_2024_part5_mini/overview', '.part-selection'],
    ['vocabulary', '/vocabulary', '.vocab-deck'],
    ['flashcard', '/vocabulary/deck_common_1000/review', '.flashcard'],
    ['dictation', '/listening', '.media-workspace'],
    ['videos', '/listening?mode=shadowing', '.video-lessons-grid'],
    ['shadowing', '/listening?mode=shadowing&view=exercise&lesson=kiki', '.media-workspace'],
    ['profile', '/profile', '.profile-card'],
    ['settings', '/settings', '.learner-content h1'],
    ['catalog', '/catalog', '.mobile-catalog-page h1'],
    ['games', '/games', '.learner-content article'],
    ['word-match', '/games/word-match', '.learner-content h2'],
    ['deck-detail', '/vocabulary/deck_600_essential', '.learner-content h1'],
    ['404', '/missing-layout-check', '.learner-content'],
  ];
  const viewports = [
    [320, 568], [390, 844], [768, 1024], [900, 720],
    [1024, 768], [1280, 800], [1920, 1080],
  ];
  function verify(condition, message) { if (!condition) { failures.push(message); console.log(`FAIL: ${message}`); } }
  async function viewport(width, height) {
    await cdp('Emulation.setDeviceMetricsOverride', { width, height, deviceScaleFactor: 1, mobile: false });
  }
  const metrics = () => evaluate(`(() => {
    const rect = selector => { const r = document.querySelector(selector)?.getBoundingClientRect(); return r && { x:r.x,y:r.y,width:r.width,height:r.height,bottom:r.bottom,right:r.right }; };
    return {
      width: innerWidth, clientWidth: document.documentElement.clientWidth, height: innerHeight, scrollY, documentWidth: document.documentElement.scrollWidth,
      sidebar: rect('.learner-sidebar'), header: rect('.app-topbar'), main: rect('.learning-main'), footer: rect('.sidebar-bottom'),
      sidebarPosition: getComputedStyle(document.querySelector('.learner-sidebar')).position,
      headerPosition: getComputedStyle(document.querySelector('.app-topbar')).position,
      brokenImages: [...document.images].filter(i => new URL(i.src).origin === location.origin && (!i.complete || !i.naturalWidth)).map(i => i.src),
      favicon: document.querySelector('link[rel="icon"]')?.href,
      brandSources: [...document.querySelectorAll('.brand-mark')].map(i => i.src),
      selectedLinks: [...document.querySelectorAll('.sidebar-link[aria-current="page"]')].map(a => a.textContent),
      overflowElements: [...document.querySelectorAll('main *')].filter(e => {
        if (e.getBoundingClientRect().right <= document.documentElement.clientWidth + 1) return false;
        for (let p=e.parentElement; p && p !== document.body; p=p.parentElement) {
          if (['auto','scroll','hidden','clip'].includes(getComputedStyle(p).overflowX) && p.getBoundingClientRect().right <= document.documentElement.clientWidth + 1) return false;
        }
        return true;
      }).slice(-10).map(e => ({tag:e.tagName, class:e.className, right:e.getBoundingClientRect().right, text:e.textContent.slice(0,100)})),
    };
  })()`);
  for (const [width, height] of viewports) {
    if (catalogOnly && width !== 320 && width !== 390) continue;
    await viewport(width, height);
    for (const [name, path, ready] of routes) {
      if (catalogOnly && name !== 'catalog') continue;
      await navigate(path, `!!document.querySelector(${JSON.stringify(ready)})`);
      await evaluate('window.scrollTo(0,0)');
      await evaluate('document.fonts.ready.then(() => true)');
      await evaluate(`Promise.all([...document.images].filter(i => new URL(i.src).origin === location.origin && i.loading !== 'lazy').map(i => i.complete ? Promise.resolve(true) : new Promise(resolve => { i.addEventListener('load', () => resolve(true), {once:true}); i.addEventListener('error', () => resolve(false), {once:true}); setTimeout(() => resolve(false), 15000); }))).then(() => true)`);
      const before = await metrics();
      const expectedSidebar = width > 900 ? 240 : 0;
      const expectedHeader = width > 900 ? 70 : 64;
      const label = `${name}@${width}`;
      verify(before.documentWidth <= before.clientWidth, `${label}: overflow ${before.documentWidth} > ${before.clientWidth}: ${JSON.stringify(before.overflowElements)}`);
      verify(before.header.y === 0 && before.header.x === expectedSidebar && before.header.height === expectedHeader, `${label}: header geometry ${JSON.stringify(before.header)}`);
      verify(before.main.x === expectedSidebar, `${label}: content offset ${before.main.x} != ${expectedSidebar}`);
      verify(before.headerPosition === 'fixed' && before.sidebarPosition === 'fixed', `${label}: navigation must be fixed`);
      verify(before.brokenImages.length === 0, `${label}: broken images ${before.brokenImages.join(',')}`);
      verify(before.brandSources.every(src => src === before.favicon), `${label}: brand does not match favicon`);
      verify(before.selectedLinks.length <= 1, `${label}: multiple selected sidebar items`);
      if (width > 900) {
        verify(before.sidebar.width === 240 && before.sidebar.y === 0 && before.sidebar.height === height, `${label}: sidebar dimensions changed`);
        verify(before.footer.bottom <= height, `${label}: profile/collapse controls outside viewport`);
      } else verify(before.sidebar.right <= 0, `${label}: mobile drawer visible while closed`);
      await evaluate('window.scrollTo(0, Math.min(600, document.documentElement.scrollHeight - innerHeight))');
      const after = await metrics();
      verify(after.header.y === 0 && after.sidebar.y === 0, `${label}: navigation moved on scroll`);
      checks.push({ name, path, viewport: [width,height], before, after });
      if ([320,768,1024,1920].includes(width) && ['home','vocabulary','dictation','deck-detail'].includes(name)) {
        await evaluate('window.scrollTo(0,0)'); await capture(`${name}-${width}`);
      }
    }
    console.log(`Layout: ${width}px, ${checks.filter(check => check.viewport[0] === width).length} pages checked`);
  }

  if (catalogOnly) return { checks, failures, interactions: ['Narrow catalog layout'] };

  // Collapsing must survive client navigation and a full refresh.
  await viewport(1280, 800);
  await navigate('/');
  await evaluate('document.querySelector(".collapse-button").click()');
  await waitFor('document.querySelector(".learner-sidebar").getBoundingClientRect().width === 68');
  await evaluate(`document.querySelector('.sidebar-link[href="/tests"]').click()`);
  await waitFor('!!document.querySelector(".test-list-card")');
  verify((await metrics()).header.x === 68, 'Collapsed state was lost during client navigation');
  await navigate('/vocabulary/deck_common_1000/review', '!!document.querySelector(".flashcard")');
  verify((await metrics()).sidebar.width === 68, 'Collapsed state was lost after reload');
  await capture('collapsed-desktop');

  // Mobile drawer remains full width even when the desktop preference is collapsed.
  await viewport(390, 844);
  await navigate('/vocabulary', '!!document.querySelector(".vocab-deck")');
  await evaluate('document.querySelector(".app-topbar .mobile-menu").focus(); document.querySelector(".app-topbar .mobile-menu").click()');
  await waitFor('document.querySelector(".learner-sidebar").getBoundingClientRect().x === 0');
  const drawer = await metrics();
  verify(drawer.sidebar.width === 280, 'Mobile drawer inherited desktop compact width');
  verify(await evaluate('getComputedStyle(document.querySelector(".brand-wordmark")).display !== "none"'), 'Mobile drawer wordmark is hidden');
  verify(await evaluate('document.body.style.overflow === "hidden"'), 'Background scroll is not locked while drawer is open');
  await capture('drawer-mobile');
  await evaluate('document.dispatchEvent(new KeyboardEvent("keydown",{key:"Escape",bubbles:true}))');
  await waitFor('!document.querySelector(".learner-sidebar.is-open")');
  await waitFor('document.activeElement === document.querySelector(".app-topbar .mobile-menu")');
  await evaluate('document.querySelector(".app-topbar .mobile-menu").click()');
  await waitFor('document.querySelector(".learner-sidebar").getBoundingClientRect().x === 0');
  await evaluate(`document.querySelector('.sidebar-link[href="/tests"]').click()`);
  await waitFor('!!document.querySelector(".test-list-card") && !document.querySelector(".learner-sidebar.is-open")');
  verify(await evaluate('document.body.style.overflow !== "hidden"'), 'Navigation did not restore document scrolling');

  // Short landscape windows keep navigation and profile controls reachable.
  await viewport(1024, 400);
  await navigate('/');
  await evaluate('document.querySelector(".collapse-button").click()');
  await waitFor('document.querySelector(".learner-sidebar").getBoundingClientRect().width === 240');
  const short = await metrics();
  verify(short.footer.bottom <= 400, 'Sidebar footer is unreachable on a short screen');
  const sidebarScrolls = await evaluate('document.querySelector(".sidebar-scroll").scrollHeight > document.querySelector(".sidebar-scroll").clientHeight');
  verify(sidebarScrolls, 'Short sidebar menu does not scroll independently');
  await capture('short-desktop');

  // One favicon for auth pages, normal routes and nested exam routes.
  const faviconResults = [];
  for (const [name,path] of [['login','/login'],['register','/register'],['onboarding','/onboarding'],['health','/health']]) {
    await viewport(320, 640); await navigate(path, name === 'health' ? '!!document.querySelector(".learner-content")' : '!!document.querySelector("h1")');
    const icon = await evaluate('document.querySelector("link[rel=icon]").href');
    const result = await evaluate(`fetch(${JSON.stringify(icon)}).then(async r => ({ status:r.status, type:r.headers.get('content-type'), svg:(await r.text()).includes('<svg') }))`);
    faviconResults.push({ path, icon, ...result });
    verify(result.status === 200 && result.svg, `${name}: favicon missing/invalid`);
    verify(await evaluate('document.documentElement.scrollWidth <= document.documentElement.clientWidth'), `${name}: narrow page overflow`);
    if (name !== 'health') await capture(`${name}-320`);
  }
  await viewport(1280,800);
  await navigate('/tests/test_ets_2024_part5_mini/overview','!!document.querySelector(".part-selection")');
  await evaluate('document.querySelector(".start-exam-button").click()');
  await waitFor('!!document.querySelector(".exam-toolbar")');
  const examIcon = await evaluate('document.querySelector(".brand-mark").src === document.querySelector("link[rel=icon]").href');
  verify(examIcon, 'Exam uses a different brand icon');
  await viewport(320,640);
  verify(await evaluate('document.documentElement.scrollWidth <= document.documentElement.clientWidth'), 'Exam: narrow page overflow');
  return { checks, faviconResults, failures, interactions: ['Fixed header/sidebar on scroll', 'Consistent navigation geometry across pages', 'Collapse/navigation/reload persistence', 'Mobile drawer/Escape/focus/scroll lock', 'Short viewport sidebar scrolling', 'Shared favicon, including auth and exam'] };
}
