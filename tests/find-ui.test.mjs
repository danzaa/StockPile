import test from 'node:test';
import assert from 'node:assert/strict';

// Exercise the actual app's render and event handlers with small DOM/network ports.
// This is a flow regression, not a substitute for browser layout or in-game QA.
const snapshot={schemaVersion:1,connected:true,worlds:[{id:'world-a',name:'A',containers:[{id:'chest-a',name:'Chest <Main>',type:'chest',dimension:'minecraft:overworld',position:[1,64,2],lastSeen:1,items:[{id:'minecraft:stone',name:'Stone',count:64}]}]}]};
function environment(t,local,fixture=snapshot){
  const keys=['document','window','location','localStorage','sessionStorage','history','fetch','setInterval','setTimeout','clearTimeout','navigator'];
  const saved=Object.fromEntries(keys.map(k=>[k,Object.getOwnPropertyDescriptor(globalThis,k)]));
  t.after(()=>{for(const k of keys)if(saved[k])Object.defineProperty(globalThis,k,saved[k]);else delete globalThis[k];});
  const handlers={},requests=[];
  const node=()=>({innerHTML:'',textContent:'',open:false,classList:{add(){},remove(){}},addEventListener(){},showModal(){this.open=true;},close(){this.open=false;}});
  const app=node(),modal=node(),toast=node();
  const root = {};
  globalThis.document={documentElement:root,activeElement:null,querySelector:s=>({'#app':app,'#modal':modal,'#toast':toast}[s]||null),addEventListener:(name,fn)=>{handlers[name]=fn;}};
  globalThis.window={scrollTo(){}};
  globalThis.location={hostname:local?'127.0.0.1':'example.test',port:local?'19482':'',hash:local?'#token=test-token':'',pathname:'/',search:''};
  const values=new Map(local?[]:[['stockpile-inventory',JSON.stringify(fixture)]]);
  values.set('stockpile-projects',JSON.stringify([{id:'road',name:'Road',materials:[{id:'minecraft:stone',count:10}]}]));
  globalThis.localStorage={getItem:k=>values.get(k)||null,setItem:(k,v)=>values.set(k,v),removeItem:k=>values.delete(k)};
  globalThis.sessionStorage={getItem:()=>null,setItem(){}};
  globalThis.history={replaceState(){}};
  let poll;
  globalThis.setInterval=fn=>{poll=fn;return 0;};globalThis.setTimeout=()=>0;globalThis.clearTimeout=()=>{};
  let status=200,latest=fixture;
  const responses = new Map();
  globalThis.fetch=async(path,options)=>{
    requests.push({path,options});
    if(path==='/api/exclusions'){
      const q=JSON.parse(options.body),w=latest.worlds.find(w=>w.id===q.worldId),c=w.containers.find(c=>c.id===q.containerId),keys=c.members?.length?c.members:[c.id],set=new Set(w.excludedLocations||[]);
      for(const key of keys)if(q.excluded)set.add(key);else set.delete(key);w.excludedLocations=[...set];w.excludedContainerIds=w.containers.filter(c=>(c.members?.length?c.members:[c.id]).some(key=>set.has(key))).map(c=>c.id);
    }
    const data=responses.has(path)?responses.get(path):path==='/api/snapshot'?latest:path==='/api/registry'?{}:{message:status===200?'Remembered chest highlighted':'Join that world first'};
    return {ok:status===200||path!=='/api/find',status,json:async()=>data};
  };
  const click=async dataset=>{const el={dataset,disabled:false};await handlers.click({target:{closest:()=>el}});return el;};
  return {app,modal,toast,requests,click,poll:async value=>{latest=value;await poll();},setStatus:n=>{status=n;},handlers,responses,root,values};
}
test('connected item details send an authenticated find and display game errors',async t=>{
  const env=environment(t,true);
  await import('../dist/app.js?find-flow-local');
  await new Promise(resolve=>setImmediate(resolve));
  await env.click({action:'details',id:'minecraft:stone'});
  assert.match(env.modal.innerHTML,/Highlight chest/);
  assert.match(env.modal.innerHTML,/Chest &lt;Main&gt;/);
  assert.match(env.modal.innerHTML,/data-world="world-a" data-packed="true"/);
  assert.doesNotMatch(env.modal.innerHTML,/data-action="find-game"[^>]*disabled/);
  const button=await env.click({action:'find-game',id:'minecraft:stone',container:'chest-a',world:'world-a',packed:'false'});
  const sent=env.requests.find(r=>r.path==='/api/find');
  assert.equal(sent.options.method,'POST');
  assert.equal(sent.options.headers['X-Stockpile-Token'],'test-token');
  assert.deepEqual(JSON.parse(sent.options.body),{worldId:'world-a',containerId:'chest-a',itemId:'minecraft:stone',includePacked:false});
  assert.equal(button.disabled,false);
  assert.equal(env.toast.textContent,'Remembered chest highlighted');
  env.setStatus(409);
  await env.click({action:'find-game',id:'minecraft:stone',container:'chest-a',world:'world-a',packed:'true'});
  assert.equal(env.toast.textContent,'Join that world first');
});
test('imported snapshots retain coordinates but disable in-game actions',async t=>{
  const env=environment(t,false);
  await import('../dist/app.js?find-flow-import');
  await env.click({action:'details',id:'minecraft:stone'});
  assert.match(env.modal.innerHTML,/data-action="find-game"[^>]*disabled/);
  assert.match(env.modal.innerHTML,/Connect the local Minecraft dashboard/);
  assert.match(env.modal.innerHTML,/1 \/ 64 \/ 2/);
  await env.click({action:'find-game',id:'minecraft:stone',container:'chest-a',world:'world-a',packed:'true'});
  assert.equal(env.requests.length,0);
});

