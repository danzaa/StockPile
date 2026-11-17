import test from 'node:test';
import assert from 'node:assert/strict';
import {planMaterials,craftingCsv} from '../dist/recipes.js';
import {recipeCatalogs} from '../dist/recipes-data.js';
import {materialsCsv,validateSnapshot} from '../dist/core.js';
const inventory = items => new Map(Object.entries(items).map(([id,count]) => ['minecraft:'+id,{count}]));
const material = (id,count) => ({id:'minecraft:'+id,count});
const plan = (materials,items,version='26.3',copies=1) => planMaterials(materials,inventory(items),copies,recipeCatalogs[version]);

for (const version of ['26.2','26.3']) {
  test(`${version}: real recipes convert logs to exact wood planks`,()=>{
    const result=plan([material('oak_planks',8)],{oak_log:2},version);
    assert.equal(result.rows[0].available,0);
    assert.equal(result.rows[0].craftable,8);
    assert.equal(result.rows[0].missing,0);
    assert.equal(result.steps[0].ingredients[0].id,'minecraft:oak_log');
    assert.equal(result.steps[0].ingredients[0].count,2);
  });
  test(`${version}: multistep crafting and batch leftovers share one stock budget`,()=>{
    const result=plan([material('oak_planks',1),material('stick',4)],{oak_log:1},version);
    assert.equal(result.rows.reduce((n,r)=>n+r.missing,0),0);
    assert.equal(result.steps.filter(s=>s.output==='minecraft:oak_planks').reduce((n,s)=>n+s.batches,0),1);
  });
  test(`${version}: stonecutting makes four stairs from four blocks`,()=>{
    const result=plan([material('stone_brick_stairs',4)],{stone_bricks:4},version);
    assert.equal(result.rows[0].missing,0);
    assert.equal(result.steps.at(-1).station,'stonecutter');
    assert.equal(result.steps.at(-1).ingredients[0].count,4);
  });
}
test('reserves every directly required material before spending crafting ingredients',()=>{
  const result=plan([material('oak_stairs',4),material('oak_planks',6)],{oak_planks:6});
  assert.equal(result.rows[0].craftable,0);
  assert.equal(result.rows[1].direct,6);
});
test('shared iron cannot be promised to a bucket and bars twice',()=>{
  const result=plan([material('bucket',1),material('iron_bars',16)],{iron_ingot:6});
  assert(result.rows.some(r=>r.missing>0));
  const iron=result.steps.flatMap(s=>s.ingredients).filter(i=>i.id==='minecraft:iron_ingot').reduce((n,i)=>n+i.count,0);
  assert(iron<=6);
});
test('tag ingredients can combine different plank types for a chest',()=>{
  const result=plan([material('chest',1)],{oak_planks:3,spruce_planks:5});
  assert.equal(result.rows[0].missing,0);
  assert.equal(result.steps.at(-1).ingredients.reduce((n,i)=>n+i.count,0),8);
});
test('oak planks cannot be manufactured from a birch log',()=>{
  assert.equal(plan([material('oak_planks',4)],{birch_log:1}).rows[0].missing,4);
});
test('counts only complete recipe batches and retains the remainder',()=>{
  const result=plan([material('oak_stairs',8)],{oak_planks:9});
  assert.equal(result.rows[0].craftable,4);
  assert.equal(result.rows[0].missing,4);
});
test('build copies multiply ingredient costs',()=>{
  const result=plan([material('oak_planks',4)],{oak_log:1},'26.3',2);
  assert.equal(result.rows[0].needed,8);
  assert.equal(result.rows[0].craftable,4);
  assert.equal(result.rows[0].missing,4);
});
test('reversible compression recipes cannot create stock from nothing',()=>{
  const result=plan([material('iron_block',1)],{});
  assert.equal(result.rows[0].craftable,0);
  assert.equal(result.steps.length,0);
});
test('smelting and custom recipes remain direct shortages',()=>{
  const result=plan([material('stone',1),{id:'example:panel',count:3}],{cobblestone:64,coal:64});
  assert.equal(result.rows[0].missing,1);
  assert.equal(result.rows[1].missing,3);
});
test('duplicate material rows do not double-spend direct stock',()=>{
  const result=plan([material('stone',6),material('stone',6)],{stone:6});
  assert.equal(result.rows.reduce((n,r)=>n+r.allocated,0),6);
});
test('crafting checklist exports the actual chosen ingredients and station',()=>{
  const text=craftingCsv(plan([material('oak_planks',4)],{oak_log:1}));
  assert.match(text,/minecraft:oak_log/);
  assert.match(text,/crafting/);
});
test('material CSV keeps stored quantities separate from craftable quantities',()=>{
  const result=plan([material('oak_planks',4)],{oak_log:1});
  const text=materialsCsv(result.rows);
  assert.match(text,/In storage,Can craft,Missing/);
  assert.match(text,/"4","0","4","0"/);
});
test('new exports retain their recipe version and older exports remain readable',()=>{
  assert.equal(validateSnapshot({schemaVersion:1,minecraftVersion:'26.2',worlds:[]}).minecraftVersion,'26.2');
  assert.equal(validateSnapshot({schemaVersion:1,worlds:[]}).minecraftVersion,undefined);
});
test('replaying generated crafting steps never spends unavailable ingredients',()=>{
  let seed=12345;
  const random=n=>{seed=(seed*1664525+1013904223)>>>0;return seed%n;};
  const stocks=['oak_log','spruce_log','oak_planks','spruce_planks','stone','stone_bricks','iron_ingot','stick','coal','iron_block'];
  const needs=['oak_stairs','spruce_stairs','stone_brick_stairs','chest','torch','lantern','iron_bars','iron_ingot','oak_planks'];
  for(let example=0;example<40;example++){
    const items=Object.fromEntries(stocks.map(id=>[id,random(15)]));
    const result=plan(needs.map(id=>material(id,random(10)+1)),items);
    const pool=new Map(Object.entries(items).map(([id,n])=>['minecraft:'+id,n]));
    for(const step of result.steps){
      for(const ingredient of step.ingredients){
        assert((pool.get(ingredient.id)||0)>=ingredient.count,`Step spends unavailable ${ingredient.id}`);
        pool.set(ingredient.id,pool.get(ingredient.id)-ingredient.count);
      }
      pool.set(step.output,(pool.get(step.output)||0)+step.count);
    }
    for(const row of result.rows){
      assert((pool.get(row.id)||0)>=row.allocated,`Build double-spends ${row.id}`);
      pool.set(row.id,(pool.get(row.id)||0)-row.allocated);
    }
  }
});
