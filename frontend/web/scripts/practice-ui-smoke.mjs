import {chromium} from '@playwright/test';
import assert from 'node:assert/strict';
const browser=await chromium.launch({executablePath:'C:/Program Files/Google/Chrome/Application/chrome.exe',headless:true});
try{
 const page=await browser.newPage();const errors=[];page.on('pageerror',e=>errors.push(e.message));
 await page.addInitScript(()=>localStorage.setItem('toeic.remote.session',JSON.stringify({accessToken:'fixture',tokenType:'Bearer',expiresAt:Date.now()+600000})));
 let selected=null,graded=false,failSave=true,sectionMode=false;
 const question={order:1,questionId:'q',questionVersionId:'v',partNumber:7,passage:{groupVersionId:'group',instruction:'Read the passage.',passageText:'Shared reading passage.'},questionText:'She ___ here.',choices:[{choiceId:'a',label:'A',content:'works'},{choiceId:'b',label:'B',content:'work'}]};
 const result=()=>({attemptId:'test',totalQuestions:1,correctCount:1,incorrectCount:0,unansweredCount:0,rawScore:1,accuracy:100,questions:[{questionVersionId:'v',selectedChoiceId:selected,correctChoiceId:'a',isCorrect:true,explanation:'Third person singular.'}]});
 await page.route('**/api/**',async route=>{const req=route.request(),path=new URL(req.url()).pathname;let data;
 if(path.endsWith('/users/me'))data={userId:'u',fullName:'Learner',roles:['LEARNER']};
 else if(path.endsWith('/answers/v')){if(failSave){failSave=false;return route.fulfill({status:503,json:{message:'Save failed'}});}selected=JSON.parse(req.postData()).selectedChoiceId;data={questionVersionId:'v',selectedChoiceId:selected};}
 else if(path.endsWith('/submit')){graded=true;data=result();}
 else if(path.endsWith('/result'))data=result();
 else { if(req.method()==='POST') {const body=JSON.parse(req.postData());sectionMode=body.sourceType==='SECTION_SNAPSHOT';if(sectionMode)assert.deepEqual(body.section,{type:'READING',questionCount:10,durationMinutes:30});else assert.deepEqual(body.practice.partNumbers,[7]);} data={attemptId:'test',sourceType:sectionMode?'SECTION_SNAPSHOT':'PRACTICE_SNAPSHOT',section:sectionMode?{questionCount:10,durationMinutes:30}:undefined,expired:sectionMode,status:graded?'GRADED':'IN_PROGRESS',expiresAt:new Date(Date.now()+(sectionMode?-1000:86400000)).toISOString(),totalQuestions:1,questions:[question],answers:selected?[{questionVersionId:'v',selectedChoiceId:selected}]:[]}; }
 await route.fulfill({json:data});});
 page.on('dialog',dialog=>dialog.accept());
 await page.goto('http://127.0.0.1:5174/practice');await page.getByRole('checkbox',{name:'Part 5',exact:true}).uncheck();await page.getByRole('checkbox',{name:'Part 7',exact:true}).check();await page.getByRole('button',{name:'Bắt đầu luyện tập'}).click();await page.getByRole('button',{name:'A. works'}).waitFor();assert.ok(await page.getByText('Shared reading passage.').isVisible());
 await page.getByRole('button',{name:'A. works'}).click();await page.getByRole('alert').waitFor();assert.equal(await page.getByRole('button',{name:'A. works'}).getAttribute('aria-pressed'),'false');
 await page.getByRole('button',{name:'A. works'}).click();await page.waitForFunction(()=>document.querySelector('[aria-pressed="true"]'));
 await page.reload();await page.locator('[aria-pressed="true"]').waitFor();await page.getByRole('button',{name:'Nộp bài',exact:true}).click();await page.getByRole('heading',{name:'Kết quả luyện tập'}).waitFor();assert.ok(await page.getByText('Third person singular.').isVisible());
 await page.reload();await page.getByRole('heading',{name:'Kết quả luyện tập'}).waitFor();graded=false;selected=null;await page.goto('http://127.0.0.1:5174/practice');await page.getByRole('radio').nth(1).check();await page.getByRole('button',{name:/^Bắt đầu/}).click();await page.getByRole('timer').waitFor();assert.equal(await page.getByRole('timer').innerText(),'00:00');assert.ok(await page.getByRole('button',{name:'A. works'}).isDisabled());await page.getByRole('button',{name:'Nộp bài',exact:true}).click();await page.locator('.score-summary').waitFor();assert.deepEqual(errors,[]);
 console.log('PASS start, failed save, retry, reload answer, submit, result/explanation and reload graded attempt.');
}finally{await browser.close();}
