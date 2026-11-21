import test from 'node:test';
import assert from 'node:assert/strict';
import {findUnavailable,findRequest} from '../dist/find.js';

test('in-game actions require a connected local dashboard',()=>{
  for(const source of ['demo','import'])assert.match(findUnavailable({source,connected:true}),/local Minecraft/);
  assert.match(findUnavailable({source:'local',connected:false}),/Join/);
  assert.match(findUnavailable({source:'local',connected:true,syncError:'offline'}),/Reconnect/);
  assert.equal(findUnavailable({source:'local',connected:true}),'');
});
test('find requests preserve world, parent container and packed-content choice without coordinates',()=>{
  const request={worldId:'world-a',containerId:'minecraft:overworld:1,64,2|minecraft:overworld:2,64,2',itemId:'minecraft:diamond',includePacked:false};
  assert.deepEqual(findRequest({...request,position:[123,456,789]}),request);
  assert.equal(findRequest({...request,includePacked:true}).includePacked,true);
});
test('find requests reject missing locations, malformed IDs and ambiguous options',()=>{
  const request={worldId:'world',containerId:'chest',itemId:'minecraft:stone',includePacked:true};
  for(const change of [{worldId:''},{containerId:null},{itemId:'stone'},{itemId:'minecraft:stone /tp'},{includePacked:'false'},{worldId:'a'.repeat(201)}])assert.throws(()=>findRequest({...request,...change}));
});
