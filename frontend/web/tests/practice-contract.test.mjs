import { test } from 'node:test';
import assert from 'node:assert/strict';
import { loadBrowserModule } from './load-browser-module.mjs';
const {practiceApi}=await loadBrowserModule('features/assessment/repositories/practice.api.js');
test('practice uses exact snapshot contract, UUID answer path and bodyless submit',async()=>{
 const original=globalThis.fetch;const calls=[];
 globalThis.localStorage={getItem:()=>null};
 globalThis.fetch=async(url,options)=>{calls.push({url,...options});return new Response('{}',{status:200});};
 try {
  await practiceApi.start(10); await practiceApi.save('attempt','version','choice'); await practiceApi.save('attempt','version',null);await practiceApi.submit('attempt');await practiceApi.result('attempt');
  assert.deepEqual(JSON.parse(calls[0].body),{sourceType:'PRACTICE_SNAPSHOT',practice:{partNumbers:[5],questionCount:10}});
  assert.equal(calls[1].url,'/api/v1/attempts/attempt/answers/version');assert.deepEqual(JSON.parse(calls[1].body),{selectedChoiceId:'choice'});
  assert.deepEqual(JSON.parse(calls[2].body),{selectedChoiceId:null});assert.equal(calls[3].body,undefined);assert.equal(calls[4].url,'/api/v1/attempts/attempt/result');
 }finally{globalThis.fetch=original;delete globalThis.localStorage;}
});
