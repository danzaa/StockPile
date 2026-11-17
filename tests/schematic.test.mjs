import test from 'node:test';
import assert from 'node:assert/strict';
import {decodeSchematic,readNbt,materialsFromNbt,stateToMaterials,parseMaterialList} from '../dist/schematic.js';
import {encodeNbt,fixture,tag} from './nbt-helper.mjs';
const quantities=result=>Object.fromEntries(result.materials.map(m=>[m.id,m.count]));
test('reads a gzip Litematica file with palette indices spanning a 64-bit boundary',async()=>{
  const raw=encodeNbt(fixture(),true);const result=await decodeSchematic(raw.buffer.slice(raw.byteOffset,raw.byteOffset+raw.byteLength),'tiny.litematic');
  assert.equal(result.name,'Tiny workshop');assert.equal(result.blocks,36);
  assert.deepEqual(quantities(result),{'minecraft:stone_bricks':10,'minecraft:spruce_log':6,'minecraft:lantern':2,'minecraft:spruce_slab':6,'minecraft:spruce_door':1,'minecraft:torch':2});
});
test('handles the single-palette 2-bit minimum and negative Litematica sizes',()=>{
  const root={Version:6,Regions:{One:{Size:{x:-2,y:1,z:1},Position:{x:2,y:0,z:0},BlockStatePalette:[{Name:'minecraft:stone'}],BlockStates:[0n]}}};
  assert.equal(quantities(materialsFromNbt(root))['minecraft:stone'],2);
});
test('does not silently approve overlapping regions',()=>{
  const r={Size:{x:1,y:1,z:1},Position:{x:0,y:0,z:0},BlockStatePalette:[{Name:'minecraft:stone'}],BlockStates:[0n]};
  assert.ok(materialsFromNbt({Version:6,Regions:{a:r,b:r}}).warnings.some(w=>w.includes('overlap')));
});
test('maps wall blocks even if their runtime block has no inventory item',()=>{
  const warnings=new Set();assert.deepEqual(stateToMaterials({Name:'minecraft:wall_torch'},{'minecraft:wall_torch':null},warnings),[{id:'minecraft:torch',count:1}]);
  assert.equal(warnings.size,0);
});
test('multiplies candles and snow layers; does not count the upper door half twice',()=>{
  const w=new Set();
  assert.equal(stateToMaterials({Name:'minecraft:candle',Properties:{candles:'4'}},null,w)[0].count,4);
  assert.equal(stateToMaterials({Name:'minecraft:snow',Properties:{layers:'7'}},null,w)[0].count,7);
  assert.deepEqual(stateToMaterials({Name:'minecraft:oak_door',Properties:{half:'upper'}},null,w),[]);
  assert.deepEqual(stateToMaterials({Name:'minecraft:red_bed',Properties:{part:'head'}},null,w),[]);
});
test('water and unknown modded blocks require review',()=>{
  const w=new Set();assert.deepEqual(stateToMaterials({Name:'minecraft:water'},null,w),[]);
  assert.deepEqual(stateToMaterials({Name:'example:machine'},null,w),[]);assert.equal(w.size,2);
  assert.deepEqual(stateToMaterials({Name:'example:machine'},{'example:machine':'example:machine_item'},new Set()),[{id:'example:machine_item',count:1}]);
});
test('Sponge v2 and v3 varints use the palette and reject truncated data',()=>{
  const v2={Version:2,Width:3,Height:1,Length:1,Palette:{'minecraft:air':0,'minecraft:stone':130},BlockData:Uint8Array.from([0,130,1,130,1])};
  assert.equal(quantities(materialsFromNbt(v2))['minecraft:stone'],2);
  const v3={Schematic:{Version:3,Width:3,Height:1,Length:1,Blocks:{Palette:v2.Palette,Data:v2.BlockData}}};
  assert.equal(quantities(materialsFromNbt(v3))['minecraft:stone'],2);
  assert.throws(()=>materialsFromNbt({...v2,BlockData:Uint8Array.from([0,130])}),/Truncated/);
});
test('vanilla structures check block indices and warn about entities',()=>{
  const root={size:[2,1,1],palette:[{Name:'minecraft:stone'}],blocks:[{state:0,pos:[0,0,0]}],entities:[{}]};
  assert.equal(quantities(materialsFromNbt(root))['minecraft:stone'],1);assert.ok(materialsFromNbt(root).warnings.some(w=>w.includes('Entities')));
  assert.throws(()=>materialsFromNbt({...root,blocks:[{state:9,pos:[0,0,0]}]}));
});
test('NBT rejects truncated, absurd and trailing data',()=>{
  const bytes=encodeNbt(fixture());
  assert.throws(()=>readNbt(bytes.subarray(0,bytes.length-1)),/truncated/);
  assert.throws(()=>readNbt(Buffer.concat([bytes,Buffer.from([0])])),/trailing/);
  assert.throws(()=>readNbt(Buffer.from([10,0,0,7,0,1,120,127,255,255,255,0])),/large/);
});
test('material CSV and JSON imports aggregate duplicates and reject bad quantities',()=>{
  const csv='"Item ID","Total"\r\n"minecraft:stone","12"\r\n"minecraft:stone","8"';
  assert.equal(parseMaterialList(csv,'test.csv').materials[0].count,20);
  assert.throws(()=>parseMaterialList('Item,Total\nStone,8','list.csv'),/Item ID/);
  assert.throws(()=>parseMaterialList('[{"id":"minecraft:stone","count":-2}]','test.json'));
});
test('checks upload limits and refuses unsupported format versions',async()=>{
  await assert.rejects(decodeSchematic(new ArrayBuffer(26*1024*1024),'huge.litematic'),/25 MB/);
  assert.throws(()=>materialsFromNbt({Version:900,Regions:{}}),/not supported/);
});