test('planner reserves its real physical plan and history/sharing controls are reachable',async t=>{
  const env=environment(t,true);
  await import('../dist/app.js?all-features-local');await new Promise(resolve=>setImmediate(resolve));
  await env.click({action:'nav',view:'planner'});
  assert.match(env.app.innerHTML,/Build reservations/);assert.match(env.app.innerHTML,/Reserved elsewhere/);assert.match(env.app.innerHTML,/Smelting &amp; fuel/);
  await env.click({action:'reserve',id:'road'});
  const sent=env.requests.find(r=>r.path==='/api/reservations');assert.ok(sent);
  const q=JSON.parse(sent.options.body);assert.equal(q.projectId,'road');assert.equal(q.revision,0);assert.deepEqual(q.claims,[{containerId:'chest-a',itemId:'minecraft:stone',packed:false,count:10}]);
  await env.click({action:'nav',view:'history'});assert.match(env.app.innerHTML,/Schematic transfers appear in the log and are excluded from the totals/);
  await env.click({action:'sharing'});assert.match(env.modal.innerHTML,/Personal Ender Chests are never shared/);assert.match(env.modal.innerHTML,/stockpileteam share/);
});

test('an imported team backup edits only its local claims using the exported viewer identity',async t=>{
  const fixture=structuredClone(snapshot),world=fixture.worlds[0];world.shared=true;world.live=false;world.viewer='exported-viewer';
  world.planning={revision:0,history:[],reservations:{road:{projectId:'road',name:'Road',owner:'exported-viewer',status:'active',updatedAt:1,claims:[{containerId:'chest-a',itemId:'minecraft:stone',packed:false,count:10}]}}};
  const env=environment(t,false,fixture);let locks=0;
  Object.defineProperty(globalThis,'navigator',{configurable:true,value:{locks:{request:async(key,fn)=>{locks++;return fn();}}}});
  await import('../dist/app.js?imported-team-planning');
  await env.click({action:'nav',view:'planner'});
  assert.match(env.app.innerHTML,/Update this reservation/);
  await env.click({action:'release-reservation',id:'road'});
  assert.equal(locks,1);assert.equal(env.requests.length,0);assert.equal(env.toast.textContent,'Reservation released.');
  await env.click({action:'reserve',id:'road'});assert.equal(locks,2);assert.equal(env.requests.length,0);
  assert.match(env.app.innerHTML,/10 items reserved/);
});

