import test from 'node:test';
import assert from 'node:assert/strict';
import {planMaterials,craftingCsv} from '../dist/recipes.js';
import {recipeCatalogs} from '../dist/recipes-data.js';
const stock=items=>new Map(Object.entries(items).map(([id,count])=>['minecraft:'+id,{count}]));
for(const version of ['26.2','26.3']){
 const catalog=recipeCatalogs[version];
 test(`${version}: smelting needs fuel and reserves physical ore plus fuel, never hypothetical output`,()=>{
  const want=[{id:'minecraft:iron_ingot',count:16}];
  assert.equal(planMaterials(want,stock({raw_iron:16}),1,catalog,{smelting:true}).rows[0].missing,16);
  const plan=planMaterials(want,stock({raw_iron:16,coal:2}),1,catalog,{smelting:true});
  assert.equal(plan.rows[0].missing,0);assert.equal(plan.steps[0].station,'blast_furnace');
  assert.equal(plan.steps[0].ticks,1600);assert.deepEqual(Object.fromEntries(plan.stockUsage.map(i=>[i.id,i.count])),{'minecraft:raw_iron':16,'minecraft:coal':2});
 });
 test(`${version}: insufficient fuel yields a feasible partial plan and direct demands protect fuel`,()=>{
  const plan=planMaterials([{id:'minecraft:iron_ingot',count:16}],stock({raw_iron:16,coal:1}),1,catalog,{smelting:true});
  assert.equal(plan.rows[0].craftable,8);assert.equal(plan.rows[0].missing,8);
  const held=planMaterials([{id:'minecraft:coal',count:1},{id:'minecraft:iron_ingot',count:8}],stock({coal:1,raw_iron:8}),1,catalog,{smelting:true});
  assert.equal(held.rows[1].missing,8);
 });
 test(`${version}: furnace heat is reused for successive jobs and bucket remainders are recorded`,()=>{
  const plan=planMaterials([{id:'minecraft:glass',count:3},{id:'minecraft:smooth_stone',count:5}],stock({sand:3,stone:5,coal:1}),1,catalog,{smelting:true,blast:false});
  assert.equal(plan.rows.reduce((n,r)=>n+r.missing,0),0);assert.equal(plan.stockUsage.find(i=>i.id==='minecraft:coal').count,1);
  const lava=planMaterials([{id:'minecraft:glass',count:100}],stock({sand:100,lava_bucket:1}),1,catalog,{smelting:true,fuelIds:['minecraft:lava_bucket']});
  assert.equal(lava.rows[0].missing,0);assert.deepEqual(lava.steps[0].remainders,[{id:'minecraft:bucket',count:1}]);assert.match(craftingCsv(lava),/lava_bucket/);
 });
 test(`${version}: furnace/ blast heat does not transfer and unselected fuels cannot be burned`,()=>{
  const plan=planMaterials([{id:'minecraft:iron_ingot',count:4},{id:'minecraft:glass',count:4}],stock({raw_iron:4,sand:4,coal:1}),1,catalog,{smelting:true});
  assert.ok(plan.rows.reduce((n,r)=>n+r.missing,0)>0);
  const none=planMaterials([{id:'minecraft:glass',count:1}],stock({sand:1,lava_bucket:1}),1,catalog,{smelting:true});assert.equal(none.rows[0].missing,1);
 });
}

test('physical reservation usage retains finished materials even when a recipe creates leftovers of the same item',()=>{
  const plan=planMaterials([{id:'minecraft:oak_planks',count:1},{id:'minecraft:crafting_table',count:1}],stock({oak_planks:1,oak_log:1}),1,recipeCatalogs['26.3']);
  assert.equal(plan.rows.reduce((n,r)=>n+r.missing,0),0);
  assert.deepEqual(Object.fromEntries(plan.stockUsage.map(i=>[i.id,i.count])),{'minecraft:oak_planks':1,'minecraft:oak_log':1});
});
