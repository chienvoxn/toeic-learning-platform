import { test } from 'node:test';
import assert from 'node:assert/strict';
import { AnswerSaveQueue } from '../src/features/assessment/answer-save-queue.js';
import { watchExpiredAttempt } from '../src/features/assessment/expired-attempt-watcher.js';

const tick = () => new Promise(resolve => setImmediate(resolve));
test('expiry stops queued PUTs, preserves unconfirmed selections, and accepts only server reconciliation', async () => {
  let finish;const calls=[];
  const q=new AnswerSaveQueue(p=>{calls.push(p);return new Promise(resolve=>{finish=resolve;});});
  const first=q.enqueue({questionId:'one',selectedChoiceId:'a'});await tick();
  const second=q.enqueue({questionId:'two',selectedChoiceId:'b'});q.seal();
  assert.equal(q.status,'error');finish();await Promise.all([first,second]);
  assert.equal(calls.length,1);assert.equal(q.pending.size,1);assert.equal(q.status,'error');
  await q.retry();assert.equal(calls.length,1);
  q.reconcileSaved({parts:[{questions:[{id:'two',userAnswer:'B',options:[{key:'B',id:'b'}]}]}]});
  assert.equal(q.status,'saved');assert.equal(q.pending.size,0);
});
test('a lost save response is not a saved acknowledgement until read from server', async () => {
  const q=new AnswerSaveQueue(async()=>{throw new Error('network');});
  await q.enqueue({questionId:'one',selectedChoiceId:'a'});q.seal();assert.equal(q.status,'error');
  q.reconcileSaved({parts:[{questions:[{id:'one',userAnswer:null,options:[{key:'A',id:'a'}]}]}]});assert.equal(q.status,'error');
  q.reconcileSaved({parts:[{questions:[{id:'one',userAnswer:'A',options:[{key:'A',id:'a'}]}]}]});assert.equal(q.status,'saved');
});
test('expiry watcher retries network loss with bounded delay, never submits, and stops after persisted grade', async () => {
  let calls=0;const scheduled=[],errors=[],snapshots=[];
  watchExpiredAttempt({read:async()=>{calls++;if(calls===1)throw new Error('offline');return {status:calls===2?'IN_PROGRESS':'GRADED'};},onError:e=>errors.push(e),onSnapshot:s=>snapshots.push(s),schedule:(fn,ms)=>{scheduled.push({fn,ms});return scheduled.length;},cancel:()=>{}});
  await tick();assert.equal(errors.length,1);assert.equal(scheduled[0].ms,4000);
  await scheduled.shift().fn();assert.equal(scheduled[0].ms,2000);await scheduled.shift().fn();
  assert.equal(calls,3);assert.deepEqual(snapshots.map(s=>s.status),['IN_PROGRESS','GRADED']);assert.equal(scheduled.length,0);
});
test('expiry watcher cancels late responses on unmount', async () => {
  let finish;let rendered=false;let scheduled=false;
  const stop=watchExpiredAttempt({read:()=>new Promise(resolve=>{finish=resolve;}),onSnapshot:()=>{rendered=true;},onError:()=>{},schedule:()=>{scheduled=true;},cancel:()=>{}});
  stop();finish({status:'GRADED'});await tick();assert.equal(rendered,false);assert.equal(scheduled,false);
});