test('a game world switch selects its empty inventory and closes old item details',async t=>{
  const fixture=structuredClone(snapshot);fixture.activeWorldId='world-a';fixture.worldSession='first';
  const env=environment(t,true,fixture);await import('../dist/app.js?world-switch-flow');await new Promise(resolve=>setImmediate(resolve));
  await env.click({action:'details',id:'minecraft:stone'});assert.equal(env.modal.open,true);
  const next=structuredClone(fixture);next.activeWorldId='world-b';next.worldSession='second';next.worlds.push({id:'world-b',name:'New empty world',containers:[]});
  await env.poll(next);
  assert.match(env.app.innerHTML,/data-kind="world" data-value="world-b" aria-pressed="true"/);
  assert.doesNotMatch(env.app.innerHTML,/Chest &lt;Main&gt;/);assert.equal(env.modal.open,false);
  await env.click({action:'choose',kind:'world',value:'world-a'});
  assert.match(env.app.innerHTML,/Viewing saved inventory for A/);
  await env.poll(next);assert.match(env.app.innerHTML,/data-kind="world" data-value="world-a" aria-pressed="true"/);
  await env.poll({...next,worldSession:'rejoined'});assert.match(env.app.innerHTML,/data-kind="world" data-value="world-b" aria-pressed="true"/);
});

test('repeated packed rows are summed in item details with a visible chest highlight action',async t=>{
  const fixture=structuredClone(snapshot);fixture.worlds[0].containers[0].packedItems=Array.from({length:27},()=>({id:'minecraft:diamond',name:'Diamond',count:64,path:['Shulker Box (slot 22)']}));
  const env=environment(t,true,fixture);await import('../dist/app.js?grouped-shulker-details');await new Promise(resolve=>setImmediate(resolve));
  await env.click({action:'details',id:'minecraft:diamond'});
  assert.match(env.modal.innerHTML,/<details class="packed-dropdown"><summary>1,728 packed · 1 location<\/summary>/);
  assert.equal((env.modal.innerHTML.match(/1,728 inside Shulker Box/g)||[]).length,1);
  assert.match(env.modal.innerHTML,/Highlight chest/);
});

test('a new automatic capture appears on the map without a manual capture request', async t => {
  const fixture = {...structuredClone(snapshot), activeWorldId: 'world-a', activeDimension: 'minecraft:overworld', worldSession: 'one', terrainRevision: 0};
  const env = environment(t, true, fixture);
  await import('../dist/app.js?automatic-terrain');
  await new Promise(resolve => setImmediate(resolve));
  await env.click({action: 'nav', view: 'map'});
  env.responses.set('/api/map/terrain/recent', [{
    worldId: 'world-a', dimension: 'minecraft:overworld', x: 0, z: -16, size: 208, capturedAt: 3, png: 'iVBORw0KGgo='
  }]);
  await env.poll({...fixture, terrainRevision: 1});
  assert.match(env.app.innerHTML, /class="map-terrain"/);
  await env.poll({...fixture, terrainRevision: 1});
  assert.equal(env.requests.filter(({path}) => path === '/api/map/terrain/recent').length, 1);
  assert.equal(env.requests.some(({path}) => path === '/api/map/terrain'), false);
});

