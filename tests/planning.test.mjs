import test from 'node:test';
import assert from 'node:assert/strict';
import {aggregate,validateSnapshot} from '../dist/core.js';
import {stale,freeContainers,allocateClaims,reservationDeficit,mutateReservation,validatePlanning,historyTrends} from '../dist/planning-state.js';
import {sharedExport,mergeShared,sharedWorkspace} from '../dist/sharing.js';
const chest=(id='a',n=64)=>({id,name:'Supplies',type:'chest',dimension:'minecraft:overworld',position:[id==='a'?0:1,64,0],lastSeen:1000,items:[{id:'minecraft:stone',count:n}],packedItems:[{id:'minecraft:diamond',count:8,path:['Blue box (slot 3)']}]});
const base=()=>validatePlanning(null);
const request=(p,claims,revision=0)=>({action:'reserve',projectId:p,name:p,claims,revision});
const claim=(n=40,packed=false,itemId='minecraft:stone')=>({containerId:'a',itemId,packed,count:n});
test('stale boundaries and legacy observations are explicit without deleting stock',()=>{
  assert.equal(stale({lastSeen:1000},1,3601000),false);assert.equal(stale({lastSeen:1000},1,3601001),true);assert.equal(stale({lastSeen:0},24,1000),true);
  assert.equal(aggregate([chest()]).get('minecraft:stone').count,64);
});
test('reservations prevent double allocation and distinguish a project’s own claims',()=>{
  const state=mutateReservation(base(),request('road',[claim()]),[chest()]);
  assert.equal(aggregate(freeContainers([chest()],state)).get('minecraft:stone').count,24);
  assert.equal(aggregate(freeContainers([chest()],state,'road')).get('minecraft:stone').count,64);
  assert.throws(()=>mutateReservation(state,request('tower',[claim(25)],1),[chest()]),/no longer free/);
  assert.throws(()=>mutateReservation(state,request('tower',[claim(1)],0),[chest()]),/changed/);
  assert.equal(mutateReservation(state,request('tower',[claim(24)],1),[chest()]).revision,2);
});
test('claims use parent containers, preserve loose/packed separation and survive overlapping filters',()=>{
  const state=mutateReservation(base(),request('jewels',[claim(5,true,'minecraft:diamond')]),[chest()]);
  const free=aggregate(freeContainers([chest()],state));assert.equal(free.get('minecraft:diamond').count,3);assert.equal(free.get('minecraft:stone').count,64);
  assert.deepEqual(allocateClaims([{id:'minecraft:diamond',count:3}],[chest()],state,'other','local'),[claim(3,true,'minecraft:diamond')]);
  assert.throws(()=>allocateClaims([{id:'minecraft:diamond',count:4}],[chest()],state,'other','local'),/changed/);
  assert.throws(()=>allocateClaims([{id:'minecraft:diamond',count:1}],[chest()],state,'other','local',false),/changed/);
});
test('stock drops report a reservation deficit, releasing and completing do not consume inventory',()=>{
  const state=mutateReservation(base(),request('road',[claim(60)]),[chest()]);
  assert.equal(reservationDeficit([chest('a',20)],state),40);
  const complete=mutateReservation(state,{action:'complete',projectId:'road',revision:1},[chest()]);
  assert.equal(complete.reservations.road.status,'completed');assert.equal(aggregate(freeContainers([chest()],complete)).get('minecraft:stone').count,64);
  const released=mutateReservation(state,{action:'release',projectId:'road',revision:1},[chest()]);assert.deepEqual(released.reservations,{});
  assert.throws(()=>mutateReservation(state,{action:'release',projectId:'road',revision:1},[chest()],'teammate'),/another teammate/);
});
test('history counts only observed changes in the selected sources, not discoveries and deletions',()=>{
  const at=Date.now()-1000,events=['discovered','forgotten','replacement','imported','observed'].map(reason=>({at,reason,type:'chest',dimension:'minecraft:overworld',changes:[{itemId:'minecraft:stone',delta:-5}]}));
  const rows=historyTrends(events,{since:at-1000});assert.equal(rows[0].decrease,5);assert.equal(rows[0].net,-5);
  assert.deepEqual(historyTrends(events,{since:at-1000,kinds:['ender']}),[]);
});
test('legacy snapshots acquire empty planning state, malformed claims are rejected',()=>{
  const parsed=validateSnapshot({schemaVersion:1,worlds:[{id:'a',containers:[chest()]}]});assert.deepEqual(parsed.worlds[0].planning,base());
  assert.throws(()=>validatePlanning({revision:0,reservations:{bad:{projectId:'bad',status:'active',name:'Bad',claims:[claim(-1)]}},history:[]}));
});
test('manual team files never export Ender Chests, deduplicate physical locations and keep newest observations',()=>{
  const settings={teamId:'builders',serverId:'Friends server',name:'Builders'};
  const first=sharedExport({containers:[chest(),{...chest('ender'),id:'ender:player',type:'ender_chest'}]},settings,2000);
  assert.equal(first.containers.length,1);
  const newer=sharedExport({containers:[{...chest('another-player'),position:[0,64,0],lastSeen:2000,items:[{id:'minecraft:stone',count:12}]}]},settings,3000);
  const merged=mergeShared(first,newer);assert.equal(merged.containers.length,1);assert.equal(merged.containers[0].items[0].count,12);
  assert.equal(mergeShared(merged,first).containers[0].items[0].count,12);
  assert.throws(()=>mergeShared(first,{...newer,serverId:'Other server'}),/different team or server/);
  assert.match(sharedWorkspace(merged).id,/file-team/);
  assert.equal('reservations' in first,false);
});

test('manual team exports omit excluded storage as well as private Ender Chests',()=>{
 const a=chest(),b=chest('b'),packet=sharedExport({containers:[a,b],excludedLocations:['a']},{teamId:'builders',serverId:'Friends',name:'Builders'});
 assert.deepEqual(packet.containers.map(c=>c.id),['b']);assert.equal(a.items[0].count,64);
});
