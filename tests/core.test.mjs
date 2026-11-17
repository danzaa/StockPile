import test from 'node:test';
import assert from 'node:assert/strict';
import {aggregate,inventoryRows,compareMaterials,validateSnapshot,materialsCsv} from '../dist/core.js';
const chest=(id,items,dimension='minecraft:overworld')=>({id,name:'Chest',type:'chest',dimension,position:[0,64,0],lastSeen:1234,items:items.map(([id,count])=>({id,count,maxStack:64}))});
const snapshot=containers=>({schemaVersion:1,capturedAt:1234,worlds:[{id:'world-a',name:'Survival',containers}]});
test('inventory replaces a chest snapshot rather than appending it',()=>{
  const first=validateSnapshot(snapshot([chest('a',[['minecraft:stone',64]])]));
  first.worlds[0].containers[0]=chest('a',[['minecraft:stone',12]]);
  assert.equal(aggregate(first.worlds[0].containers).get('minecraft:stone').count,12);
});
test('aggregates item IDs and retains container locations',()=>{
  const rows=aggregate([chest('a',[['minecraft:stone',64]]),chest('b',[['minecraft:stone',32]])]);
  assert.equal(rows.get('minecraft:stone').count,96);assert.equal(rows.get('minecraft:stone').locations.length,2);
});
test('targets include missing items, and zero disables low-stock tracking',()=>{
  const rows=inventoryRows([chest('a',[['minecraft:stone',64]])],{'minecraft:stone':32,'minecraft:diamond':8,'minecraft:air':0});
  assert.equal(rows.find(r=>r.id==='minecraft:stone').status,'plenty');
  assert.equal(rows.find(r=>r.id==='minecraft:diamond').status,'low');
  assert.equal(rows.length,2);
  assert.equal(inventoryRows([chest('a',[['minecraft:stone',1]])])[0].status,'untracked');
});
test('schematic comparison supports copies and does not allocate excess stock to other items',()=>{
  const inv=aggregate([chest('a',[['minecraft:stone',100],['minecraft:oak_log',10]])]);
  const result=compareMaterials([{id:'minecraft:stone',count:40},{id:'minecraft:oak_log',count:8}],inv,2);
  assert.deepEqual(result.map(r=>[r.needed,r.available,r.missing,r.allocated]),[[80,100,0,80],[16,10,6,10]]);
  assert.throws(()=>compareMaterials([],inv,0));assert.throws(()=>compareMaterials([{id:'x:y',count:2_147_483_647}],inv,2));
});
test('imports reject negative quantities, invalid coordinates and duplicate chest identities',()=>{
  assert.throws(()=>validateSnapshot(snapshot([chest('a',[['minecraft:stone',-1]])])));
  assert.throws(()=>validateSnapshot(snapshot([chest('a',[]),chest('a',[])])));
  const c=chest('a',[]);c.position=['oops',64,0];assert.throws(()=>validateSnapshot(snapshot([c])));
  assert.throws(()=>validateSnapshot({schemaVersion:2,worlds:[]}));
});
test('empty chest snapshots retain their location and clear item totals',()=>{
  const data=validateSnapshot(snapshot([chest('a',[])]));assert.equal(data.worlds[0].containers.length,1);assert.equal(aggregate(data.worlds[0].containers).size,0);
});
test('exports quote material names correctly',()=>{
  const csv=materialsCsv([{id:'minecraft:stone',name:'Stone, "smooth"',needed:3,available:1,missing:2}]);
  assert.match(csv,/"Stone, ""smooth"""/);assert.match(csv,/"3","1","2"/);
});
