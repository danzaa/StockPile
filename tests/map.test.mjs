import test from 'node:test';
import assert from 'node:assert/strict';
import {aggregate} from '../dist/core.js';
import {containerKeys,chestExcluded,buildSources,toggleChestSelection,changeChestExclusion,chestMap,fitChestGroup} from '../dist/map.js';
const chest=(id,x=0,y=64,z=0,dimension='minecraft:overworld')=>({id,name:id,dimension,type:'chest',position:[x,y,z],items:[{id:'minecraft:stone',count:64}]});
test('map keeps dimensions separate, fits distant coordinates and never mixes stored worlds',()=>{
 const containers=[chest('a',-30000000),chest('b',30000000),chest('nether',0,64,0,'minecraft:the_nether')];
 const map=chestMap(containers,'minecraft:overworld');assert.equal(map.visible.length,2);assert.equal(map.markers.flatMap(g=>g.containers).length,2);assert.ok(map.span>=60000000);assert.ok(map.xGrid.length<30);
 assert.equal(chestMap([],'minecraft:overworld').markers.length,0);assert.ok(Number.isFinite(chestMap([],'minecraft:overworld').span));
});
test('stacked chests remain distinct selectable containers in a shared map marker',()=>{
 const map=chestMap([chest('low'),chest('high',0,80)],'minecraft:overworld');assert.equal(map.markers.length,1);assert.deepEqual(map.markers[0].containers.map(c=>c.position[1]),[64,80]);
 assert.equal(buildSources(map.visible,{selected:['high']}).length,1);
});
test('custom selection starts with one chest and controls the available material pool',()=>{
 const a=chest('a'),b=chest('b',10);let selected=toggleChestSelection(null,a);assert.deepEqual(selected,['a']);
 assert.equal(aggregate(buildSources([a,b],{selected})).get('minecraft:stone').count,64);
 selected=toggleChestSelection(selected,b);assert.equal(buildSources([a,b],{selected}).length,2);
 selected=toggleChestSelection(selected,a);assert.deepEqual(selected,['b']);assert.equal(buildSources([a,b],{selected:[]}).length,0);
});
test('exclusions preserve observations and follow double-chest physical halves through replacements',()=>{
 const a=chest('a'),b=chest('b',1),double={...a,id:'a|b',members:['a','b']};
 let excluded=changeChestExclusion([],double,true);assert.equal(chestExcluded(a,excluded),true);assert.equal(chestExcluded(b,excluded),true);assert.equal(buildSources([a,b],{excluded}).length,0);
 excluded=changeChestExclusion(excluded,a,false);assert.equal(chestExcluded(double,excluded),true);
 excluded=changeChestExclusion(excluded,b,false);assert.equal(chestExcluded(double,excluded),false);assert.equal(a.items[0].count,64);
 assert.deepEqual(containerKeys(double),['a','b']);assert.equal(buildSources([double],{selected:['a']}).length,0);
});
test('map zoom bounds are finite and fitting a group preserves negative and vertical coordinates',()=>{
 const group=[chest('a',-20,12,-40),chest('b',-18,100,-38)],fit=fitChestGroup(group),map=chestMap(group,'minecraft:overworld',{...fit,span:1});
 assert.equal(map.span,4);assert.ok(fit.centerX<0&&fit.centerZ<0);assert.equal(chestMap(group,'minecraft:overworld',{centerX:NaN,centerZ:0,span:10}).visible.length,2);
});

test('box selection adds clustered and stacked chests to a build while skipping exclusions', async () => {
  const {mapBox,addChestsInBox}=await import('../dist/map.js');
  const nearby=[{...chest('double',2,64,-3),members:['left','right']},chest('stacked',2,80,-3),chest('excluded',2,90,-3)];
  const containers=[...nearby,chest('far',100),chest('nether',2,64,-3,'minecraft:the_nether')];
  const box=mapBox({x:4,y:0},{x:1,y:-4});
  const result=addChestsInBox(containers,'minecraft:overworld',box,['far'],['excluded']);
  assert.deepEqual(result,{selected:['far','left','right','stacked'],added:2});
  assert.deepEqual(buildSources(containers,{selected:result.selected}).map(c=>c.id),['double','stacked','far']);
  assert.deepEqual(addChestsInBox(containers,'minecraft:the_nether',box,null,['nether']),{selected:null,added:0});
});

test('terrain placement preserves the captured block coordinates and rejects untrusted image sources', async () => {
  const {validTerrain,fitTerrain}=await import('../dist/map.js');
  const snapshot={x:-208,z:16,size:208,capturedAt:1,png:'iVBORw0KGgo='};
  assert.equal(validTerrain(snapshot),true);
  assert.equal(validTerrain({...snapshot,png:'https://example.test/map.png'}),false);
  const view=fitTerrain(snapshot);
  assert.equal(view.centerX,-104);
  assert.equal(view.centerZ,120);
  assert.ok(chestMap([],'minecraft:overworld',view).height>snapshot.size);
});
