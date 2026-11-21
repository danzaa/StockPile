const valid=id=>typeof id==='string'&&id.length<=256&&/^[a-z0-9_.-]+:[a-z0-9_./-]+$/.test(id);
const integer=n=>Number.isSafeInteger(n)&&n>=0;
export const claimKey=c=>`${c.containerId}\n${c.itemId}\n${!!c.packed}`;
export function validatePlanning(value){
  if(value==null)return {revision:0,reservations:{},history:[]};
  if(!integer(value.revision)||!value.reservations||typeof value.reservations!=='object'||Array.isArray(value.reservations)||Object.keys(value.reservations).length>200||!Array.isArray(value.history)||value.history.length>1000)throw new Error('Invalid planning data.');
  const reservations=Object.fromEntries(Object.entries(value.reservations).map(([key,r])=>{
    if(!r||r.projectId!==key||!/^[a-zA-Z0-9_.:-]{1,160}$/.test(key)||!['active','completed'].includes(r.status)||typeof r.name!=='string'||!Array.isArray(r.claims)||r.claims.length>5000)throw new Error('Invalid reservation.');
    const claims=r.claims.map(c=>{if(typeof c.containerId!=='string'||!c.containerId||c.containerId.length>500||!valid(c.itemId)||typeof c.packed!=='boolean'||!integer(c.count)||c.count<1||c.count>2147483647)throw new Error('Invalid reserved material.');return {...c};});
    return [key,{projectId:key,name:r.name.slice(0,150),owner:String(r.owner||'local').slice(0,200),updatedAt:Number(r.updatedAt)||0,status:r.status,claims}];
  }));
  const history=value.history.map(e=>{
    if(!e||!integer(e.at)||typeof e.containerId!=='string'||!Array.isArray(e.changes)||e.changes.length>512||!['observed','discovered','forgotten','replacement','imported','transfer'].includes(e.reason))throw new Error('Invalid stock history.');
    return {...e,changes:e.changes.map(c=>{if(!valid(c.itemId)||!Number.isSafeInteger(c.delta))throw new Error('Invalid stock change.');return {...c};})};
  });
  return {revision:value.revision,reservations,history};
}
export function stale(container,hours=24,now=Date.now()){return !container.lastSeen||now-container.lastSeen>Math.max(1,hours)*3600000;}
export function reservedClaims(planning,ignoreProject='',owner='local'){
  const held=new Map();
  for(const r of Object.values(planning?.reservations||{}))if(r.status==='active'&&!(r.projectId===ignoreProject&&r.owner===owner))for(const c of r.claims)held.set(claimKey(c),(held.get(claimKey(c))||0)+c.count);
  return held;
}
export function freeContainers(containers,planning,ignoreProject='',owner='local'){
  const held=reservedClaims(planning,ignoreProject,owner);
  return containers.map(c=>{
    const subtract=(items,packed)=>(items||[]).map(i=>{const key=claimKey({containerId:c.id,itemId:i.id,packed}),take=Math.min(i.count,held.get(key)||0);held.set(key,(held.get(key)||0)-take);return {...i,count:i.count-take};});
    return {...c,items:subtract(c.items,false),packedItems:subtract(c.packedItems,true)};
  });
}
export function allocateClaims(usage,containers,planning,projectId,owner,includePacked=true){
  const need=new Map(usage.map(i=>[i.id,i.count])),claims=[];
  for(const c of freeContainers(containers,planning,projectId,owner))for(const [items,packed] of [[c.items,false],[includePacked?(c.packedItems||[]):[],true]])for(const i of items){
    const count=Math.min(need.get(i.id)||0,i.count);if(!count)continue;
    claims.push({containerId:c.id,itemId:i.id,packed,count});need.set(i.id,need.get(i.id)-count);
  }
  if([...need.values()].some(n=>n>0))throw new Error('The material plan changed. Refresh and calculate it again.');
  return claims;
}
export function reservationDeficit(containers,planning){
  const held=reservedClaims(planning),stock=new Map();
  for(const c of containers)for(const [items,packed] of [[c.items,false],[c.packedItems||[],true]])for(const i of items){const key=claimKey({containerId:c.id,itemId:i.id,packed});stock.set(key,(stock.get(key)||0)+i.count);}
  return [...held].reduce((sum,[key,n])=>sum+Math.max(0,n-(stock.get(key)||0)),0);
}
export function mutateReservation(planning,request,containers,owner='local',now=Date.now()){
  const next=structuredClone(validatePlanning(planning));
  if(request.revision!==next.revision)throw new Error('Reservations changed. Refresh and retry.');
  const old=next.reservations[request.projectId];
  if(old&&old.owner!==owner)throw new Error('This reservation belongs to another teammate.');
  if(request.action==='reserve'){
    const r={projectId:request.projectId,name:request.name,owner,updatedAt:now,status:'active',claims:request.claims};
    validatePlanning({...next,reservations:{...next.reservations,[request.projectId]:r}});
    if(!r.claims.length)throw new Error('No available materials to reserve.');
    const free=freeContainers(containers,next,request.projectId,owner),stock=new Map();
    for(const c of free)for(const [items,packed] of [[c.items,false],[c.packedItems||[],true]])for(const i of items){const key=claimKey({containerId:c.id,itemId:i.id,packed});stock.set(key,(stock.get(key)||0)+i.count);}
    for(const claim of r.claims){const key=claimKey(claim);if(claim.count>(stock.get(key)||0))throw new Error('These supplies are no longer free.');stock.set(key,stock.get(key)-claim.count);}
    next.reservations[request.projectId]=r;
  }else if(['release','complete'].includes(request.action)){
    if(!old)throw new Error('Reservation not found.');
    if(request.action==='complete')next.reservations[request.projectId]={...old,status:'completed',updatedAt:now,claims:[]};
    else delete next.reservations[request.projectId];
  }else throw new Error('Unknown reservation action.');
  next.revision++;return next;
}
export function historyTrends(events,{since=Date.now()-86400000,kinds=null,dimension='all'}={}){
  const rows=new Map();
  const kind=e=>e.type==='ender_chest'?'ender':e.type?.endsWith('shulker_box')?'shulker':e.type?.endsWith('barrel')?'barrel':'chest';
  for(const e of events||[])if(e.reason==='observed'&&e.at>=since&&(!kinds||kinds.includes(kind(e)))&&(dimension==='all'||e.dimension===dimension||kind(e)==='ender'))for(const c of e.changes){
    const r=rows.get(c.itemId)||{id:c.itemId,increase:0,decrease:0,net:0};r.net+=c.delta;if(c.delta>0)r.increase+=c.delta;else r.decrease-=c.delta;rows.set(c.itemId,r);
  }
  const hours=Math.max(1/60,(Date.now()-since)/3600000);
  return [...rows.values()].map(r=>({...r,perHour:r.net/hours})).sort((a,b)=>b.decrease-a.decrease||b.increase-a.increase);
}