test('the schematic chest sends only confirmed materials and selected sources', async t => {
  const fixture = {...structuredClone(snapshot), activeWorldId: 'world-a', activeDimension: 'minecraft:overworld',
    worldSession: 'one', schematicFunnelAvailable: true};
  const world = fixture.worlds[0];
  world.containers.push({...world.containers[0], id: 'destination', position: [3,64,3], items: []});
  world.containers.push({...world.containers[0], id: 'unselected', position: [7,64,7]});
  const env = environment(t, true, fixture);
  env.responses.set('/api/schematic-chest', {moved: 9, skipped: 0, message: 'Some materials are still needed.', missing: [{id:'minecraft:stone', count:1}]});
  await import('../dist/app.js?schematic-chest-flow');
  await new Promise(resolve => setImmediate(resolve));
  await env.click({action: 'nav', view: 'map'});
  await env.click({action: 'map-toggle', id: 'chest-a'});
  await env.click({action: 'planner'});
  assert.match(env.app.innerHTML, /Schematic chest/);
  await env.handlers.submit({preventDefault(){}, target:{id: 'schematic-chest-form', elements:{destination:{value:'destination'}}}});
  assert.match(env.modal.innerHTML, /Move materials for Road/);
  assert.equal(env.requests.some(({path}) => path === '/api/schematic-chest'), false);
  await env.handlers.submit({preventDefault(){}, target:{id: 'confirm-schematic-fill', querySelector: () => ({})}});
  const sent = env.requests.find(({path}) => path === '/api/schematic-chest');
  assert.deepEqual(JSON.parse(sent.options.body), {worldId:'world-a', worldSession:'one', destination:'destination',
    sources:['chest-a'], materials:[{id:'minecraft:stone', count:10}]});
  assert.match(env.modal.innerHTML, /9 items moved/);
  assert.match(env.modal.innerHTML, /1 Stone/);
});

test('an in-game schematic chest choice supersedes a previous browser selection', async t => {
  const fixture = {...structuredClone(snapshot), activeWorldId:'world-a', worldSession:'one',
    activeDimension:'minecraft:overworld', schematicFunnelAvailable:true};
  fixture.worlds[0].containers.push({...fixture.worlds[0].containers[0], id:'target', name:'Build chest', position:[3,64,5]});
  const env = environment(t,true,fixture);
  env.values.set('stockpile-schematic-chest:world-a:road', JSON.stringify('chest-a'));
  await import('../dist/app.js?schematic-chest-picked-in-game');
  await new Promise(resolve=>setImmediate(resolve));
  await env.click({action:'nav',view:'planner'});
  assert.match(env.app.innerHTML, /option value="chest-a" selected/);
  const next = structuredClone(fixture);
  next.worlds[0].schematicChest = 'target';
  next.worlds[0].schematicChestMark = 'in-game-choice';
  await env.poll(next);
  assert.match(env.app.innerHTML, /option value="target" selected/);
  assert.doesNotMatch(env.app.innerHTML, /option value="chest-a" selected/);
  env.handlers.change({target:{id:'schematic-destination',value:'chest-a',form:{querySelector:()=>({})}}});
  await env.click({action:'nav',view:'planner'});
  assert.match(env.app.innerHTML, /option value="chest-a" selected/);
});
test('map selection limits schematic reservations, and excluded chests stay available for management',async t=>{
  const fixture=structuredClone(snapshot),w=fixture.worlds[0];w.containers.push({...structuredClone(w.containers[0]),id:'chest-b',name:'Build supplies',position:[50,64,50],items:[{id:'minecraft:stone',count:32}]});
  w.containers.push({...structuredClone(w.containers[0]),id:'empty',name:'Empty chest',position:[25,64,25],items:[]});
  const env=environment(t,true,fixture);await import('../dist/app.js?storage-map-flow');await new Promise(resolve=>setImmediate(resolve));
  await env.click({action:'nav',view:'map'});assert.match(env.app.innerHTML,/id="storage-map"/);assert.match(env.app.innerHTML,/Use selection in Build planner/);
  await env.click({action:'map-toggle',id:'chest-b'});assert.match(env.app.innerHTML,/1 selected container/);
  await env.click({action:'nav',view:'planner'});await env.click({action:'reserve',id:'road'});
  const reserve=JSON.parse(env.requests.find(r=>r.path==='/api/reservations').options.body);assert.deepEqual(reserve.claims,[{containerId:'chest-b',itemId:'minecraft:stone',packed:false,count:10}]);
  await env.click({action:'nav',view:'map'});await env.click({action:'exclude-chest',id:'chest-b',excluded:'true'});
  assert.match(env.app.innerHTML,/Excluded from counts and planning/);assert.match(env.app.innerHTML,/Include chest/);assert.match(env.app.innerHTML,/0 selected container/);
  await env.click({action:'exclude-chest',id:'chest-b',excluded:'false'});assert.match(env.app.innerHTML,/1 selected container/);
  await env.click({action:'highlight-chest',id:'empty',world:'world-a'});
  const find=JSON.parse(env.requests.find(r=>r.path==='/api/find').options.body);assert.equal(find.containerId,'empty');assert.equal(find.itemId,null);
});

