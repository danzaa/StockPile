import {validId, count, humanize} from './core.js';

const MAX_BYTES = 64 * 1024 * 1024;
const MAX_BLOCKS = 8_000_000;
export function readNbt(bytes) {
  const view = new DataView(bytes.buffer, bytes.byteOffset, bytes.byteLength);
  const decoder = new TextDecoder('utf-8', {fatal:true});
  let offset = 0, tags = 0;
  const check = n => { if (!Number.isSafeInteger(n) || n < 0 || offset + n > bytes.length) throw new Error('The NBT file is truncated or has an invalid length.'); };
  const u8 = () => {check(1);return view.getUint8(offset++);};
  const read = (size, method) => { check(size);const value = view[method](offset, false);offset += size;return value; };
  const str = () => {const n = read(2,'getUint16');check(n);const s = decoder.decode(bytes.subarray(offset,offset+n));offset += n;return s;};
  const length = (size=1) => {const n=read(4,'getInt32');if(n<0 || n>MAX_BYTES/size)throw new Error('NBT array is too large.');return n;};
  function payload(type, depth=0) {
    if (depth>64 || ++tags>2_000_000) throw new Error('NBT nesting or tag count exceeds the supported limit.');
    switch(type) {
      case 1:return read(1,'getInt8');
      case 2:return read(2,'getInt16');
      case 3:return read(4,'getInt32');
      case 4:return read(8,'getBigInt64');
      case 5:return read(4,'getFloat32');
      case 6:return read(8,'getFloat64');
      case 7:{const n=length();check(n);const b=bytes.subarray(offset,offset+n);offset+=n;return b;}
      case 8:return str();
      case 9:{const t=u8(), n=length();if(n>2_000_000 || (t===0&&n>0))throw new Error('Invalid NBT list.');return Array.from({length:n},()=>payload(t,depth+1));}
      case 10:{const o=Object.create(null);let t;while((t=u8())!==0){const k=str();if(Object.hasOwn(o,k))throw new Error('Duplicate NBT tag.');o[k]=payload(t,depth+1);}return o;}
      case 11:{const n=length(4);check(n*4);return Array.from({length:n},()=>read(4,'getInt32'));}
      case 12:{const n=length(8);check(n*8);return Array.from({length:n},()=>read(8,'getBigUint64'));}
      default:throw new Error(`Unknown NBT tag type ${type}.`);
    }
  }
  if(u8()!==10)throw new Error('Expected a named NBT compound. Legacy numeric-ID schematics are not supported.');
  str();const root=payload(10);
  if(offset!==bytes.length)throw new Error('Unexpected trailing bytes in the NBT file.');
  return root;
}

