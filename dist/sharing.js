import {validateSnapshot,containerKind} from './core.js';
import {chestExcluded} from './map.js';
// Manual exchange is explicit and has no upload endpoint. Teammates use the same server label and team code.
export function sharedExport(world,{teamId,serverId,name},now=Date.now()){
  if(!/^[a-zA-Z0-9_-]{1,64}$/.test(teamId)||typeof serverId!=='string'||!serverId.trim()||serverId.length>100||typeof name!=='string'||!name.trim()||name.length>100)throw new Error('Enter a team code, server label and team name.');
  return {format:'stockpile-shared-storage',version:1,teamId,serverId:serverId.trim(),name:name.trim(),capturedAt:now,
    containers:world.containers.filter(c=>containerKind(c)!=='ender'&&!chestExcluded(c,world.excludedLocations||[])&&!(world.excludedContainerIds||[]).includes(c.id)).map(c=>({...c,items:c.items.map(i=>({...i})),packedItems:(c.packedItems||[]).map(i=>({...i,path:[...i.path]}))}))};
}
function physical(c){return c.members?.length?new Set(c.members):new Set([`${c.dimension}:${c.position.join(',')}`]);}
export function mergeShared(previous,packet){
  if(packet?.format!=='stockpile-shared-storage'||packet.version!==1)throw new Error('Unsupported team snapshot.');
  const meta=sharedExport({containers:[]},packet);packet={...packet,serverId:meta.serverId,name:meta.name};
  const incoming=validateSnapshot({schemaVersion:1,worlds:[{id:'team',name:packet.name,containers:packet.containers}]}).worlds[0].containers.filter(c=>containerKind(c)!=='ender');
  if(previous&&(previous.teamId!==packet.teamId||previous.serverId!==packet.serverId))throw new Error('This file belongs to a different team or server. Import it as a separate workspace.');
  let containers=[...(previous?.containers||[])];
  for(const c of incoming){
    const positions=physical(c),overlap=old=>old.dimension===c.dimension&&[...physical(old)].some(p=>positions.has(p));
    if(containers.some(old=>overlap(old)&&old.lastSeen>c.lastSeen))continue;
    containers=containers.filter(old=>!overlap(old));containers.push({...c,id:[...positions].sort().join('|')});
  }
  if(containers.length>20000)throw new Error('Too many shared containers.');
  return {...packet,containers,capturedAt:Math.max(packet.capturedAt||0,previous?.capturedAt||0)};
}
export function sharedWorkspace(packet){return {id:`file-team:${encodeURIComponent(packet.serverId)}:${packet.teamId}`,name:`Team snapshot · ${packet.name}`,containers:packet.containers,planning:{revision:0,reservations:{},history:[]},shared:false,live:false,manualTeam:true};}
