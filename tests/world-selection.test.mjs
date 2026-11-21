import test from 'node:test';
import assert from 'node:assert/strict';
import {selectLiveWorld,validateSnapshot} from '../dist/core.js';
const data=(active,session='one',connected=true)=>({schemaVersion:1,connected,activeWorldId:active,worldSession:session,worlds:[{id:'old',name:'Old world',containers:[]},{id:'new',name:'New world',containers:[]},{id:'team',name:'Team',containers:[]}]});
test('new connections select the joined world rather than the first saved inventory',()=>{
  const next=validateSnapshot(data('new'));
  assert.equal(next.activeWorldId,'new');assert.equal(next.worldSession,'one');
  assert.deepEqual(selectLiveWorld({connected:false,worlds:[]},next,''),{worldId:'new',reset:true});
  assert.deepEqual(selectLiveWorld(data('old'),data('new','two'),'old'),{worldId:'new',reset:true});
});
test('intentional saved/team selections survive ordinary polls but not a new connection',()=>{
  const current=data('new');
  assert.deepEqual(selectLiveWorld(current,structuredClone(current),'old'),{worldId:'old',reset:false});
  assert.deepEqual(selectLiveWorld(current,structuredClone(current),'team'),{worldId:'team',reset:false});
  assert.deepEqual(selectLiveWorld(current,data('new','rejoined'),'old'),{worldId:'new',reset:true});
});
test('an unresolved connected world never falls back to another world’s supplies',()=>{
  assert.deepEqual(selectLiveWorld(data('old'),data(null,null),'old'),{worldId:'',reset:true});
  assert.deepEqual(selectLiveWorld(data(null,null),data(null,null),''),{worldId:'',reset:false});
});
test('offline snapshots and older exports remain browsable without inventing an active world',()=>{
  const old={schemaVersion:1,connected:false,worlds:data('new').worlds};
  const parsed=validateSnapshot(old);assert.equal(Object.hasOwn(parsed,'activeWorldId'),false);
  assert.deepEqual(selectLiveWorld(old,parsed,'old'),{worldId:'old',reset:false});
  assert.equal(selectLiveWorld(data('new'),data(null,null,false),'new').worldId,'new');
  assert.throws(()=>validateSnapshot(data('unknown')),/active world/);
});
