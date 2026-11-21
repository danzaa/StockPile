import test from 'node:test';
import assert from 'node:assert/strict';
import {aggregate,inventoryRows,compareMaterials,validateSnapshot} from '../dist/core.js';
const storage=()=>({id:'chest-1',name:'Main storage',dimension:'minecraft:overworld',position:[1,64,2],lastSeen:1,items:[{id:'minecraft:iron_ingot',count:20,maxStack:64},{id:'minecraft:blue_shulker_box',count:2,maxStack:1}],packedItems:[{id:'minecraft:iron_ingot',count:64,maxStack:64,path:['Blue Shulker Box (slot 3)']},{id:'minecraft:iron_ingot',count:32,maxStack:64,path:['Blue Shulker Box (slot 4)']},{id:'minecraft:diamond',count:8,maxStack:64,path:['Blue Shulker Box (slot 4)','Inner box (slot 1)']}],packedScanComplete:true});
test('loose and shulker counts merge once per item and parent chest',()=>{
  const row=aggregate([storage()]).get('minecraft:iron_ingot');
  assert.equal(row.count,116);assert.equal(row.looseCount,20);assert.equal(row.packedCount,96);
  assert.equal(row.locations.length,1);assert.equal(row.locations[0].packedPaths.length,2);
  assert.deepEqual(row.locations[0].position,[1,64,2]);
});
test('excludes shulker contents without removing the physical shulker items',()=>{
  const rows=aggregate([storage()],{includePacked:false});
  assert.equal(rows.get('minecraft:iron_ingot').count,20);assert.equal(rows.get('minecraft:blue_shulker_box').count,2);
  assert.equal(rows.has('minecraft:diamond'),false);
});
test('stock targets and schematic checks use the same packed inventory',()=>{
  const row=inventoryRows([storage()],{'minecraft:iron_ingot':100})[0];
  assert.equal(row.status,'healthy');
  const materials=[{id:'minecraft:iron_ingot',count:110}];
  assert.equal(compareMaterials(materials,aggregate([storage()]))[0].missing,0);
  assert.equal(compareMaterials(materials,aggregate([storage()],{includePacked:false}))[0].missing,90);
});
test('export and import round-trip packed locations and completeness',()=>{
  const snapshot={schemaVersion:1,capturedAt:1,worlds:[{id:'world',containers:[storage()]}]};
  const parsed=validateSnapshot(JSON.parse(JSON.stringify(snapshot))).worlds[0].containers[0];
  assert.equal(parsed.packedItems.length,3);assert.equal(parsed.packedScanComplete,true);
  assert.equal(aggregate([parsed]).get('minecraft:diamond').count,8);
  assert.equal(aggregate([parsed]).get('minecraft:diamond').locations[0].packedPaths[0].path.length,2);
});
test('old exports remain readable and do not invent packed counts',()=>{
  const c=storage();delete c.packedItems;delete c.packedScanComplete;
  const parsed=validateSnapshot({schemaVersion:1,worlds:[{id:'world',containers:[c]}]}).worlds[0].containers[0];
  assert.equal(parsed.packedItems,undefined);assert.equal(aggregate([parsed]).get('minecraft:iron_ingot').count,20);
});
test('rejects negative packed counts and malformed nested locations',()=>{
  for(const change of [c=>c.packedItems[0].count=-4,c=>c.packedItems[0].path=[],c=>c.packedItems[0].path=Array(9).fill('box')]){
    const c=storage();change(c);assert.throws(()=>validateSnapshot({schemaVersion:1,worlds:[{id:'world',containers:[c]}]}));
  }
});
test('packed paths collapse repeated stacks only within the same box and parent container',()=>{
 const a=storage();a.packedItems=Array.from({length:27},()=>({id:'minecraft:diamond',name:'Diamond',count:64,maxStack:64,path:['Shulker Box (slot 22)']}));
 a.packedItems.push({id:'minecraft:diamond',count:1,path:['Shulker Box (slot 23)']});
 const b={...a,id:'other-chest',position:[99,64,2],packedItems:[{id:'minecraft:diamond',count:64,path:['Shulker Box (slot 22)']}]};
 const row=aggregate([a,b]).get('minecraft:diamond');assert.equal(row.count,1793);assert.equal(row.locations.length,2);
 assert.deepEqual(row.locations[0].packedPaths,[{path:['Shulker Box (slot 22)'],count:1728},{path:['Shulker Box (slot 23)'],count:1}]);assert.equal(row.locations[1].packedPaths[0].count,64);
});
