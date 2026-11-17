import {gzipSync} from 'node:zlib';
export const tag=(type,value)=>({type,value});
const n=(bytes,method,value)=>{const b=Buffer.alloc(bytes);b[method](value);return b;};
const str=s=>{const b=Buffer.from(s);return Buffer.concat([n(2,'writeUInt16BE',b.length),b]);};
function payload({type,value}){
  if(type===1)return n(1,'writeInt8',value);
  if(type===2)return n(2,'writeInt16BE',value);
  if(type===3)return n(4,'writeInt32BE',value);
  if(type===4)return n(8,'writeBigInt64BE',BigInt(value));
  if(type===7)return Buffer.concat([n(4,'writeInt32BE',value.length),Buffer.from(value)]);
  if(type===8)return str(value);
  if(type===9){const [subtype,list]=value;return Buffer.concat([Buffer.from([subtype]),n(4,'writeInt32BE',list.length),...list.map(v=>payload(tag(subtype,v)))]);}
  if(type===10)return Buffer.concat([...Object.entries(value).map(([k,t])=>Buffer.concat([Buffer.from([t.type]),str(k),payload(t)])),Buffer.from([0])]);
  if(type===11)return Buffer.concat([n(4,'writeInt32BE',value.length),...value.map(v=>n(4,'writeInt32BE',v))]);
  if(type===12)return Buffer.concat([n(4,'writeInt32BE',value.length),...value.map(v=>n(8,'writeBigUInt64BE',BigInt.asUintN(64,v)))]);
  throw new Error('Unsupported test tag');
}
export function encodeNbt(root,compressed=false){const raw=Buffer.concat([Buffer.from([10]),str(''),payload(tag(10,root))]);return compressed?gzipSync(raw):raw;}
const s=v=>tag(8,v),i=v=>tag(3,v),compound=v=>tag(10,v),list=(t,v)=>tag(9,[t,v]);
export function fixture(){
  const palette=[['air'],['stone_bricks'],['spruce_log'],['lantern'],['spruce_slab',{type:'double'}],['spruce_door',{half:'lower'}],['spruce_door',{half:'upper'}],['wall_torch']].map(([name,props])=>({Name:s('minecraft:'+name),...(props?{Properties:compound(Object.fromEntries(Object.entries(props).map(([k,v])=>[k,s(v)])))}:{})}));
  const indices=[...Array(10).fill(1),...Array(6).fill(2),3,3,4,4,4,5,6,7,7,...Array(11).fill(0)];
  let stream=0n;for(let index=indices.length-1;index>=0;index--)stream=(stream<<3n)|BigInt(indices[index]);
  const longs=Array.from({length:Math.ceil(indices.length*3/64)},(_,idx)=>(stream>>BigInt(idx*64))&((1n<<64n)-1n));
  return {Version:i(6),MinecraftDataVersion:i(4750),Metadata:compound({Name:s('Tiny workshop'),Author:s('Stockpile example')}),Regions:compound({Workshop:compound({Size:compound({x:i(4),y:i(3),z:i(3)}),Position:compound({x:i(0),y:i(0),z:i(0)}),BlockStatePalette:list(10,palette),BlockStates:tag(12,longs)})})};
}
