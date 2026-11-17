export const MAX_COUNT = 2_147_483_647;
export const CONTAINER_KINDS = ['chest','barrel','shulker','ender'];
export const STORAGE_KINDS = ['chest','barrel','shulker'];
export function containerKind(container) {
  const type=String(container.type||'chest').replace(/^.*:/,'');
  if(type==='ender_chest'||String(container.id).startsWith('ender:'))return 'ender';
  if(type.endsWith('shulker_box'))return 'shulker';
  if(type.endsWith('barrel'))return 'barrel';
  return 'chest';
}
export function sourceScope(kinds) {
  const selected=new Set(kinds);
  return selected.has('ender')?(selected.size===1?'ender':'combined'):(selected.size?'storage':'none');
}
export function filterContainers(containers,{kinds=STORAGE_KINDS,dimension='all'}={}) {
  const enabled=new Set(kinds);
  return containers.filter(c=>enabled.has(containerKind(c))&&(containerKind(c)==='ender'||dimension==='all'||c.dimension===dimension));
}
export function humanize(id) {
  return id.replace(/^.*:/, '').split('_').map(s => s[0]?.toUpperCase() + s.slice(1)).join(' ');
}
export function validId(id) { return typeof id === 'string' && /^[a-z0-9_.-]+:[a-z0-9_./-]+$/.test(id); }
export function count(value, label = 'count') {
  if (!Number.isSafeInteger(value) || value < 0 || value > MAX_COUNT) throw new Error(`Invalid ${label}. Use a non-negative whole number.`);
  return value;
}
export function validateSnapshot(data) {
  if (!data || data.schemaVersion !== 1 || !Array.isArray(data.worlds)) throw new Error('This is not a Stockpile inventory export (schema version 1).');
  if (data.worlds.length > 200) throw new Error('Too many worlds in this export.');
  const ids = new Set();
  const worlds = data.worlds.map(world => {
    if (typeof world.id !== 'string' || !world.id || ids.has(world.id) || !Array.isArray(world.containers)) throw new Error('Invalid or duplicate world.');
    ids.add(world.id);
    if (world.containers.length > 20_000) throw new Error('Too many containers.');
    const containerIds = new Set();
    const containers = world.containers.map(c => {
      if (!c || typeof c.id !== 'string' || !c.id || containerIds.has(c.id) || !Array.isArray(c.items)) throw new Error('Invalid or duplicate container.');
      containerIds.add(c.id);
      if (typeof c.dimension !== 'string' || !Array.isArray(c.position) || c.position.length !== 3 || !c.position.every(Number.isSafeInteger)) throw new Error('Invalid container position.');
      if (c.items.length > 200) throw new Error('Too many item types in a container.');
      const seen = new Set();
      const items = c.items.map(i => {
        if (!validId(i.id) || seen.has(i.id)) throw new Error('Invalid or duplicate item ID.');
        seen.add(i.id);
        return {id:i.id, name:typeof i.name === 'string' ? i.name.slice(0,150) : humanize(i.id), count:count(i.count), maxStack:count(i.maxStack ?? 64)};
      });
      let packedItems;
      if(c.packedItems!=null){
        if(!Array.isArray(c.packedItems)||c.packedItems.length>10_000)throw new Error('Invalid shulker contents.');
        packedItems=c.packedItems.map(i=>{
          if(!validId(i.id)||!Array.isArray(i.path)||i.path.length<1||i.path.length>8||!i.path.every(s=>typeof s==='string'))throw new Error('Invalid packed-item location.');
          return {id:i.id,name:typeof i.name==='string'?i.name.slice(0,150):humanize(i.id),count:count(i.count),maxStack:count(i.maxStack??64),path:i.path.map(s=>s.slice(0,180))};
        });
      }
      return {id:c.id, name:String(c.name || 'Chest').slice(0,150), type:String(c.type || 'chest'), dimension:c.dimension, position:c.position, lastSeen:Math.max(0, Number(c.lastSeen) || 0), items,...(packedItems?{packedItems,packedScanComplete:c.packedScanComplete!==false}:{})};
    });
    return {id:world.id, name:String(world.name || world.id).slice(0,150), containers};
  });
  return {schemaVersion:1, capturedAt:Number(data.capturedAt) || 0, connected:!!data.connected, trackingEnabled:data.trackingEnabled!==false, ...(['26.2','26.3'].includes(data.minecraftVersion)?{minecraftVersion:data.minecraftVersion}:{}), worlds};
}
export function aggregate(containers, {includePacked=true}={}) {
  const result = new Map();
  for (const c of containers) {
    const locations=new Map();
    for (const [items,packed] of [[c.items,false],[includePacked?(c.packedItems||[]):[],true]])for(const item of items){
      if(!result.has(item.id))result.set(item.id,{id:item.id,name:item.name||humanize(item.id),maxStack:item.maxStack||64,count:0,looseCount:0,packedCount:0,placedShulkerCount:0,locations:[]});
      const row=result.get(item.id);row.count+=item.count;row[packed?'packedCount':'looseCount']+=item.count;
      if(containerKind(c)==='shulker')row.placedShulkerCount+=item.count;
      if(!item.count)continue;
      if(!locations.has(item.id)){
        const location={...c,kind:containerKind(c),placed:containerKind(c)==='shulker',items:undefined,packedItems:undefined,count:0,looseCount:0,packedCount:0,packedPaths:[]};
        locations.set(item.id,location);row.locations.push(location);
      }
      const location=locations.get(item.id);location.count+=item.count;location[packed?'packedCount':'looseCount']+=item.count;
      if(packed)location.packedPaths.push({path:item.path,count:item.count});
    }
  }
  return result;
}
export function inventoryRows(containers, targets = {}, options={}) {
  const items = aggregate(containers,options);
  for (const id of Object.keys(targets)) if (!items.has(id) && targets[id] > 0) items.set(id, {id, name:humanize(id), count:0, maxStack:64, locations:[]});
  return [...items.values()].map(item => {
    const target = targets[item.id] ?? 0;
    return {...item, target, status:target === 0 ? 'untracked' : item.count < target ? 'low' : item.count >= target * 2 ? 'plenty' : 'healthy'};
  });
}
export function compareMaterials(materials, inventory, multiplier = 1) {
  if (!Number.isSafeInteger(multiplier) || multiplier < 1 || multiplier > 999) throw new Error('Build copies must be between 1 and 999.');
  return materials.map(m => {
    const needed = count(m.count * multiplier, 'material quantity');
    const available = inventory.get(m.id)?.count ?? 0;
    return {...m, needed, available, allocated:Math.min(available, needed), missing:Math.max(0, needed-available)};
  });
}
export function csvEscape(value) { return '"' + String(value).replaceAll('"','""') + '"'; }
export function materialsCsv(rows) {
  const recipes=rows.some(r=>r.craftable!=null);
  return [recipes?'Item ID,Item,Required,In storage,Can craft,Missing':'Item ID,Item,Required,Available,Missing', ...rows.map(r => [r.id,r.name || humanize(r.id),r.needed,r.available,...(recipes?[r.craftable||0]:[]),r.missing].map(csvEscape).join(','))].join('\r\n');
}
