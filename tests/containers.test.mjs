import test from 'node:test';
import assert from 'node:assert/strict';
import {filterContainers,aggregate,containerKind,sourceScope,validateSnapshot,compareMaterials} from '../dist/core.js';
const box=(id,type,count,dimension='minecraft:overworld')=>({id,type,name:type,dimension,position:[1,64,2],lastSeen:1,items:[{id:'minecraft:stone',count,maxStack:64}],packedItems:[]});
const containers=[box('chest','chest',64),box('barrel','barrel',32),box('placed','blue_shulker_box',16),box('ender:p','ender_chest',128,'minecraft:the_nether')];
test('storage defaults exclude Ender Chest items',()=>{
  assert.equal(aggregate(filterContainers(containers)).get('minecraft:stone').count,112);
  assert.equal(filterContainers(containers).length,3);
});
test('Ender Chest view is personal and independent of the last-opened dimension',()=>{
  const selected=filterContainers(containers,{kinds:['ender'],dimension:'minecraft:overworld'});
  assert.equal(selected.length,1);assert.equal(aggregate(selected).get('minecraft:stone').count,128);
});
test('container toggles include only checked types, including an empty selection',()=>{
  assert.equal(aggregate(filterContainers(containers,{kinds:['barrel']})).get('minecraft:stone').count,32);
  assert.equal(filterContainers(containers,{kinds:[]}).length,0);
  assert.equal(aggregate(filterContainers(containers,{kinds:['chest','ender']})).get('minecraft:stone').count,192);
});
test('placed shulker origins are retained and distinct from packed contents',()=>{
  const selected=aggregate(filterContainers(containers,{kinds:['shulker']})).get('minecraft:stone');
  assert.equal(selected.placedShulkerCount,16);assert.equal(selected.packedCount,0);
  assert.equal(selected.locations[0].placed,true);assert.equal(selected.locations[0].kind,'shulker');
});
test('schematic checks follow selected sources and change after an item removal',()=>{
  const materials=[{id:'minecraft:stone',count:150}];
  assert.equal(compareMaterials(materials,aggregate(filterContainers(containers)))[0].missing,38);
  assert.equal(compareMaterials(materials,aggregate(filterContainers(containers,{kinds:['chest','ender']})))[0].missing,0);
  const updated=containers.map(c=>c.id==='chest'?box('chest','chest',4):c);
  assert.equal(aggregate(filterContainers(updated)).get('minecraft:stone').count,52);
});
test('scope names keep Ender Chest stock targets separate',()=>{
  assert.equal(sourceScope(['chest','barrel']),'storage');assert.equal(sourceScope(['ender']),'ender');
  assert.equal(sourceScope(['ender','shulker']),'combined');assert.equal(sourceScope([]),'none');
  assert.equal(containerKind({id:'ender:x',type:'chest'}),'ender');
});
test('paused tracking survives snapshot import and legacy snapshots default to active',()=>{
  assert.equal(validateSnapshot({schemaVersion:1,trackingEnabled:false,worlds:[]}).trackingEnabled,false);
  assert.equal(validateSnapshot({schemaVersion:1,worlds:[]}).trackingEnabled,true);
});
