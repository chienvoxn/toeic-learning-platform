import { chromium } from '@playwright/test';
import assert from 'node:assert/strict';
const browser = await chromium.launch({executablePath:'C:/Program Files/Google/Chrome/Application/chrome.exe',headless:true});
try {
 const page=await browser.newPage(); const errors=[]; page.on('pageerror',e=>errors.push(e.message));
 await page.addInitScript(()=>localStorage.setItem('toeic.remote.session',JSON.stringify({accessToken:'fixture',tokenType:'Bearer',expiresAt:Date.now()+600000})));
 let snapshot=null; let ratingCalls=0;
 await page.route('**/api/**',async route=>{
  const req=route.request(), path=new URL(req.url()).pathname; let data;
  if(path.endsWith('/users/me')) data={userId:'u',fullName:'Learner',roles:['LEARNER']};
  else if(path.endsWith('/learning-session')) {if(req.method()==='PUT') snapshot=JSON.parse(req.postData()); if(req.method()==='DELETE')snapshot=null; data=snapshot?{data:snapshot}:null;}
  else if(path.endsWith('/flashcards'))data=[{vocabularyId:'word',word:'work',meaning:'lam viec'}];
  else if(path.endsWith('/review-state'))return route.fulfill({status:404,json:{}});
  else if(path.endsWith('/reviews')) {ratingCalls++; const rating=JSON.parse(req.postData()).rating;data={intervalDays:rating===1?0:1,nextReviewAt:new Date(Date.now()+(rating===1?600000:86400000)).toISOString()};}
  else data=[{deckId:'deck',name:'Test'}];
  await route.fulfill({json:data});
 });
 const base='http://127.0.0.1:5174/vocabulary/deck/review';
 await page.goto(base); await page.getByRole('button',{name:/TỐT/}).click();
 await page.getByRole('heading',{name:'Bạn đã hoàn thành lượt học!'}).waitFor();
 assert.deepEqual(snapshot.completedIds,['word']);
 await page.reload(); await page.getByRole('heading',{name:'Bạn đã hoàn thành lượt học!'}).waitFor(); assert.equal(ratingCalls,1);
 await page.getByRole('button',{name:'Học lượt mới'}).click();
 await page.getByRole('button',{name:/HỌC LẠI/}).click();
 await page.getByRole('heading',{name:'Đang chờ đến lịch học lại'}).waitFor();
 await page.reload(); await page.getByRole('heading',{name:'Đang chờ đến lịch học lại'}).waitFor();assert.equal(ratingCalls,2);
 assert.deepEqual(errors,[]); console.log('PASS Good completes, reload preserves completion, Again waits across reload, no duplicate reviews/page errors.');
}finally{await browser.close();}
