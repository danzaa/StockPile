const now=Date.now();
const specs=[
  ['Stone & earth',[128,64,-230],{'cobblestone':3456,'stone':1536,'stone_bricks':832,'deepslate':960}],
  ['Timber I',[130,64,-230],{'spruce_log':832,'oak_log':320,'spruce_planks':256,'oak_planks':640,'blue_shulker_box':1}],
  ['Timber II',[132,64,-230],{'spruce_stairs':96,'spruce_slab':48,'spruce_trapdoor':18,'spruce_fence':32}],
  ['Metals & minerals',[128,64,-232],{'iron_ingot':384,'gold_ingot':96,'diamond':24,'copper_ingot':512}],
  ['Redstone supplies',[130,64,-232],{'redstone':112,'redstone_torch':24,'repeater':12,'hopper':8}],
  ['Glass & lighting',[132,64,-232],{'glass':192,'glass_pane':32,'lantern':12,'torch':128}],
  ['Sand & gravel',[128,64,-234],{'sand':576,'gravel':448,'dirt':1728}],
  ['The greenhouse',[130,64,-234],{'wheat':320,'carrot':128,'bone_meal':64,'oak_leaves':192}],
  ['Building details',[132,64,-234],{'brick':64,'bricks':128,'flower_pot':8,'chain':16}],
  ['Roofing',[128,65,-230],{'deepslate_tiles':384,'deepslate_tile_stairs':128,'deepslate_tile_slab':64}],
  ['Odds & ends',[130,65,-230],{'chest':24,'barrel':12,'white_wool':128,'leather':32}],
  ['Nether supplies',[32,67,18],{'quartz':160,'glowstone':64,'blackstone':384,'soul_sand':48}]
];
export const demoSnapshot={schemaVersion:1,capturedAt:now,connected:false,worlds:[{id:'demo-survival',name:"Danzaa’s survival",containers:specs.map(([name,position,items],i)=>({id:'demo-'+i,name,type:[5,11].includes(i)?'barrel':'chest',dimension:i===11?'minecraft:the_nether':'minecraft:overworld',position,lastSeen:now-(i%4)*30*60_000,packedScanComplete:true,packedItems:i===1?[{id:'minecraft:spruce_planks',count:128,maxStack:64,path:['Blue Shulker Box (slot 20)']},{id:'minecraft:glass',count:64,maxStack:64,path:['Blue Shulker Box (slot 20)']}]:[],items:Object.entries(items).map(([id,count])=>({id:'minecraft:'+id,count,maxStack:id.endsWith('shulker_box')?1:64}))}))}]};
export const demoTargets={'minecraft:spruce_log':256,'minecraft:oak_log':256,'minecraft:stone_bricks':512,'minecraft:iron_ingot':128,'minecraft:diamond':32,'minecraft:redstone':256,'minecraft:hopper':32,'minecraft:lantern':32,'minecraft:glass':64,'minecraft:cobblestone':1024,'minecraft:spruce_stairs':128};
export const demoProjects=[{id:'demo-house',name:'Spruce starter house',filename:'spruce-starter-house.json',sample:true,regions:1,blocks:1152,warnings:[],materials:[['spruce_log',192],['spruce_planks',384],['cobblestone',256],['spruce_stairs',160],['spruce_slab',96],['glass_pane',48],['lantern',16]].map(([id,count])=>({id:'minecraft:'+id,count}))},{id:'demo-bridge',name:'Stone bridge',filename:'stone-bridge.json',sample:true,regions:1,blocks:480,warnings:[],materials:[['stone_bricks',320],['spruce_fence',32],['stone_brick_stairs',96],['lantern',8],['chain',24]].map(([id,count])=>({id:'minecraft:'+id,count}))}];

demoSnapshot.worlds[0].containers.push(
  {id:'ender:example-player',name:'Ender Chest',type:'ender_chest',dimension:'minecraft:overworld',position:[134,64,-230],lastSeen:now,packedScanComplete:true,items:[{id:'minecraft:diamond',count:32,maxStack:64},{id:'minecraft:ender_pearl',count:12,maxStack:16},{id:'minecraft:blue_shulker_box',count:1,maxStack:1}],packedItems:[{id:'minecraft:iron_ingot',count:128,maxStack:64,path:['Travel supplies (slot 3)']}]},
  {id:'demo-placed-shulker',name:'Stone build supplies',type:'blue_shulker_box',dimension:'minecraft:overworld',position:[136,64,-230],lastSeen:now,packedScanComplete:true,items:[{id:'minecraft:stone_bricks',count:128,maxStack:64},{id:'minecraft:stone_brick_stairs',count:64,maxStack:64}],packedItems:[]}
);