test('map-selected chests in another dimension supply the planner without an old dimension filter hiding them',async t=>{
 const fixture=structuredClone(snapshot),w=fixture.worlds[0];w.containers.push({...structuredClone(w.containers[0]),id:'nether-chest',dimension:'minecraft:the_nether',position:[1,64,2]});
 const env=environment(t,true,fixture);await import('../dist/app.js?map-dimension-selection');await new Promise(resolve=>setImmediate(resolve));
 env.handlers.change({target:{id:'dimension',value:'minecraft:overworld'}});await env.click({action:'nav',view:'map'});
 env.handlers.change({target:{id:'map-dimension',value:'minecraft:the_nether'}});await env.click({action:'map-toggle',id:'nether-chest'});await env.click({action:'planner'});await env.click({action:'reserve',id:'road'});
 const sent=JSON.parse(env.requests.find(r=>r.path==='/api/reservations').options.body);assert.equal(sent.claims[0].containerId,'nether-chest');
});

test('the language dropdown persists a selection and switches Arabic to right-to-left', async t => {
  const env = environment(t, false);
  await import('../dist/app.js?local-language');
  for (const code of ['en', 'hi', 'es', 'ar', 'fr', 'fil', 'mn']) assert.match(env.app.innerHTML, new RegExp('data-value="' + code + '"'));
  assert.doesNotMatch(env.app.innerHTML, /data-kind="language" data-value="zh"/);
  await env.click({action:'choose', kind:'language', value:'ar'});
  assert.equal(env.root.dir, 'rtl');
  assert.equal(env.root.lang, 'ar');
  assert.equal(JSON.parse(env.values.get('stockpile-language')), 'ar');
  await env.click({action:'nav', view:'history'});
  assert.match(env.app.innerHTML, /سجل المخزون/);
  await env.click({action:'choose', kind:'history', value:'30'});
  assert.match(env.app.innerHTML, /data-kind="history" data-value="30" aria-pressed="true"/);
  await env.click({action:'choose', kind:'language', value:'es'});
  assert.equal(env.root.dir, 'ltr');
  assert.match(env.app.innerHTML, /Historial de existencias/);
  await env.click({action:'choose', kind:'language', value:'fil'});
  assert.match(env.app.innerHTML, /Kasaysayan ng stock/);
  await env.click({action:'choose', kind:'language', value:'mn'});
  assert.equal(env.root.lang, 'mn');
  assert.match(env.app.innerHTML, /Нөөцийн түүх/);
});

test('shared storage explains the detected server and administrator permission', async t => {
  const fixture = {...structuredClone(snapshot), teamConnection:'server'};
  const env = environment(t, true, fixture);
  await import('../dist/app.js?team-diagnostics');
  await new Promise(resolve => setImmediate(resolve));
  await env.click({action:'sharing'});
  assert.match(env.modal.innerHTML, /Server support detected/);
  assert.match(env.modal.innerHTML, /Client only/);
  assert.match(env.modal.innerHTML, /Client and Fabric server/);
  assert.match(env.modal.innerHTML, /account with admin command permission/);
  assert.match(env.modal.innerHTML, /\/stockpileteam share builders 100 64 -25/);
  await env.poll({...fixture, teamConnection:'client-only'});
  await env.click({action:'sharing'});
  assert.match(env.modal.innerHTML, /Client-only connection/);
});

