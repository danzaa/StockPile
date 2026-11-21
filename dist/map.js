export function containerKeys(c){return Array.isArray(c.members)&&c.members.length?c.members:[c.id];}
export function chestExcluded(c,locations=[]){const excluded=locations instanceof Set?locations:new Set(locations);return containerKeys(c).some(key=>excluded.has(key));}
export function buildSources(containers,{excluded=[],selected=null}={}){
  const hidden=new Set(excluded),chosen=selected===null?null:new Set(selected);
  return containers.filter(c=>!containerKeys(c).some(key=>hidden.has(key))&&(!chosen||containerKeys(c).every(key=>chosen.has(key))));
}
export function toggleChestSelection(selected,c){
  const keys=containerKeys(c);if(selected===null)return [...keys];
  const next=new Set(selected),remove=keys.every(key=>next.has(key));
  for(const key of keys)if(remove)next.delete(key);else next.add(key);
  return [...next];
}
export function changeChestExclusion(locations,c,value){const next=new Set(locations);for(const key of containerKeys(c))if(value)next.add(key);else next.delete(key);return [...next];}
export function chestMap(containers,dimension,viewport=null){
  const visible=containers.filter(c=>c.dimension===dimension);
  let centerX=0,centerZ=0,span=32;
  if(visible.length){const xs=visible.map(c=>c.position[0]+.5),zs=visible.map(c=>c.position[2]+.5),minX=Math.min(...xs),maxX=Math.max(...xs),minZ=Math.min(...zs),maxZ=Math.max(...zs);
    centerX=(minX+maxX)/2;centerZ=(minZ+maxZ)/2;span=Math.max(16,maxX-minX+8,(maxZ-minZ+8)/.6);
  }
  if(viewport&&[viewport.centerX,viewport.centerZ,viewport.span].every(Number.isFinite)){centerX=viewport.centerX;centerZ=viewport.centerZ;span=Math.max(4,Math.min(200000000,viewport.span));}
  const left=centerX-span/2,top=centerZ-span*.3,groups=new Map();
  for(const c of visible){const x=c.position[0]+.5,z=c.position[2]+.5;if(x<left||x>left+span||z<top||z>top+span*.6)continue;
    const key=`${Math.floor((x-left)/span*40)},${Math.floor((z-top)/span*40)}`;
    if(!groups.has(key))groups.set(key,{containers:[],x:0,z:0});const g=groups.get(key);g.containers.push(c);g.x+=x;g.z+=z;
  }
  const markers=[...groups.values()].map(g=>({...g,x:g.x/g.containers.length,z:g.z/g.containers.length}));
  const rough=span/8,base=10**Math.floor(Math.log10(rough)),step=[1,2,5,10].map(n=>n*base).find(n=>n>=rough);
  const grid=(start,length)=>{const rows=[];for(let n=Math.ceil(start/step)*step;n<=start+length;n+=step)rows.push(n);return rows;};
  return {dimension,visible,markers,centerX,centerZ,span,left,top,height:span*.6,radius:span/100,xGrid:grid(left,span),zGrid:grid(top,span*.6)};
}
export function fitChestGroup(containers){
  const map=chestMap(containers,containers[0]?.dimension||'');return {centerX:map.centerX,centerZ:map.centerZ,span:map.span};
}

export function mapBox(start, end) {
  return {
    left: Math.min(start.x, end.x),
    top: Math.min(start.y, end.y),
    right: Math.max(start.x, end.x),
    bottom: Math.max(start.y, end.y)
  };
}

export function addChestsInBox(containers, dimension, box, selected, excluded) {
  const keys = new Set(selected ?? []);
  const hidden = new Set(excluded);
  let added = 0;
  for (const chest of containers) {
    if (chest.dimension !== dimension || chestExcluded(chest, hidden)) continue;
    const x = chest.position[0] + .5, z = chest.position[2] + .5;
    if (x < box.left || x > box.right || z < box.top || z > box.bottom) continue;
    const members = containerKeys(chest);
    if (members.every(key => keys.has(key))) continue;
    members.forEach(key => keys.add(key));
    added++;
  }
  return {selected: added ? [...keys] : selected, added};
}

export function validTerrain(snapshot) {
  return snapshot && Number.isSafeInteger(snapshot.x) && Number.isSafeInteger(snapshot.z)
    && snapshot.size === 208 && Number.isSafeInteger(snapshot.capturedAt)
    && typeof snapshot.png === 'string' && snapshot.png.startsWith('iVBORw0KGgo')
    && snapshot.png.length <= 250000 && /^[a-zA-Z0-9+/]+=*$/.test(snapshot.png);
}

export function fitTerrain(snapshot) {
  return {
    centerX: snapshot.x + snapshot.size / 2,
    centerZ: snapshot.z + snapshot.size / 2,
    span: (snapshot.size + 12) / .6
  };
}