function volume(size) {
  if(!Array.isArray(size)||size.length!==3||!size.every(n=>Number.isInteger(n)&&n!==0&&Math.abs(n)<=8192))throw new Error('Invalid schematic dimensions.');
  const v=size.reduce((a,b)=>a*Math.abs(b),1);
  if(v>MAX_BLOCKS)throw new Error('Schematic exceeds 8 million blocks. Split it into smaller regions.');
  return v;
}
const IGNORED = new Set(['air','cave_air','void_air','structure_void']);
const ALIASES = {
  redstone_wire:'redstone',redstone_wall_torch:'redstone_torch',wall_torch:'torch',soul_wall_torch:'soul_torch',tripwire:'string',
  wheat:'wheat_seeds',carrots:'carrot',potatoes:'potato',beetroots:'beetroot_seeds',cocoa:'cocoa_beans',sweet_berry_bush:'sweet_berries',
  melon_stem:'melon_seeds',attached_melon_stem:'melon_seeds',pumpkin_stem:'pumpkin_seeds',attached_pumpkin_stem:'pumpkin_seeds',
  bamboo_sapling:'bamboo',kelp_plant:'kelp',cave_vines:'glow_berries',cave_vines_plant:'glow_berries',tall_seagrass:'seagrass',
  potted_bamboo:'bamboo',powder_snow:'powder_snow_bucket'
};
const NO_ITEM = new Set(['piston_head','moving_piston','fire','soul_fire','nether_portal','end_portal','end_gateway','bubble_column','frosted_ice','farmland','dirt_path','water','lava','spawner','trial_spawner','vault','budding_amethyst','infested_stone','infested_cobblestone','infested_stone_bricks','infested_mossy_stone_bricks','infested_cracked_stone_bricks','infested_chiseled_stone_bricks','infested_deepslate']);
export function stateToMaterials(state, registry, warnings) {
  const name = state.Name;
  if(!validId(name))throw new Error('A palette entry has an invalid block ID.');
  const p=state.Properties||{}, key=name.replace(/^minecraft:/,'');
  if(IGNORED.has(key))return [];
  if(p.waterlogged==='true')warnings.add('Waterlogged blocks need water; water is not included in this material count.');
  if(p.half==='upper'||p.part==='head')return [];
  if(name.startsWith('minecraft:')&&NO_ITEM.has(key)){
    warnings.add(`${humanize(name)} needs manual review and is excluded from the item count.`);return [];
  }
  let units=1;
  if(p.type==='double'&&key.endsWith('_slab'))units=2;
  for(const prop of ['candles','pickles','eggs','layers','flower_amount','segment_amount'])if(p[prop]){
    const n=Number(p[prop]);if(!Number.isInteger(n)||n<1||n>8)throw new Error('Invalid block-state material quantity.');units*=n;
  }
  if(name.startsWith('minecraft:potted_')) {
    return [{id:'minecraft:flower_pot',count:1},{id:'minecraft:'+key.slice(7),count:1}];
  }
  let id=registry?.[name];
  if(name.startsWith('minecraft:')){
    const alias=ALIASES[key] || key.replace('_wall_hanging_sign','_hanging_sign').replace('_wall_sign','_sign').replace('_wall_banner','_banner').replace('_wall_head','_head').replace('_wall_skull','_skull').replace('_wall_fan','_fan');
    if(alias!==key)id='minecraft:'+alias;
  }
  if(id===null){warnings.add(`${humanize(name)} has no direct inventory item; check it manually.`);return [];}
  if(!id&&name.startsWith('minecraft:')){
    const alias=ALIASES[key] || key.replace('_wall_hanging_sign','_hanging_sign').replace('_wall_sign','_sign').replace('_wall_banner','_banner').replace('_wall_head','_head').replace('_wall_skull','_skull').replace('_wall_fan','_fan');
    id='minecraft:'+alias;
  }
  if(!id){warnings.add(`Unmapped modded block: ${name}. Connect Minecraft to use its item registry.`);return [];}
  if(registry&&!Object.hasOwn(registry,name)&&!name.startsWith('minecraft:')){warnings.add(`Unknown block: ${name}.`);return [];}
  return [{id,count:units}];
}
function parseBlockName(text) {
  const m=/^([^\[]+)(?:\[([^\]]+)\])?$/.exec(text);
  if(!m)throw new Error('Invalid block palette entry.');
  return {Name:m[1],Properties:Object.fromEntries((m[2]||'').split(',').filter(Boolean).map(s=>s.split('=')))};
}
export function materialsFromNbt(root, registry) {
  const totals=new Map(),warnings=new Set();let blocks=0,regions=0,name=String(root.Metadata?.Name||'Imported schematic');
  function add(state,amount=1) {
    const list=stateToMaterials(state,registry,warnings);
    for(const m of list)totals.set(m.id,count((totals.get(m.id)||0)+m.count*amount));
  }
  function packed(region) {
    const palette=region.BlockStatePalette;
    if(!Array.isArray(palette)||!palette.length||palette.length>1_000_000)throw new Error('Invalid Litematica palette.');
    const n=volume([region.Size?.x,region.Size?.y,region.Size?.z]);
    blocks+=n;if(blocks>MAX_BLOCKS)throw new Error('Combined schematic exceeds 8 million blocks.');
    const bits=Math.max(2,Math.ceil(Math.log2(palette.length))),mask=(1n<<BigInt(bits))-1n;
    const longs=region.BlockStates;
    if(!Array.isArray(longs)||longs.length!==Math.ceil(n*bits/64))throw new Error('Litematica packed block data has an invalid length.');
    const counts=new Map();
    for(let i=0;i<n;i++){
      const bit=i*bits,w=Math.floor(bit/64),shift=bit%64;
      let v=longs[w]>>BigInt(shift);
      if(shift+bits>64)v|=longs[w+1]<<BigInt(64-shift);
      const index=Number(v&mask);
      if(index>=palette.length)throw new Error('Invalid Litematica palette index.');
      counts.set(index,(counts.get(index)||0)+1);
    }
    for(const [index,amount]of counts)add(palette[index],amount);
    if(region.Entities?.length)warnings.add('Entities (including item frames, paintings and armor stands) are not counted.');
    if(region.TileEntities?.length)warnings.add('Block-entity contents, patterns and other extra materials need manual review.');
    regions++;
  }
  if(root.Regions){
    if(![4,5,6,7].includes(root.Version))throw new Error(`Litematica version ${root.Version} is not supported. Re-export with a recent Litematica version.`);
    const bounds=[];
    for(const region of Object.values(root.Regions)){
      packed(region);
      const pos=region.Position||{x:0,y:0,z:0},sz=region.Size;
      const bound=['x','y','z'].map(k=>[Math.min(pos[k],pos[k]+sz[k]-Math.sign(sz[k])),Math.max(pos[k],pos[k]+sz[k]-Math.sign(sz[k]))]);
      if(bounds.some(b=>b.every((range,i)=>range[0]<=bound[i][1]&&range[1]>=bound[i][0])))warnings.add('Schematic regions overlap. Overlapping blocks may be counted twice; review the material list.');
      bounds.push(bound);
    }
  } else if(root.Schematic||root.Palette){
    const s=root.Schematic||root;
    if(![2,3].includes(s.Version))throw new Error('Only Sponge schematic versions 2 and 3 are supported.');
    const n=volume([s.Width&65535,s.Height&65535,s.Length&65535]);blocks=n;regions=1;
    const palette=s.Blocks?.Palette||s.Palette,data=s.Blocks?.Data||s.BlockData;
    if(!palette||!(data instanceof Uint8Array))throw new Error('Missing Sponge palette or block data.');
    const states=new Map(Object.entries(palette).map(([k,v])=>[v,parseBlockName(k)]));
    if(states.size!==Object.keys(palette).length)throw new Error('Duplicate Sponge palette index.');
    let offset=0;
    const counts=new Map();
    for(let i=0;i<n;i++){
      let id=0,shift=0,b;
      do{
        if(offset>=data.length||shift>28)throw new Error('Truncated or invalid Sponge block data.');
        b=data[offset++];id+=(b&127)*2**shift;shift+=7;
      }while(b&128);
      if(!states.has(id))throw new Error('Invalid Sponge palette index.');
      counts.set(id,(counts.get(id)||0)+1);
    }
    if(offset!==data.length)throw new Error('Sponge block data does not match its dimensions.');
    for(const [index,amount]of counts)add(states.get(index),amount);
    if(s.Entities?.length)warnings.add('Entities are not counted.');
    if((s.Blocks?.BlockEntities||s.BlockEntities)?.length)warnings.add('Block-entity contents and extra materials need manual review.');
    name=String(s.Metadata?.Name||name);
  } else if(root.palette&&root.blocks){
    if(root.palettes)throw new Error('Randomized structure palettes need manual material selection.');
    volume(root.size);regions=1;const seen=new Set();
    for(const block of root.blocks){
      if(!Number.isInteger(block.state)||!root.palette[block.state])throw new Error('Invalid structure palette index.');
      if(!Array.isArray(block.pos)||block.pos.length!==3||!block.pos.every((n,i)=>Number.isInteger(n)&&n>=0&&n<root.size[i]))throw new Error('Invalid structure block position.');
      const key=block.pos.join(',');if(seen.has(key))throw new Error('Duplicate structure block position.');seen.add(key);
      add(root.palette[block.state]);blocks++;
      if(block.nbt)warnings.add('Block-entity contents and extra materials need manual review.');
    }
    if(root.entities?.length)warnings.add('Entities are not counted.');
  } else throw new Error('Unrecognized schematic. Use .litematic, Sponge .schem v2/v3, or a vanilla .nbt structure.');
  if(!regions||!totals.size)throw new Error('No supported building materials were found in this schematic.');
  if(!registry)warnings.add('Using vanilla block-to-item estimates. Open this file in the mod dashboard for registry-backed item matching.');
  return {name,materials:[...totals].map(([id,count])=>({id,count,name:humanize(id)})),warnings:[...warnings],blocks,regions};
}