test('material header menus sort the visible rows and keep the missing-materials filter', async t => {
  const env = environment(t, true);
  env.values.set('stockpile-projects', JSON.stringify([{id:'road',name:'Road',materials:[
    {id:'minecraft:stone',count:10}, {id:'minecraft:diamond',count:25}, {id:'minecraft:oak_planks',count:5}
  ]}]));
  await import('../dist/app.js?material-header-menus');
  await new Promise(resolve => setImmediate(resolve));
  await env.click({action:'nav',view:'planner'});
  const visible = () => [...env.app.innerHTML.matchAll(/class="item-button" data-action="details" data-id="minecraft:([^"]+)"/g)]
    .map(match=>match[1]);
  assert.match(env.app.innerHTML, /Build sources/);
  assert.match(env.app.innerHTML, /All included storage/);
  await env.click({action:'choose',kind:'material-needed',value:'descending'});
  assert.deepEqual(visible(), ['diamond','stone','oak_planks']);
  await env.click({action:'choose',kind:'material-physical',value:'descending'});
  assert.equal(visible()[0],'stone');
  env.handlers.change({target:{id:'missing-only',checked:true,matches:()=>false}});
  await env.click({action:'choose',kind:'material-name',value:'ascending'});
  assert.deepEqual(visible(), ['diamond','oak_planks']);
  assert.match(env.app.innerHTML, /aria-sort="ascending"/);
});

test('material checkmarks track the gathered quantity for each build and world across reloads', async t => {
  const fixture = structuredClone(snapshot);
  fixture.worlds.push({id:'world-b',name:'B',containers:[]});
  const env = environment(t,false,fixture);
  env.values.set('stockpile-projects',JSON.stringify([
    {id:'road',name:'Road',materials:[{id:'minecraft:stone',count:10},{id:'minecraft:diamond',count:5}]},
    {id:'bridge',name:'Bridge',materials:[{id:'minecraft:diamond',count:5}]}
  ]));
  const openPlanner = () => env.click({action:'nav',view:'planner'});
  const diamondChecked = () => /data-material-check="minecraft:diamond"[^>]*\bchecked\b/.test(env.app.innerHTML);
  const checkDiamond = checked => env.handlers.change({target:{dataset:{materialCheck:'minecraft:diamond'},checked}});
  const copies = value => env.handlers.change({target:{id:'copies',value}});

  await import('../dist/app.js?materials-checklist');
  await openPlanner();
  assert.match(env.app.innerHTML,/0 of 2 checked/);
  assert.equal(diamondChecked(),false);
  checkDiamond(true);
  assert.equal(diamondChecked(),true);
  assert.match(env.app.innerHTML,/1 of 2 checked/);
  assert.match(env.app.innerHTML,/<td class="number material-missing">5<\/td>/);
  copies('2');
  assert.equal(diamondChecked(),false);
  copies('1');
  assert.equal(diamondChecked(),true);
  await env.click({action:'project',id:'bridge'});
  assert.equal(diamondChecked(),false);
  await env.click({action:'project',id:'road'});
  await env.click({action:'choose',kind:'world',value:'world-b'});
  assert.equal(diamondChecked(),false);
  await env.click({action:'choose',kind:'world',value:'world-a'});
  assert.equal(diamondChecked(),true);

  await import('../dist/app.js?materials-checklist-reloaded');
  await openPlanner();
  assert.equal(diamondChecked(),true);
  checkDiamond(false);
  assert.match(env.app.innerHTML,/0 of 2 checked/);
  assert.equal(diamondChecked(),false);
});

test('switching off the schematic chest removes its fill controls and saves the preference', async t => {
  const fixture = {...structuredClone(snapshot), activeWorldId:'world-a', schematicFunnelAvailable:true, schematicChestEnabled:true, worldSession:'session-a'};
  const env = environment(t, true, fixture);
  await import('../dist/app.js?schematic-off');
  await new Promise(resolve => setImmediate(resolve));
  await env.click({action:'nav',view:'planner'});
  assert.match(env.app.innerHTML, /id="schematic-chest-form"/);
  env.responses.set('/api/schematic-chest/settings', {enabled:false});
  env.handlers.change({target:{id:'schematic-chest-enabled',checked:false}});
  await new Promise(resolve => setImmediate(resolve));
  assert.deepEqual(JSON.parse(env.requests.find(r=>r.path==='/api/schematic-chest/settings').options.body), {enabled:false});
  assert.match(env.app.innerHTML, /Schematic chest is disabled/);
  assert.doesNotMatch(env.app.innerHTML, /id="schematic-chest-form"/);
});

test('auto-clear is on by default and independent of the schematic chest feature', async t => {
  const fixture = {...structuredClone(snapshot), activeWorldId:'world-a', schematicFunnelAvailable:true, worldSession:'one'};
  const env = environment(t, true, fixture);
  await import('../dist/app.js?schematic-auto-clear-setting');
  await new Promise(resolve => setImmediate(resolve));
  await env.click({action:'nav',view:'planner'});
  assert.match(env.app.innerHTML, /id="schematic-chest-auto-clear"[^>]*checked/);
  env.responses.set('/api/schematic-chest/settings', {enabled:true, autoClear:false});
  env.handlers.change({target:{id:'schematic-chest-auto-clear',checked:false}});
  await new Promise(resolve => setImmediate(resolve));
  assert.deepEqual(JSON.parse(env.requests.find(r => r.path === '/api/schematic-chest/settings').options.body), {autoClear:false});
  assert.doesNotMatch(env.app.innerHTML, /id="schematic-chest-auto-clear"[^>]*checked/);
  assert.match(env.app.innerHTML, /id="schematic-chest-enabled"[^>]*checked/);
  assert.match(env.app.innerHTML, /id="schematic-chest-form"/);
});

test('a checked chest can monitor a hand-filled build and the dashboard drops its selection on completion', async t => {
  const fixture = {...structuredClone(snapshot), activeWorldId:'world-a', activeDimension:'minecraft:overworld',
    schematicFunnelAvailable:true, worldSession:'one'};
  Object.assign(fixture.worlds[0], {schematicChest:'chest-a', schematicChestMark:'marked'});
  const env = environment(t, true, fixture);
  await import('../dist/app.js?schematic-auto-clear-completion');
  await new Promise(resolve => setImmediate(resolve));
  await env.click({action:'nav',view:'planner'});
  assert.match(env.app.innerHTML, /option value="chest-a" selected/);
  await env.handlers.submit({preventDefault(){}, target:{id:'schematic-chest-form', elements:{destination:{value:'chest-a'}}}});
  assert.match(env.modal.innerHTML, /Check contents/);
  const completed = structuredClone(fixture);
  delete completed.worlds[0].schematicChest;
  completed.worlds[0].schematicChestMark = 'cleared';
  env.responses.set('/api/snapshot', completed);
  env.responses.set('/api/schematic-chest', {message:'The schematic chest has all listed materials.', moved:0, missing:[], autoCleared:true});
  await env.handlers.submit({preventDefault(){}, target:{id:'confirm-schematic-fill', querySelector: () => ({})}});
  const sent = env.requests.find(r => r.path === '/api/schematic-chest');
  assert.deepEqual(JSON.parse(sent.options.body), {worldId:'world-a', worldSession:'one', destination:'chest-a',
    destinationMark:'marked', sources:[], materials:[{id:'minecraft:stone', count:10}]});
  assert.match(env.modal.innerHTML, /Build chest unchecked because all materials are ready/);
  assert.doesNotMatch(env.app.innerHTML, /option value="chest-a" selected/);
});