export function parseCsv(text) {
  let rows=[],row=[],cell='',quoted=false;
  for(let i=0;i<text.length;i++){
    const c=text[i];
    if(c==='"'){
      if(quoted&&text[i+1]==='"'){cell+='"';i++;}else quoted=!quoted;
    }else if(!quoted&&(c===','||c==='\n'||c==='\r')){
      row.push(cell.trim());cell='';
      if(c!==','){if(row.some(Boolean))rows.push(row);row=[];if(c==='\r'&&text[i+1]==='\n')i++;}
    }else cell+=c;
  }
  if(quoted)throw new Error('Unclosed quotation mark in CSV.');
  row.push(cell.trim());if(row.some(Boolean))rows.push(row);
  return rows;
}
export function parseMaterialList(text,filename) {
  let list,name=filename.replace(/\.[^.]+$/,'');
  if(filename.toLowerCase().endsWith('.csv')){
    const rows=parseCsv(text.replace(/^\uFEFF/,''));
    const headers=rows.shift()?.map(x=>x.toLowerCase().trim())||[];
    const idIndex=headers.findIndex(x=>['id','item id','item_id'].includes(x));
    const countIndex=headers.findIndex(x=>['count','total','required'].includes(x));
    if(idIndex<0||countIndex<0)throw new Error('CSV needs an "Item ID" and a "Total" (or "count") column. Export an ID-based material list.');
    list=rows.map(r=>({id:r[idIndex],count:Number(r[countIndex])}));
  }else{
    const data=JSON.parse(text);list=Array.isArray(data)?data:data.materials;name=String(data.name||name);
  }
  if(!Array.isArray(list)||!list.length||list.length>20_000)throw new Error('Provide between 1 and 20,000 material rows.');
  const totals=new Map();
  for(const m of list){
    if(!m||!validId(m.id))throw new Error('Materials need namespaced item IDs, such as minecraft:stone.');
    totals.set(m.id,count((totals.get(m.id)||0)+count(m.count)));
  }
  const materials=[...totals].filter(([,n])=>n>0).map(([id,count])=>({id,count,name:humanize(id)}));
  if(!materials.length)throw new Error('This material list is empty.');
  return {name,materials,warnings:[],regions:0,blocks:materials.reduce((a,b)=>a+b.count,0)};
}
export async function decodeSchematic(buffer,filename,registry) {
  if(buffer.byteLength>25*1024*1024)throw new Error('Maximum upload size is 25 MB.');
  if(/\.(json|csv)$/i.test(filename))return parseMaterialList(new TextDecoder().decode(buffer),filename);
  let bytes=new Uint8Array(buffer);
  if(bytes[0]===0x1f&&bytes[1]===0x8b){
    const reader=new Blob([bytes]).stream().pipeThrough(new DecompressionStream('gzip')).getReader();
    const chunks=[];let size=0;
    while(true){const {done,value}=await reader.read();if(done)break;size+=value.length;if(size>MAX_BYTES){await reader.cancel();throw new Error('Expanded schematic exceeds 64 MB.');}chunks.push(value);}
    bytes=new Uint8Array(size);let pos=0;for(const chunk of chunks){bytes.set(chunk,pos);pos+=chunk.length;}
  }
  if(bytes.length>MAX_BYTES)throw new Error('Expanded schematic exceeds 64 MB.');
  return {...materialsFromNbt(readNbt(bytes),registry),filename};
}
