import {humanize,validId,validateSnapshot,aggregate,inventoryRows,compareMaterials,materialsCsv,MAX_COUNT,CONTAINER_KINDS,STORAGE_KINDS,containerKind,sourceScope,filterContainers,selectLiveWorld} from './core.js';
import {demoSnapshot,demoTargets,demoProjects} from './demo.js';
import {itemIcons} from './item-icons.js';
import {parseMaterialList} from './schematic.js';
import {planMaterials,craftingCsv} from './recipes.js';
import {recipeCatalogs} from './recipes-data.js';
import {findUnavailable,findRequest} from './find.js';
import {stale,freeContainers,allocateClaims,reservationDeficit,mutateReservation,validatePlanning,historyTrends} from './planning-state.js';
import {sharedExport,mergeShared,sharedWorkspace} from './sharing.js';
import {languages,localized,resolveLanguage} from './i18n.js';
import {containerKeys,chestExcluded,buildSources,toggleChestSelection,changeChestExclusion,chestMap,fitChestGroup,mapBox,addChestsInBox,validTerrain,fitTerrain} from './map.js';

const $=s=>document.querySelector(s);
const esc=s=>String(s??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
let language = resolveLanguage([readLocal('stockpile-language', ''), ...(globalThis.navigator?.languages || [])]);
let numberFormatter;
function applyLanguage() {
  numberFormatter = new Intl.NumberFormat(languages[language].locale);
  if (document.documentElement) {
    document.documentElement.lang = language;
    document.documentElement.dir = language === 'ar' ? 'rtl' : 'ltr';
    const skip = $('.skip');
    if (skip) skip.textContent = localized(language, 'Skip to content');
  }
}
applyLanguage();
const num = n => numberFormatter.format(n);
const t = (label, values) => esc(localized(language, label, values));
const pixelPaths={search:'M2 2h8v2H2zM0 4h2v6H0zM10 4h2v6h-2zM2 10h8v2H2zM10 12h2v2h-2zM12 14h4v2h-4z',arrow:'M1 7h10V3h2v2h2v2h1v2h-1v2h-2v2h-2V9H1z',download:'M7 1h2v8h3v2h-2v2H6v-2H4V9h3zM1 12h2v2h10v-2h2v4H1z',upload:'M6 1h4v2h2v2H9v8H7V5H4V3h2zM1 12h2v2h10v-2h2v4H1z',plus:'M7 2h2v5h5v2H9v5H7V9H2V7h5z',close:'M2 2h2v2H2zm2 2h2v2H4zm2 2h4v4H6zm4-2h2v2h-2zm2-2h2v2h-2zM4 10h2v2H4zm-2 2h2v2H2zm8-2h2v2h-2zm2 2h2v2h-2z',chevron:'M5 2h2v2H5zm2 2h2v2H7zm2 2h2v4H9zm-2 4h2v2H7zm-2 2h2v2H5z',check:'M12 2h2v4h-2zm-2 4h2v2h-2zM8 8h2v2H8zM6 10h2v2H6zM4 8h2v2H4zM2 6h2v2H2z',menu:'M1 2h14v2H1zm0 5h14v2H1zm0 5h14v2H1z',copy:'M1 1h9v2H3v8H1zm4 4h10v10H5zm2 2v6h6V7z',trash:'M5 1h6v2h4v2H1V3h4zM3 6h2v7h6V6h2v9H3zM7 6h2v6H7z',sliders:'M2 1h2v4H2zm0 10h2v4H2zM0 5h6v6H0zm8-4h2v9H8zm0 13h2v1H8zM6 10h6v4H6zm8-9h2v3h-2zm0 9h2v5h-2zM12 4h4v6h-4z',info:'M2 0h12v2H2zM0 2h2v12H0zM14 2h2v12h-2zM2 14h12v2H2zM7 3h2v2H7zm0 4h2v6H7z',external:'M9 0h7v7h-2V3l-6 6-1-2 6-5H9zM0 3h6v2H2v9h9v-4h2v6H0z',refresh:'M3 2h8v2H3zM1 4h2v7H1zm2 7h8v2H3zm8-7h2v5h-6V7h4z'};
const gameIcons={grid:'crafting_table',box:'chest',layers:'iron_ingot',hammer:'iron_pickaxe',pin:'compass',alert:'redstone_torch',clock:'clock',leaf:'oak_sapling',file:'paper',cube:'grass_block',spark:'book',link:'redstone',ender:'ender_pearl',barrel:'barrel',shulker:'shulker_box'};
const icon=(name,size=17)=>gameIcons[name]?`<img class="game-ui-icon" src="./items/${gameIcons[name]}.png" width="${size}" height="${size}" alt="" aria-hidden="true">`:`<svg width="${size}" height="${size}" viewBox="0 0 16 16" fill="currentColor" shape-rendering="crispEdges" aria-hidden="true"><path d="${pixelPaths[name]||pixelPaths.info}" fill-rule="evenodd"/></svg>`;
function itemArt(id){return `<img class="minecraft-item" src="${esc(itemIcons[id]||'./ui/unknown.svg')}" alt="" loading="lazy" width="32" height="32">`;}
const itemImage=id=>`<span class="item-image">${itemArt(id)}</span>`;
let persistenceError=false;
function readLocal(key,fallback){try{return JSON.parse(localStorage.getItem(key))??fallback;}catch{return fallback;}}
function writeLocal(key,value){try{localStorage.setItem(key,JSON.stringify(value));return true;}catch{persistenceError=true;toast('Browser storage is full or unavailable. Export your changes before closing.');return false;}}
const local=/^(127\.0\.0\.1|localhost)$/.test(location.hostname)&&/^194(?:8[2-9]|9[0-2])$/.test(location.port);
let token='';
let firstView='overview';
let openSharingOnSync=false;
try{
  const link=new URLSearchParams(location.hash.slice(1));
  if(local&&link.get('view')==='map')firstView='map';
  openSharingOnSync=local&&link.get('view')==='sharing';
  token=link.get('token')||sessionStorage.getItem('stockpile-token')||'';
  if(token){sessionStorage.setItem('stockpile-token',token);history.replaceState(null,'',location.pathname+location.search);}
}catch{}
let restored=readLocal('stockpile-inventory',null);
try{if(restored)restored=validateSnapshot(restored);}catch{restored=null;}
let savedProjects=readLocal('stockpile-projects',[]);
if(!Array.isArray(savedProjects))savedProjects=[];
savedProjects=savedProjects.filter(p=>{try{parseMaterialList(JSON.stringify(p),'.json');return typeof p.id==='string';}catch{return false;}}).slice(0,50);
const state={mapCaptureBusy:false,mapTerrainVisible:true,mapMode:'pan',mapWorldId:'',mapDimension:'',mapViewport:null,mapFocus:[],mapGroups:[],staleHours:Math.max(1,Number(readLocal('stockpile-stale-hours',24))||24),excludeStale:readLocal('stockpile-exclude-stale',false)===true,smelting:readLocal('stockpile-smelting',false)===true,blast:readLocal('stockpile-blast',true)!==false,fuelChoice:readLocal('stockpile-fuel','coals'),historyDays:1,view:firstView,source:local?'local':restored?'import':'demo',data:local?{schemaVersion:1,capturedAt:0,connected:false,worlds:[]}:restored||validateSnapshot(demoSnapshot),worldId:'',dimension:'all',query:'',filter:'all',sort:'count',limit:20,projects:savedProjects,selectedProject:'',copies:1,missingOnly:false,materialSort:null,registry:null,syncError:'',uploadError:'',busy:false,mobile:false,recipeSubstitutions:readLocal('stockpile-recipe-substitutions',false)===true,recipeVersion:['26.2','26.3'].includes(readLocal('stockpile-recipe-version','26.3'))?readLocal('stockpile-recipe-version','26.3'):'26.3',includePacked:readLocal('stockpile-include-packed',true)!==false,containerKinds:(Array.isArray(readLocal('stockpile-container-kinds',STORAGE_KINDS))?readLocal('stockpile-container-kinds',STORAGE_KINDS):STORAGE_KINDS).filter(k=>CONTAINER_KINDS.includes(k))};
state.worldId=state.data.worlds[0]?.id||'';
let toastTimer;
function toast(message){const el=$('#toast');el.textContent=localized(language,message);el.classList.add('show');clearTimeout(toastTimer);toastTimer=setTimeout(()=>el.classList.remove('show'),4200);}
function world(){return state.data.worlds.find(w=>w.id===state.worldId)||{id:'',name:'No world yet',containers:[]};}
function rawContainers(){return filterContainers(world().containers,{kinds:state.containerKinds,dimension:state.dimension});}
function exclusionKey(){return `stockpile-excluded-chests:${state.worldId}`;}
function exclusions(){
  if(state.source==='local')return world().excludedLocations||[];
  const legacy=(world().excludedContainerIds||[]).flatMap(id=>containerKeys(world().containers.find(c=>c.id===id)||{id}));
  const saved=readLocal(exclusionKey(),world().excludedLocations?.length?world().excludedLocations:legacy);
  return Array.isArray(saved)?saved.filter(key=>typeof key==='string'):[];
}
function containers(){return buildSources(rawContainers(),{excluded:exclusions()});}
function selectionKey(){return `stockpile-map-sources:${state.worldId}`;}
function mapSelection(){const saved=readLocal(selectionKey(),null);return Array.isArray(saved)?saved.filter(key=>typeof key==='string'):null;}
function selectedSources(){return buildSources(rawContainers(),{excluded:exclusions(),selected:mapSelection()});}
function mapSelected(c){const selected=mapSelection();return selected!==null&&containerKeys(c).every(key=>selected.includes(key));}
function exclusionButton(c){const excluded=chestExcluded(c,exclusions());return button('exclude-chest',excluded?'Include chest':'Exclude chest','','quiet small',`data-id="${esc(c.id)}" data-excluded="${!excluded}"`);}
async function excludeChest(id,value){
  const c=world().containers.find(c=>c.id===id);if(!c)throw new Error('Saved container not found.');
  if(state.source==='local'){await api('/api/exclusions',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({worldId:state.worldId,containerId:id,excluded:value})});await sync();}
  else if(!writeLocal(exclusionKey(),changeChestExclusion(exclusions(),c,value)))throw new Error('Chest preference could not be saved.');
  $('#modal').close();render();toast(value?'Chest excluded from counts and new build plans. Existing reservations are retained.':'Chest included again.');
}
function mapControls() {
  const selection = mapSelection();
  const count = selection === null ? 0 : selectedSources().length;
  const label = selection === null ? t('All included storage') : `${num(count)} ${t(count === 1 ? 'selected container' : 'selected containers')}`;
  return `<section class="map-source-summary" aria-label="${t('Build sources')}">
    <div class="build-source-icon">${icon('box', 23)}</div>
    <div class="build-source-text"><h2>${t('Build sources')}</h2><span>${label}</span></div>
    <div class="build-source-actions">
      ${state.view === 'map' ? '' : button('nav','Choose on map','pin','primary small','data-view="map"')}
      ${selection === null ? '' : button('map-all','Use all included storage','','quiet small')}
    </div></section>`;
}
function mapHighlight(c){const reason=world().manualTeam?'Manual team files provide coordinates only.':findUnavailable({source:state.source,connected:state.data.connected,syncError:state.syncError});return button('highlight-chest','Highlight chest','pin','small',`data-id="${esc(c.id)}" data-world="${esc(state.worldId)}" ${reason?`disabled title="${esc(reason)}"`:''}`);}
const TERRAIN_CACHE_KEY = 'stockpile-terrain';
const savedTerrain = readLocal(TERRAIN_CACHE_KEY, []);
let terrainCache = Array.isArray(savedTerrain) ? savedTerrain.slice(0, 8) : [];
let terrainRevision = 0;
let mapDrag = null;

function terrainForMap() {
  const scope = world().shared ? world().authority : state.worldId;
  return terrainCache.find(entry => (entry?.worldId === state.worldId || entry?.worldId === scope) &&
    entry.dimension === state.mapDimension && validTerrain(entry));
}

function rememberTerrain(captures) {
  const combined = [...captures, ...terrainCache].filter(entry =>
    typeof entry?.worldId === 'string' && validId(entry.dimension) && validTerrain(entry));
  const seen = new Set();
  terrainCache = combined.sort((a, b) => b.capturedAt - a.capturedAt).filter(entry => {
    const key = JSON.stringify([entry.worldId, entry.dimension]);
    if (seen.has(key)) return false;
    seen.add(key);
    return true;
  }).slice(0, 8);
  return writeLocal(TERRAIN_CACHE_KEY, terrainCache);
}

function isJoinedWorkspace() {
  return state.worldId === state.data.activeWorldId ||
    (world().shared && world().live && world().authority === state.data.activeWorldId);
}

function terrainUnavailable() {
  if (state.source !== 'local' || state.syncError || !state.data.connected || !state.worldId) return 'Join a world in Minecraft to capture terrain.';
  if (!isJoinedWorkspace()) return 'Select the joined world to capture terrain.';
  if (state.data.activeDimension && state.mapDimension !== state.data.activeDimension) return 'Enter this dimension in Minecraft to capture terrain.';
  return '';
}

async function captureTerrain() {
  const reason = terrainUnavailable();
  if (state.mapCaptureBusy || reason) {
    if (reason) toast(reason);
    return;
  }
  const {worldId, mapDimension: dimension} = state;
  const captureScope = world().shared ? world().authority : worldId;
  state.mapCaptureBusy = true;
  render();
  try {
    const capture = await api('/api/map/terrain', {
      method: 'POST', headers: {'Content-Type': 'application/json'}, body: JSON.stringify({worldId, dimension})
    });
    if (!validTerrain(capture)) throw new Error('Minecraft returned an invalid terrain snapshot.');
    const saved = rememberTerrain([{...capture, worldId: captureScope, dimension}]);
    if (state.worldId === worldId && state.mapDimension === dimension) {
      state.mapViewport = fitTerrain(capture);
      state.mapTerrainVisible = true;
    }
    toast(saved ? capture.message : 'Captured terrain for this tab. Browser storage is full.');
  } catch (error) {
    toast(error.message);
  } finally {
    state.mapCaptureBusy = false;
    render();
  }
}

async function showHighlightSettings() {
  try {
    const options = await api('/api/highlight/settings');
    modal('Highlight settings', `<p class="highlight-intro">${t('Customize how marked chests appear in Minecraft.')}</p>
      <form id="highlight-form">
        <div class="highlight-preview" aria-label="${t('Outline preview')}">
          <div class="highlight-tiles" aria-hidden="true"><img src="./items/chest.png" alt=""><i></i></div>
          <div><strong>${t('Outline preview')}</strong><p id="highlight-preview-text"></p></div>
        </div>
        <div class="highlight-fields">
          <label class="field">${t('Outline color')}<input name="color" type="color" value="${esc(options.color)}" required></label>
          <label class="field">${t('Duration in seconds')}<input name="seconds" type="number" min="5" max="120" value="${esc(options.seconds)}" required></label>
        </div>
        <div class="highlight-palette" role="group" aria-label="${t('Outline color')}">
          ${['#ffc857', '#97cc71', '#69bbff', '#e698ff', '#ff8484'].map(color => `<button type="button" data-action="highlight-color"
            data-color="${color}" aria-label="${color}" aria-pressed="${options.color.toLowerCase() === color}">${color}</button>`).join('')}
        </div>
        <label class="switch-line"><input name="throughWalls" type="checkbox" role="switch" ${options.throughWalls ? 'checked' : ''}>
          <span>${t('Show through walls')}</span></label>
        <div class="modal-actions"><button type="submit" class="button primary">${t('Save highlight settings')}</button></div>
      </form>`);
    updateHighlightPreview($('#highlight-form'));
  } catch (error) {
    toast(error.message);
  }
}

function updateHighlightPreview(form) {
  if (!form) return;
  const {color, throughWalls} = form.elements;
  const preview = form.querySelector('.highlight-preview');
  preview.style.setProperty('--outline', color.value);
  preview.classList.toggle('through-walls', throughWalls.checked);
  form.querySelector('#highlight-preview-text').textContent = localized(language,
    throughWalls.checked ? 'Visible through walls' : 'Visible in line of sight');
  for (const swatch of form.querySelectorAll('[data-color]')) {
    swatch.style.setProperty('--swatch', swatch.dataset.color);
    swatch.setAttribute('aria-pressed', String(swatch.dataset.color === color.value.toLowerCase()));
  }
}

function renderMapGraphic(model, terrain, excluded, selected) {
  const {left, top, span, height, radius} = model;
  const xGrid = model.xGrid.map(x => `<line x1="${x}" y1="${top}" x2="${x}" y2="${top + height}"/>
    <text x="${x + span / 400}" y="${top + span / 55}">X ${num(x || 0)}</text>`).join('');
  const zGrid = model.zGrid.map(z => `<line x1="${left}" y1="${z}" x2="${left + span}" y2="${z}"/>
    <text x="${left + span / 400}" y="${z + span / 55}">Z ${num(z || 0)}</text>`).join('');
  const markers = model.markers.map((group, index) => {
    const allExcluded = group.containers.every(chest => chestExcluded(chest, excluded));
    const chosen = group.containers.some(selected);
    const label = group.containers.length === 1
      ? `${group.containers[0].name} at ${group.containers[0].position.join(', ')}`
      : `${group.containers.length} chests. Zoom or inspect stacked heights.`;
    const color = allExcluded ? '#a0a5a9' : chosen ? '#c7f481' : '#78c8ef';
    return `<g data-action="map-pick" data-index="${index}" role="button" tabindex="0" aria-label="${esc(label)}" aria-pressed="${chosen}">
      <title>${esc(label)}</title>
      <circle class="map-marker-halo" cx="${group.x}" cy="${group.z}" r="${radius * 1.3}" fill="#102019"/>
      <circle class="map-marker" cx="${group.x}" cy="${group.z}" r="${radius}" fill="${color}"/>
      ${group.containers.length > 1 ? `<text class="map-marker-count" x="${group.x}" y="${group.z + radius * .35}" font-size="${radius}">${group.containers.length}</text>` : ''}
    </g>`;
  }).join('');
  const image = terrain && state.mapTerrainVisible
    ? `<image class="map-terrain" x="${terrain.x}" y="${terrain.z}" width="${terrain.size}" height="${terrain.size}" href="data:image/png;base64,${terrain.png}"/>` : '';
  return `<svg id="storage-map" class="storage-map ${state.mapMode === 'select' ? 'box-mode' : ''}"
      viewBox="${left} ${top} ${span} ${height}" role="group" aria-label="Remembered storage positions in ${esc(dimensionLabel(model.dimension))}">
    ${image}
    <g class="map-grid" font-size="${span / 65}">${xGrid}${zGrid}</g>
    ${markers}
    <rect id="map-selection-box" class="map-selection-box" visibility="hidden"/>
  </svg>`;
}

function renderChestMap() {
  if (state.mapWorldId !== state.worldId) {
    state.mapWorldId = state.worldId;
    state.mapDimension = '';
    state.mapViewport = null;
    state.mapFocus = [];
  }
  const candidates = filterContainers(world().containers, {kinds:state.containerKinds});
  const dimensions = new Set(candidates.map(chest => chest.dimension));
  const currentDimension = isJoinedWorkspace() ? state.data.activeDimension : '';
  if (currentDimension) dimensions.add(currentDimension);
  for (const entry of terrainCache) {
    if ((entry?.worldId === state.worldId || (world().shared && entry?.worldId === world().authority)) &&
        validId(entry.dimension) && validTerrain(entry)) dimensions.add(entry.dimension);
  }
  if (!dimensions.has(state.mapDimension)) {
    state.mapDimension = currentDimension || (dimensions.has('minecraft:overworld') ? 'minecraft:overworld' : [...dimensions][0] || 'minecraft:overworld');
    state.mapViewport = null;
    state.mapFocus = [];
  }

  const terrain = terrainForMap();
  const viewport = state.mapViewport || (terrain && state.mapTerrainVisible ? fitTerrain(terrain) : null);
  const model = chestMap(candidates, state.mapDimension, viewport);
  state.mapGroups = model.markers;
  const selection = mapSelection();
  const excluded = new Set(exclusions());
  const chosenKeys = new Set(selection || []);
  const selected = chest => selection !== null && containerKeys(chest).every(key => chosenKeys.has(key));
  const query = state.query.trim().toLowerCase();
  const focused = new Set(state.mapFocus);
  const list = model.visible.filter(chest =>
    (!focused.size || focused.has(chest.id)) &&
    (!query || `${chest.name} ${chest.position.join(' ')}`.toLowerCase().includes(query)));
  const reason = terrainUnavailable();
  const captureAttributes = reason ? `disabled title="${esc(reason)}"` : state.mapCaptureBusy ? 'disabled' : '';
  const tools = `<div class="map-toolbar">
    <label>${t('Dimension')} <select id="map-dimension">${[...dimensions].map(dimension =>
      `<option value="${esc(dimension)}" ${dimension === state.mapDimension ? 'selected' : ''}>${esc(dimensionLabel(dimension))}</option>`).join('')
      || `<option>${t('Overworld')}</option>`}</select></label>
    <div class="button-row" aria-label="Map tools">
      ${button('map-mode-pan', 'Pan', '', state.mapMode === 'pan' ? 'primary small' : 'small', `aria-pressed="${state.mapMode === 'pan'}"`)}
      ${button('map-mode-select', 'Box select', '', state.mapMode === 'select' ? 'primary small' : 'small', `aria-pressed="${state.mapMode === 'select'}"`)}
      ${button('map-zoom-in', 'Zoom in', '', 'small')}
      ${button('map-zoom-out', 'Zoom out', '', 'small')}
      ${button('map-fit', 'Fit chests', '', 'small')}
      ${button('map-none', 'Select none', '', 'quiet small')}
    </div>
  </div>`;
  const terrainTools = `<div class="map-toolbar map-terrain-tools"><div class="button-row">
    ${button('map-capture', state.mapCaptureBusy ? 'Capturing…' : 'Capture terrain', 'cube', 'small', captureAttributes)}
    ${terrain ? button('map-fit-terrain', 'Fit terrain', '', 'small') : ''}
    ${terrain ? button('map-terrain-toggle', state.mapTerrainVisible ? 'Hide terrain' : 'Show terrain', '', 'quiet small') : ''}
    </div><span class="map-note">${terrain ? `Captured ${esc(new Date(terrain.capturedAt).toLocaleString(languages[language].locale))}` : reason ? esc(reason) : t('Captures automatically after nearby terrain loads.')}</span>
  </div>`;
  const cards = list.slice(0, state.limit).map(chest => {
    const isExcluded = chestExcluded(chest, excluded);
    const isSelected = selected(chest);
    let sourceStatus = 'Available to all build checks';
    if (selection !== null) sourceStatus = isSelected ? 'Selected build source' : 'Outside the build selection';
    if (isExcluded) sourceStatus = 'Excluded from counts and planning';
    const [x, y, z] = chest.position;
    return `<article class="map-chest ${isExcluded ? 'excluded' : ''}">
      <div><strong>${esc(chest.name)}</strong><p>${esc(originLabel(chest))} · X ${x} / Y ${y} / Z ${z}</p>
      <p>${sourceStatus}${containerKind(chest) === 'ender' ? ' · last Ender Chest access point' : ''}</p></div>
      <div class="button-row">
        ${button('map-toggle', isSelected ? 'Deselect' : 'Select', 'check', 'small',
          `data-id="${esc(chest.id)}" ${isExcluded ? 'disabled' : ''} aria-pressed="${isSelected}"`)}
        ${mapHighlight(chest)}${exclusionButton(chest)}
      </div></article>`;
  }).join('');
  return `<div class="title-row"><div><h1>${t('Storage map')}</h1><p>${t('Choose the chests that supply your schematic.')}</p></div>
    <div class="map-page-actions">${highlightButton()}${button('planner', 'Use selection in Build planner', 'hammer', 'primary')}</div></div>
    ${mapControls()}${tools}${terrainTools}
    <div class="storage-map-wrap">${renderMapGraphic(model, terrain, excluded, selected)}
      <div class="map-direction"><span>↑ North (−Z) · East (+X) →</span>
        <span><i class="map-legend selected"></i>${t('Selected')} <i class="map-legend available"></i>${t('Available')} <i class="map-legend excluded"></i>${t('Excluded')}</span>
      </div>
    </div>
    <p class="map-note">${t('Drag in Box select mode or hold Shift to add chests. Click a cluster to zoom or list chests by height.')}</p>
    ${containerControls()}${staleControls()}
    <div class="map-toolbar"><label class="search"><input id="inventory-search" type="search" placeholder="Search names or coordinates"
      value="${esc(state.query)}" aria-label="${t('Search map containers')}"></label>
      ${focused.size ? button('map-unfocus', 'Show all heights and locations', '', 'small') : ''}
    </div>
    <div class="map-chest-list">${cards || `<p>${t('Open storage in Minecraft to add chest locations to this view.')}</p>`}
      ${list.length > state.limit ? button('more', 'Show more locations', '', 'small') : ''}</div>`;
}
function scopeLabel(){return localized(language,{storage:'Storage inventory',ender:'Ender Chest',combined:'Combined inventory',none:'No containers selected'}[sourceScope(state.containerKinds)]);}
function originLabel(c){return localized(language,{chest:'Chest',barrel:'Barrel',shulker:'Placed shulker box',ender:'Ender Chest'}[containerKind(c)]);}
function containerControls(){return `<section class="container-controls" aria-label="${t('Container sources')}"><div class="source-heading"><strong>${t('Container sources')}</strong><span>${scopeLabel()}</span></div><div class="container-options">${CONTAINER_KINDS.map(k=>`<label class="container-kind ${state.containerKinds.includes(k)?'selected':''}">${icon(k==='chest'?'box':k,18)}<input type="checkbox" data-container-kind="${k}" ${state.containerKinds.includes(k)?'checked':''}><span>${t({chest:'Chests',barrel:'Barrels',shulker:'Placed shulkers',ender:'Ender Chest'}[k])}</span></label>`).join('')}</div><p>${t('Ender Chest contents stay separate unless you include them here. Build checks use these selected sources.')}</p></section>`;}
function legacyTargetKey(){return `stockpile-targets:${state.source==='demo'?'demo':'real'}:${state.worldId}`;}
function targetKey(){return `${legacyTargetKey()}:${sourceScope(state.containerKinds)}`;}
function targets(){const fallback=sourceScope(state.containerKinds)==='storage'?readLocal(legacyTargetKey(),state.source==='demo'?demoTargets:{}):{};const data=readLocal(targetKey(),fallback);return Object.fromEntries(Object.entries(data||{}).filter(([id,n])=>validId(id)&&Number.isInteger(n)&&n>=0&&n<=MAX_COUNT));}
function inventory(){return aggregate(containers(),{includePacked:state.includePacked});}
function planningKey(){return `stockpile-planning:${state.source==='demo'?'demo':'real'}:${state.worldId}`;}
function planning(){try{return state.source==='local'?validatePlanning(world().planning):validatePlanning(readLocal(planningKey(),world().planning));}catch{return validatePlanning(null);}}
function planningOwner(){return world().shared?world().viewer||'local':'local';}
function planningContainers(){return selectedSources().filter(c=>!state.excludeStale||!stale(c,state.staleHours));}
let pendingFunnel;
const funnelDestinationKey = p => `stockpile-schematic-chest:${state.worldId}:${p.id}`;
function schematicDestination(p) {
  const {schematicChest, schematicChestMark} = world();
  const saved = readLocal(funnelDestinationKey(p), '');
  if (typeof saved === 'string') return schematicChestMark ? schematicChest || '' : saved;
  return saved.mark === (schematicChestMark || '') ? saved.id : schematicChest || '';
}
function saveSchematicDestination(p, id) {
  writeLocal(funnelDestinationKey(p), {id,mark:world().schematicChestMark || ''});
}
const isFunnelSession = () => state.source === 'local' && !state.syncError &&
  state.data.schematicChestEnabled !== false && state.data.schematicFunnelAvailable && state.data.activeWorldId === state.worldId;
function funnelDestinations() {
  return world().containers.filter(c => containerKind(c) === 'chest' && !chestExcluded(c, exclusions()) &&
    state.data.activeDimension === c.dimension);
}
function funnelSources(destination) {
  if (!mapSelection()?.length) return [];
  return planningContainers().filter(c => c.id !== destination && containerKind(c) !== 'ender' &&
    c.dimension === state.data.activeDimension);
}
const canReviewSchematicChest = id => !!id && (id === world().schematicChest || funnelSources(id).length > 0);
function schematicChestPanel(p) {
  const enabled = state.data.schematicChestEnabled !== false;
  const connectedDashboard = state.source === 'local' && !state.syncError;
  if (!connectedDashboard && (!p || p.sample)) return '';
  const heading = `<div class="schematic-heading"><h2>${t('Schematic chest')}</h2>
    ${connectedDashboard ? `<div class="schematic-settings">
      <label class="switch-line"><input id="schematic-chest-enabled" type="checkbox" role="switch" ${enabled?'checked':''}>
        <span>${t('Enable schematic chest')}</span></label>
      <label class="switch-line"><input id="schematic-chest-auto-clear" type="checkbox" role="switch"
        ${state.data.schematicChestAutoClear!==false?'checked':''}><span>${t('Auto-clear on completion')}</span></label>
    </div>` : ''}</div>`;
  if (!enabled || !isFunnelSession()) return `<section class="reservation-panel schematic-chest">${heading}
    <p>${t(enabled ? 'Available in single-player. Open a destination chest and select its sources on the Storage map.' : 'Schematic chest is disabled.')}</p></section>`;
  if (!p || p.sample) return `<section class="reservation-panel schematic-chest">${heading}<p>${t('Upload schematic')}</p></section>`;
  const destinations = funnelDestinations();
  const chosen = schematicDestination(p);
  return `<section class="reservation-panel schematic-chest">${heading}
    <p>${t('Move loose materials for this build from the map selection into one chest. Reserved items stay in place.')}</p>
    <p>${t('Open a chest in Minecraft and click Build chest to select it here.')}</p>
    <p>${t('Confirm a build to watch its materials. Auto-clear unchecks Build chest once all listed materials are inside.')}</p>
    <form id="schematic-chest-form">
      <label>${t('Destination')} <select name="destination" id="schematic-destination" required>
        <option value="">${t(destinations.length ? 'Choose a chest…' : 'Open a chest in this dimension first')}</option>
        ${destinations.map(c => `<option value="${esc(c.id)}" ${chosen === c.id ? 'selected' : ''}>${esc(c.name)} · ${esc(c.position.join(' / '))}</option>`).join('')}
      </select></label>
      <button class="button primary" type="submit" ${destinations.some(c=>c.id===chosen) && canReviewSchematicChest(chosen) ? '' : 'disabled'}>${t('Review fill')}</button>
      ${button('nav', 'Choose sources', 'pin', 'small quiet', 'type="button" data-view="map"')}
    </form></section>`;
}

async function saveSchematicSetting(control, setting) {
  const checked = control.checked;
  control.disabled = true;
  try {
    const result = await api('/api/schematic-chest/settings', {
      method:'POST', headers:{'Content-Type':'application/json'}, body:JSON.stringify({[setting]:checked})
    });
    if (setting === 'enabled') {
      state.data.schematicChestEnabled = result.enabled;
      if (!result.enabled) {
        pendingFunnel = undefined;
        if ($('#confirm-schematic-fill')) $('#modal').close();
      }
    } else {
      state.data.schematicChestAutoClear = result.autoClear;
    }
    render();
    toast(setting === 'enabled'
      ? (result.enabled ? 'Schematic chest is enabled.' : 'Schematic chest is disabled.')
      : (result.autoClear ? 'Auto-clear is on.' : 'Auto-clear is off.'));
  } catch (error) {
    control.checked = !checked;
    toast(error.message);
  } finally {
    control.disabled = false;
  }
}
function reviewSchematicFill(destinationId) {
  const p = project();
  const destination = funnelDestinations().find(c => c.id === destinationId);
  const sources = funnelSources(destinationId);
  if (!isFunnelSession() || !p || !destination || !canReviewSchematicChest(destinationId)) throw new Error('Check Build chest in Minecraft or choose a destination and at least one source.');
  if (sources.length > 128 || p.materials.length > 1000) throw new Error('Select up to 128 source chests for a build with up to 1,000 material types.');
  const materials = compareMaterials(p.materials, new Map(), state.copies).filter(row => row.needed > 0)
    .map(({id, needed}) => ({id, count: needed}));
  if (!materials.length) throw new Error('This build has no materials to move.');
  pendingFunnel = {worldId: state.worldId, worldSession: state.data.worldSession,
    destination: destination.id, sources: sources.map(c => c.id), materials};
  if (destination.id === world().schematicChest && world().schematicChestMark) {
    pendingFunnel.destinationMark = world().schematicChestMark;
  }
  saveSchematicDestination(p, destination.id);
  modal(sources.length ? 'Fill schematic chest' : 'Check build chest', `<p>${t(sources.length
    ? 'Move materials for {build} × {copies} into {chest} at {position}.'
    : 'Check materials for {build} × {copies} in {chest} at {position}.',
    {build:p.name,copies:num(state.copies),chest:destination.name,position:destination.position.join(' / ')})}</p>
    ${sources.length ? `<p>${t('Stockpile checks {count} selected sources and moves what fits.',{count:num(sources.length)})}</p>` : ''}
    <form id="confirm-schematic-fill" class="modal-actions"><button class="button primary" type="submit">${t(sources.length ? 'Move materials' : 'Check contents')}</button></form>`);
}
async function fillSchematicChest() {
  const request = pendingFunnel;
  if (!request || !isFunnelSession() || request.worldSession !== state.data.worldSession || request.worldId !== state.worldId) return;
  pendingFunnel = undefined;
  try {
    const result = await api('/api/schematic-chest', {
      method:'POST', headers:{'Content-Type':'application/json'}, body:JSON.stringify(request)
    });
    await sync();
    if (request.worldSession !== state.data.worldSession || request.worldId !== state.worldId) return;
    modal('Schematic chest', `<p>${request.sources.length?`<strong>${t('{count} items moved.',{count:num(result.moved)})}</strong> `:''}${t(result.message)}</p>
      ${result.autoCleared ? `<p>${t('Build chest unchecked because all materials are ready.')}</p>` : ''}
      ${result.skipped ? `<p>${t('{count} unavailable sources skipped.',{count:num(result.skipped)})}</p>` : ''}
      ${result.missing.length ? `<details class="packed-dropdown"><summary>${t('Still needed · {count} material types',{count:num(result.missing.length)})}</summary>
        <ul>${result.missing.map(item => `<li>${num(item.count)} ${esc(humanize(item.id))}</li>`).join('')}</ul></details>` : ''}`);
  } catch (error) {
    if (request.worldSession === state.data.worldSession) modal('Schematic chest', `<p>${esc(error.message)}</p>`);
  }
}
function freeInventory(p){return aggregate(freeContainers(planningContainers(),planning(),p?.id,planningOwner()),{includePacked:state.includePacked});}
function fuelIds(){return state.fuelChoice==='all'?(recipeCatalogs[recipeVersion()].fuels||[]).map(f=>f.id):state.fuelChoice==='coals'?['minecraft:coal','minecraft:charcoal']:[state.fuelChoice];}
function savedWorldNotice(){
  if(state.source!=='local'||!state.data.connected||!state.data.activeWorldId||state.worldId===state.data.activeWorldId||world().shared&&world().authority===state.data.activeWorldId)return '';
  const joined=state.data.worlds.find(w=>w.id===state.data.activeWorldId);
  return `<div class="storage-warning">${t('Viewing saved inventory for {world}. Joined world: {joined}.',{world:world().name,joined:joined?.name||'Unknown'})} ${button('current-world','Show joined world','','small')}</div>`;
}
function staleControls() {
  const old = containers().filter(c=>stale(c,state.staleHours)).length;
  const status = world().shared ? (world().live ? 'Team server updates are connected.' : 'Team workspace is offline; counts are saved observations.')
    : state.data.trackingEnabled === false ? 'Tracking is paused.'
    : state.source === 'import' ? 'Imported snapshots do not update automatically.'
    : !state.data.connected ? 'Minecraft is offline.' : '';
  return `<section class="freshness-controls">
    <label>${t('Warn after')} <input id="stale-hours" type="number" min="1" max="8760" value="${state.staleHours}">
      ${t('hours without an observation')}</label>
    <label><input id="exclude-stale" type="checkbox" ${state.excludeStale?'checked':''}>${t('Exclude old observations from build planning')}</label>
    <p>${old?t('{count} saved containers may be outdated.',{count:num(old)}):t('All selected observations are within this age limit.')}
      ${t(status)} ${t('Reopen storage to refresh.')}</p>
  </section>`;
}
async function changeReservation(action,projectId=project()?.id){
  const p=projectList().find(p=>p.id===projectId),current=planning();
  const request={worldId:state.worldId,projectId,revision:current.revision,action};
  if(action==='reserve'){
    if(!p)throw new Error('Choose a project first.');
    const plan=recipePlan(p);request.name=p.name;
    request.claims=allocateClaims(plan.stockUsage||[],planningContainers(),current,projectId,planningOwner(),state.includePacked);
    if(!request.claims.length)throw new Error('No free supplies in this plan to reserve.');
  }
  if(state.source==='local'){
    await api('/api/reservations',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(request)});await sync();
  }else{
    if(!globalThis.navigator?.locks)throw new Error('This browser cannot safely coordinate reservations across tabs. Use the connected dashboard or a browser with Web Locks.');
    await navigator.locks.request(planningKey(),async()=>{
      const latest=validatePlanning(readLocal(planningKey(),world().planning));
      const next=mutateReservation(latest,request,world().containers,planningOwner());
      if(!writeLocal(planningKey(),next))throw new Error('Reservation could not be saved.');
    });
  }
  render();toast(action==='reserve'?'Available supplies reserved. Missing materials remain unreserved.':action==='complete'?'Project completed; claims released.':'Reservation released.');
}
function reservationPanel(p){
  const plan=planning(),entries=Object.values(plan.reservations),deficit=reservationDeficit(buildSources(world().containers,{excluded:exclusions()}),plan),own=entries.find(r=>r.projectId===p?.id&&r.status==='active'&&r.owner===planningOwner());
  return `<section class="reservation-panel"><div class="section-head"><h2>${t('Build reservations')}</h2>${p?button('reserve',own?'Update this reservation':'Reserve available materials','box','small',`data-id="${esc(p.id)}" ${state.source==='local'&&world().shared&&!world().live?'disabled':''}`):''}</div><p>${t('Reserving materials makes them unavailable to other builds. Recipe plans reserve ingredients and fuel.')}</p>${deficit?`<div class="warning-note">${t('Observed stock is {count} items below existing claims. Reopen containers and adjust reservations before building.',{count:num(deficit)})}</div>`:''}${entries.map(r=>`<div class="reservation-row"><div><strong>${esc(r.name)}</strong><p>${r.status==='active'?t('{count} items reserved',{count:num(r.claims.reduce((n,c)=>n+c.count,0))}):t('Completed · claims released')}${r.owner!==planningOwner()?` · ${t('Teammate')}`:''}</p></div>${r.owner===planningOwner()?`<div class="button-row">${r.status==='active'?button('complete-reservation','Complete','check','small',`data-id="${esc(r.projectId)}"`):''}${button('release-reservation',r.status==='active'?'Release':'Remove','','quiet small',`data-id="${esc(r.projectId)}"`)}</div>`:''}</div>`).join('')||`<p>${t('No material claims yet.')}</p>`}</section>`;
}
const historyWindows = ['Last 24 hours', 'Last 7 days', 'Last 30 days'];
const historyActions = {
  observed: 'Contents changed', discovered: 'New container', forgotten: 'Record removed',
  replacement: 'Container replaced', imported: 'Snapshot imported', transfer: 'Schematic transfer'
};
const signed = n => `${n > 0 ? '+' : ''}${num(n)}`;

function historyRanking(rows, field, label, peak) {
  const top = rows.filter(row => row[field] > 0).sort((a,b) => b[field] - a[field]).slice(0, 5);
  return `<section class="history-ranking ${field}"><h2>${t(label)}</h2>
    ${top.length ? `<ol>${top.map(row => `<li><div class="history-item">${itemArt(row.id)}
      <span>${esc(humanize(row.id))}</span><b>${num(row[field])}</b></div>
      <meter min="0" max="${peak}" value="${row[field]}" aria-label="${esc(humanize(row.id))}"></meter></li>`).join('')}</ol>`
      : `<p>${t('No changes for these filters.')}</p>`}</section>`;
}

function renderHistory() {
  const since = Date.now() - state.historyDays * 86400000;
  const events = planning().history.filter(event => event.at >= since &&
    state.containerKinds.includes(containerKind(event)) &&
    (state.dimension === 'all' || state.dimension === event.dimension || containerKind(event) === 'ender'));
  const rows = historyTrends(events, {since});
  const totals = rows.reduce((sum, row) => ({
    increase:sum.increase + row.increase, decrease:sum.decrease + row.decrease,
  }), {increase:0,decrease:0});
  const net = totals.increase - totals.decrease;
  const peak = rows.reduce((maximum, row) => Math.max(maximum, row.increase, row.decrease), 1);
  const timeWindow = choice('history', [1,7,30].map((days, index) => ({
    value:String(days), label:localized(language,historyWindows[index])
  })), state.historyDays, 'History window');
  const containers = new Map(world().containers.map(container => [container.id, container]));
  return `<div class="title-row"><div><h1>${t('Stock history')}</h1>
    <p>${t('See what was added or taken from your storage.')}</p></div>${button('history-csv','Export history','download')}</div>
    ${containerControls()}
    <div class="history-filters"><div><span class="history-filter-label">${t('History window')}</span>${timeWindow}</div>${dimensionOptions()}</div>
    <div class="history-summary">
      <section class="history-metric gain"><span>${t('Items added')}</span><strong>${num(totals.increase)}</strong></section>
      <section class="history-metric loss"><span>${t('Items removed')}</span><strong>${num(totals.decrease)}</strong></section>
      <section class="history-metric ${net < 0 ? 'loss' : 'net'}"><span>${t('Net change')}</span><strong>${signed(net)}</strong></section>
    </div>
    <div class="history-rankings">${historyRanking(rows,'increase','Most added',peak)}${historyRanking(rows,'decrease','Most removed',peak)}</div>
    <h2 class="history-heading">${t('Item activity')}</h2>
    <div class="table-card table-wrap history-table"><table><thead><tr>
      ${['Item','Added','Removed','Net','Net per hour'].map(label=>`<th scope="col">${t(label)}</th>`).join('')}
    </tr></thead><tbody>
      ${rows.map(row=>`<tr><th scope="row"><div class="history-item">${itemArt(row.id)}${esc(humanize(row.id))}</div></th>
        <td class="history-gain">${num(row.increase)}</td><td class="history-loss">${num(row.decrease)}</td>
        <td>${signed(row.net)}</td><td>${signed(Math.round(row.perHour*100)/100)}</td></tr>`).join('') ||
        `<tr><td colspan="5">${t('No changes for these filters.')}</td></tr>`}
    </tbody></table></div>
    <h2 class="history-heading">${t('Recent observations')}</h2>
    <p class="history-note">${t('History records changes when storage is checked. Schematic transfers appear in the log and are excluded from the totals.')}</p>
    <div class="history-events">${events.slice(-50).reverse().map(event => {
      const container = containers.get(event.containerId);
      const location = container ? `${container.name} · ${container.position.join(' / ')}` : event.containerId;
      return `<article class="history-event"><div><strong>${t(historyActions[event.reason] || event.reason)}</strong>
        <span>${esc(new Date(event.at).toLocaleString(languages[language].locale))}</span></div>
        <p dir="auto">${esc(location)} · ${esc(dimensionLabel(event.dimension))}</p>
        <ul>${event.changes.slice(0,4).map(change => `<li class="${change.delta < 0 ? 'history-loss' : 'history-gain'}">
          ${signed(change.delta)} ${esc(humanize(change.itemId))}</li>`).join('')}
          ${event.truncated || event.changes.length > 4 ? `<li>${t('Partial')}</li>` : ''}</ul></article>`;
    }).join('') || `<p>${t('No changes for these filters.')}</p>`}</div>`;
}

function sharing() {
  const saved = readLocal('stockpile-share-settings', {teamId:'builders',serverId:'',name:''});
  const labels = {operator:'Admin commands available', server:'Server support detected',
    'client-only':'Client-only connection', offline:'Join a server to check'};
  const connection = state.source === 'local' && !state.syncError ? state.data.teamConnection : 'offline';
  modal('Shared storage', `<section class="sharing-setup">
    <div class="sharing-modes">
      <div><strong>${t('Client only')}</strong><p>${t('Install Stockpile in your own game to track chests you open and exchange team files. A server install is optional for these features.')}</p></div>
      <div><strong>${t('Client and Fabric server')}</strong><p>${t('Live team counts and shared reservations require the same Stockpile mod on the server and on each teammate’s client.')}</p></div>
    </div>
    <div class="sharing-status"><h3>${t('Live team storage')}</h3><span class="source-tag ${connection === 'server' || connection === 'operator' ? 'live' : ''}">
      <i class="dot"></i>${t(labels[connection] || labels.offline)}</span></div>
    <ol class="sharing-steps">
      <li><strong>${t('Install on the Fabric server')}</strong><p>${t('The server operator puts the matching Stockpile JAR and Fabric API in the server’s mods folder and restarts it. Players keep Stockpile on their own clients.')}</p></li>
      <li><strong>${t('Run the commands as an operator')}</strong><p>${t('Use the server console or an account with admin command permission. The chest must be in a loaded chunk. Use the example coordinates or replace them with your chest’s coordinates.')}</p>
        <pre dir="ltr">/stockpileteam create builders\n/stockpileteam add builders PlayerName\n/stockpileteam share builders 100 64 -25</pre>
        <p>${t('In the server console, omit the leading slash. Use /stockpile team on your client to open this setup guide.')}</p>
        <p>${t('If the command is missing, ask the operator to check the JAR on the server and your permission. In single-player, enable commands for the world.')}</p></li>
      <li><strong>${t('Choose the team workspace')}</strong><p>${t('Once the operator adds you, choose the team in the world dropdown. Shared storage updates while its chunks are loaded.')}</p></li>
    </ol>
  </section>
  <details class="sharing-manual"><summary>${t('Share a team file instead')}</summary>
    <p>${t('Agree on a team code and server label. Export the snapshot and send it to your teammates. Importing a matching file keeps the newest observation at each location.')}</p>
    <form id="shared-export-form">
      <div class="field"><label>${t('Team name')}<input id="share-name" maxlength="100" required value="${esc(saved.name)}"></label></div>
      <div class="field"><label>${t('Team code (letters, digits, _ or -)')}<input id="share-team" maxlength="64" pattern="[a-zA-Z0-9_\\-]+" required value="${esc(saved.teamId)}"></label></div>
      <div class="field"><label>${t('Server label agreed with teammates')}<input id="share-server" maxlength="100" required value="${esc(saved.serverId)}"></label></div>
      <button class="button primary" type="submit">${t('Export team file')}</button>
    </form><div class="modal-actions">${button('import','Import a teammate’s file','upload','small')}</div></details>
  <p class="small muted">${t('Personal Ender Chests are never shared. Team file exports also omit excluded chests.')}</p>`);
}

function allRows(){return inventoryRows(containers(),targets(),{includePacked:state.includePacked});}
function packedControl(){return `<label class="packed-control"><input id="include-packed" type="checkbox" ${state.includePacked?'checked':''}>${t('Include shulker contents')}</label>`;}
function scanNotes(){const missing=state.includePacked&&containers().some(c=>!c.packedItems&&c.items.some(i=>i.id.endsWith('shulker_box')));const incomplete=state.includePacked&&containers().some(c=>c.packedScanComplete===false);return `${missing?'<div class="storage-warning">This export has no shulker-content data for some chests. Update the mod and reopen those chests.</div>':''}${incomplete?'<div class="storage-warning">Some shulker contents could not be read completely. Check those containers before building.</div>':''}`;}
function projectList(){return [...state.projects,...(state.source==='demo'?demoProjects:[])];}
function project(){return projectList().find(p=>p.id===state.selectedProject)||projectList()[0];}
function recipeVersion(){return recipeCatalogs[state.data.minecraftVersion]&&!world().manualTeam?state.data.minecraftVersion:state.recipeVersion;}
function recipePlan(p=project(),copies=state.copies){
  if(!p)return {rows:[],steps:[],stockUsage:[],limited:false};
  const free=freeInventory(p),physical=aggregate(selectedSources(),{includePacked:state.includePacked}),withoutClaims=aggregate(freeContainers(selectedSources(),planning(),p.id,planningOwner()),{includePacked:state.includePacked});
  const plan=state.recipeSubstitutions?planMaterials(p.materials,free,copies,recipeCatalogs[recipeVersion()],{smelting:state.smelting,blast:state.blast,fuelIds:fuelIds()}):planMaterials(p.materials,free,copies,{minecraftVersion:recipeVersion(),recipes:[]});
  if(!state.recipeSubstitutions)plan.rows=plan.rows.map(({craftable,...row})=>row);
  if(!plan.stockUsage)plan.stockUsage=plan.rows.filter(r=>r.allocated).map(r=>({id:r.id,count:r.allocated}));
  plan.rows=plan.rows.map(r=>({...r,physical:physical.get(r.id)?.count||0,reserved:Math.max(0,(physical.get(r.id)?.count||0)-(withoutClaims.get(r.id)?.count||0))}));return plan;
}
function comparison(p=project()){return recipePlan(p).rows;}
function coverage(rows){const total=rows.reduce((a,r)=>a+r.needed,0),have=rows.reduce((a,r)=>a+r.allocated,0);return total?Math.floor(have/total*100):0;}
function ago(time){if(!time)return 'Never checked';const mins=Math.max(0,Math.floor((Date.now()-time)/60000));if(mins<1)return 'Just now';if(mins<60)return `${mins}m ago`;if(mins<1440)return `${Math.floor(mins/60)}h ago`;return `${Math.floor(mins/1440)}d ago`;}
function packedDropdown(paths) {
  if (!paths?.length) return '';
  const count = paths.reduce((total, entry) => total + entry.count, 0);
  return `<details class="packed-dropdown"><summary>${num(count)} packed · ${paths.length} ${paths.length === 1 ? 'location' : 'locations'}</summary>
    <ul aria-label="Packed item locations">${paths.map(entry => `<li>${num(entry.count)} inside ${esc(entry.path.join(' / '))}</li>`).join('')}</ul></details>`;
}
function dimensionLabel(dim){return localized(language,dim==='all'?'All dimensions':dim==='minecraft:overworld'?'Overworld':dim==='minecraft:the_nether'?'The Nether':dim==='minecraft:the_end'?'The End':humanize(dim));}
function statusLabel(status){return {low:'Low stock',healthy:'In stock',plenty:'Plenty',untracked:'No target'}[status];}
function stockBadge(status){return `<span class="stock ${status}"><i class="dot"></i>${t(statusLabel(status))}</span>`;}
function button(action,label,ico='',cls='',extra=''){return `<button class="button ${cls}" data-action="${action}" ${extra}>${ico?icon(ico,14):''}${t(label)}</button>`;}
function nav(view,label,ico,badge=''){return `<button class="nav-item ${state.view===view?'active':''}" data-action="nav" data-view="${view}">${icon(ico)}<span>${t(label)}</span>${badge?`<span class="badge-count">${badge}</span>`:''}</button>`;}
function choice(kind, options, value, label, compact = false) {
  const selected = options.find(option => option.value === String(value));
  const header = kind.startsWith('material-');
  return `<details class="choice ${compact ? 'choice-compact' : ''}${header ? ' material-choice' : ''}" data-choice="${kind}">
    <summary id="choice-${kind}" aria-label="${t(label)}"><span>${header ? t(label) : esc(selected?.label || localized(language, label))}</span>${icon('chevron', 11)}</summary>
    <div class="choice-options" aria-label="${t(label)}">
      ${options.map((option, index) => `<button type="button" id="choice-${kind}-${index}" class="choice-option"
        data-action="choose" data-kind="${kind}" data-value="${esc(option.value)}" aria-pressed="${option === selected}" ${option.disabled ? 'disabled' : ''}>
        <span dir="auto">${esc(option.label)}</span><span class="choice-check" aria-hidden="true">${option === selected ? '✓' : ''}</span></button>`).join('')}
    </div></details>`;
}

function chooseValue(kind, value) {
  if (kind === 'world') {
    if (!state.data.worlds.some(world => world.id === value)) return;
    state.mapDimension = '';
    state.mapViewport = null;
    state.mapFocus = [];
    state.worldId = value;
    state.dimension = 'all';
    state.query = '';
  } else if (kind === 'language' && Object.hasOwn(languages, value)) {
    language = value;
    writeLocal('stockpile-language', language);
    applyLanguage();
  } else if (kind === 'history' && ['1','7','30'].includes(value)) {
    state.historyDays = Number(value);
  } else if (kind.startsWith('material-') && Object.hasOwn(materialColumns, kind.slice(9)) &&
             ['ascending','descending','original'].includes(value)) {
    state.materialSort = value === 'original' ? null : {key:kind.slice(9), order:value};
  } else {
    return;
  }
  render();
  $(`#choice-${kind}`)?.focus();
}

function handleChoiceKey(event) {
  const menu = event.target.closest?.('details[data-choice]');
  if (!menu) return false;
  const summary = menu.querySelector('summary');
  if (event.key === 'Escape' && menu.open) {
    event.preventDefault();
    menu.open = false;
    summary.focus();
    return true;
  }
  if (!['ArrowDown', 'ArrowUp', 'Home', 'End'].includes(event.key)) return false;
  const options = [...menu.querySelectorAll('.choice-option:not(:disabled)')];
  if (!options.length) return false;
  event.preventDefault();
  menu.open = true;
  const index = options.indexOf(event.target);
  const next = event.key === 'Home' ? 0 : event.key === 'End' ? options.length - 1
    : index < 0 ? (event.key === 'ArrowUp' ? options.length - 1 : 0)
    : (index + (event.key === 'ArrowDown' ? 1 : -1) + options.length) % options.length;
  options[next].focus();
  return true;
}

function worldChoice() {
  const options = state.data.worlds.map(w => ({value:w.id, label:w.name}));
  if (!state.worldId) options.unshift({value:'', disabled:true, label:localized(language,
    state.data.connected ? 'Waiting for joined world…' : 'No world yet')});
  return choice('world', options, state.worldId, 'Selected world');
}

function languageChoice() {
  return choice('language', Object.entries(languages).map(([value, {name}]) => ({value, label:name})),
    language, 'Language', true);
}

function highlightButton(compact = false) {
  const available = state.source === 'local' && !state.syncError;
  return button('highlight-settings', 'Highlight settings', 'sliders',
    compact ? 'highlight-button small' : 'highlight-button', available ? '' : `disabled title="${t('Join Minecraft to change the outline.')}"`);
}

function render(){
  if(mapDrag?.moved&&mapDrag.svg.isConnected)return;
  const active=document.activeElement,focusId=active?.id,selection=active?.selectionStart;
  const openChoice = $('details[data-choice][open]')?.dataset.choice;
  const openPanels = [...(document.querySelectorAll?.('.planner-panel[open]') || [])].map(panel=>panel.id);
  const rows=allRows(),low=rows.filter(r=>r.status==='low').length;
  const title={overview:'Overview',inventory:'Inventory',ender:'Ender Chest',planner:'Build planner',containers:'Containers',low:'Low stock',history:'Stock history',map:'Storage map'}[state.view];
  $('#app').innerHTML=`<aside class="sidebar ${state.mobile?'open':''}" aria-label="${t('Main navigation')}"><a class="brand" href="./" aria-label="${t('Stockpile home')}"><img src="./logo.png" alt="Stockpile chest logo">Stockpile<small>BETA</small></a><div class="nav-label">${t('YOUR WORKSPACE')}</div><nav class="nav">${nav('overview','Overview','grid')}${nav('inventory','Inventory','layers')}${nav('ender','Ender Chest','ender')}${nav('planner','Build planner','hammer')}${nav('map','Storage map','pin')}${nav('containers','Containers','box')}${nav('history','Stock history','clock')}<div class="nav-label">${t('KEEP AN EYE ON')}</div>${nav('low','Low stock','alert',low||'')}</nav><div class="sidebar-bottom">${state.source==='local'?highlightButton(true):''}${button('sharing','Shared storage','box','quiet')}${button('connect','Connection','link','quiet')}<div class="side-foot"><i class="dot"></i>Fabric · Minecraft 26.2 / 26.3</div></div></aside><div class="workspace"><header class="topbar"><button class="mobile-toggle" data-action="mobile" aria-label="${t('Toggle navigation')}">${icon('menu',20)}</button><div class="breadcrumb">${icon('box',15)}<span class="muted">${t('Workspace')}</span><span class="muted">/</span><strong>${t(title)}</strong></div><div class="top-right"><span class="source-tag ${state.source==='local'&&!state.syncError?'live':''}"><i class="dot"></i>${t(state.source==='demo'?'Example data':state.source==='import'?'Imported snapshot':state.syncError?'Disconnected':state.data.trackingEnabled===false?'Tracking paused':state.data.connected?'Tracking active':'World offline')}</span>${worldChoice()}${languageChoice()}</div></header><main id="main" class="main">${persistenceError?'<div class="storage-warning">Browser storage is unavailable. Export important changes before closing.</div>':''}${state.source==='local'&&state.data.trackingEnabled===false?'<div class="storage-warning">Personal tracking is paused. Personal counts are saved snapshots; connected team workspaces continue receiving server updates. Run /stockpile start in Minecraft to resume.</div>':''}${state.syncError?`<div class="storage-warning">${icon('alert',15)}${esc(state.syncError)} ${button('connect','Reconnect','','small quiet')}</div>`:''}${state.source==='demo'?`<div class="example-note">${icon('info',13)}<span>${t('Example inventory.')}
  <button data-action="connect">${t('Connect your mod')}</button> · <button data-action="import">${t('Import inventory')}</button></span></div>`:state.source==='import'?`<div class="example-note">${icon('clock',13)}<span>Inventory snapshot · ${esc(new Date(state.data.capturedAt||Date.now()).toLocaleString())}. Import a newer export to refresh it.</span></div>`:''}${savedWorldNotice()}${scanNotes()}${['map','history','planner'].includes(state.view)?'':staleControls()}${state.view==='map'?renderChestMap():state.view==='history'?renderHistory():state.view==='planner'?renderPlanner():state.view==='containers'?renderContainers():renderInventoryPage(rows,low)}<footer class="footer"><span>${state.source==='local'?t('Local dashboard'):t('Your browser')} · Counts reflect last-opened containers. ${button('about','About the counts','','quiet small')} ${button('licenses','Licenses','','quiet small')} <a class="button quiet small" href="https://github.com/danzaa/StockPile/issues/new/choose" target="_blank" rel="noopener noreferrer">${t('GitHub support')}</a></span></footer></main></div><input type="file" class="hidden" id="inventory-file" accept=".json,application/json" aria-label="Import inventory snapshot"><input type="file" class="hidden" id="schematic-file" accept=".litematic,.schem,.nbt,.json,.csv" aria-label="Upload schematic">`;
  for (const id of openPanels) {
    const panel = $(`#${id}`);
    if (panel) panel.open = true;
  }
  if (openChoice) {
    const menu = $(`details[data-choice="${openChoice}"]`);
    if (menu) menu.open = true;
  }
  if(focusId){const field=document.getElementById(focusId);if(field){field.focus({preventScroll:true});if(typeof selection==='number'&&field.type!=='number')try{field.setSelectionRange(selection,selection);}catch{}}}
  const drop=$('#dropzone');if(drop){drop.addEventListener('dragover',e=>{e.preventDefault();drop.classList.add('dragging');});drop.addEventListener('dragleave',()=>drop.classList.remove('dragging'));drop.addEventListener('drop',e=>{e.preventDefault();drop.classList.remove('dragging');if(e.dataTransfer.files.length)uploadSchematic(e.dataTransfer.files[0]);});}
}
function renderInventoryPage(rows,low){
  const overview=state.view==='overview',lowView=state.view==='low';
  const total=rows.reduce((s,r)=>s+r.count,0);
  return `<div class="title-row"><div><h1>${overview?t('Overview'):lowView?t('Low stock'):esc(scopeLabel())}</h1><p>${t(overview?'Item counts, chest locations and build materials.':lowView?'Items below your minimum stock targets.':'Search item names or IDs to find their storage locations.')}</p></div><div class="button-row">${button('export','Export inventory','download','quiet')}${!local?button('import','Import inventory','upload'):''}</div></div>${containerControls()}${overview?`<div class="stats"><div class="stat"><div class="stat-label">${t('Total items')} ${icon('layers',16)}</div><div class="stat-value">${num(total)}</div><div class="stat-foot">${t('Loose items and included shulker contents')}</div></div><div class="stat"><div class="stat-label">${t('Item types')} ${icon('cube',16)}</div><div class="stat-value">${num(rows.filter(r=>r.count>0).length)}</div><div class="stat-foot">${t('Distinct item IDs in storage')}</div></div><div class="stat"><div class="stat-label">${t('Containers')} ${icon('box',16)}</div><div class="stat-value">${num(containers().length)}</div><div class="stat-foot">${t('{count} dimensions · last observed',{count:num(new Set(containers().map(c=>c.dimension)).size)})}</div></div><div class="stat warning"><div class="stat-label">${t('Running low')} ${icon('alert',16)}</div><div class="stat-value">${num(low)}</div><div class="stat-foot">${t(low?'Items below minimum targets':'No items below minimum targets')}</div></div></div>`:''}<section class="table-section"><div class="section-head"><h2>${t(overview?'Inventory':lowView?'Below target':'Items')} <span class="count-label">${num(lowView?low:rows.length)} ${t('types')}</span></h2>${button('targets','Stock targets','sliders','quiet small')}</div>${renderInventoryTable(rows,overview,lowView)}</section>${overview?renderBottom():''}`;
}
function dimensionOptions(){return `<select id="dimension" class="control-select" aria-label="${t('Dimension')}"><option value="all">${t('All dimensions')}</option>${[...new Set(world().containers.map(c=>c.dimension))].map(d=>`<option value="${esc(d)}" ${state.dimension===d?'selected':''}>${esc(dimensionLabel(d))}</option>`).join('')}</select>`;}
function renderInventoryTable(all,overview,lowView){
  let rows=all.filter(r=>(!lowView||r.status==='low')&&(state.filter==='all'||r.status===state.filter)&&(!state.query||`${r.name||humanize(r.id)} ${r.id}`.toLowerCase().includes(state.query.toLowerCase())));
  rows.sort((a,b)=>state.sort==='name'?(a.name||humanize(a.id)).localeCompare(b.name||humanize(b.id)):state.sort==='low'?(a.count/(a.target||1)-b.count/(b.target||1)):b.count-a.count);
  const filteredCount=rows.length,limit=overview?7:state.limit;rows=rows.slice(0,limit);
  return `<div class="table-card"><div class="table-tools"><label class="search">${icon('search',15)}<input id="inventory-search" type="search" placeholder="${t('Search items or an item ID…')}" value="${esc(state.query)}" aria-label="${t('Search items')}"><kbd>/</kbd></label><div class="filter-group">${packedControl()}${!lowView?`<div class="tabs" role="group" aria-label="${t('Stock status filter')}">${['all','low','plenty'].map(f=>`<button class="tab ${state.filter===f?'active':''}" data-action="filter" data-filter="${f}" aria-pressed="${state.filter===f}">${t({all:'All items',low:'Low stock',plenty:'Plenty'}[f])}</button>`).join('')}</div>`:`<span class="small muted">${t('Based on your stock targets')}</span>`}${dimensionOptions()}<select id="sort" class="control-select" aria-label="${t('Sort items')}"><option value="count" ${state.sort==='count'?'selected':''}>${t('Most stocked')}</option><option value="name" ${state.sort==='name'?'selected':''}>${t('Name A–Z')}</option><option value="low" ${state.sort==='low'?'selected':''}>${t('Lowest stock')}</option></select></div></div><div class="table-wrap"><table><thead><tr>${['Item','Quantity','Status','Stock target','Location'].map(label=>`<th class="upper-label">${t(label)}</th>`).join('')}</tr></thead><tbody>${rows.map(r=>`<tr><td><div class="item-cell">${itemImage(r.id)}<div><button class="item-button" data-action="details" data-id="${esc(r.id)}">${esc(r.name||humanize(r.id))}</button><div class="item-sub mono">${esc(r.id)}</div></div></div></td><td><span class="number">${num(r.count)}</span><span class="stacks">${t('{count} stacks',{count:num(Math.floor(r.count/(r.maxStack||64)))})}${r.count%(r.maxStack||64)?' + '+r.count%(r.maxStack||64):''}</span>${r.packedCount?`<span class="packed-note">${t('{count} inside packed shulkers',{count:num(r.packedCount)})}</span>`:''}${r.placedShulkerCount?`<span class="placed-note">${t('{count} in placed shulkers',{count:num(r.placedShulkerCount)})}</span>`:''}</td><td>${stockBadge(r.status)}</td><td><div class="target-cell ${r.status}">${r.target?`<progress value="${Math.min(r.count,r.target)}" max="${r.target}" aria-label="${t('Stock target progress')}"></progress>`:''}<button class="target-button" data-action="details" data-id="${esc(r.id)}">${r.target?num(r.target):`+ ${t('Set target')}`}</button></div></td><td><button class="location-button" data-action="details" data-id="${esc(r.id)}">${icon('pin',12)}${r.locations.length?t('{count} containers',{count:num(r.locations.length)}):t('Not in storage')}${icon('chevron',11)}</button></td></tr>`).join('')||`<tr><td colspan="5"><div class="empty">${icon(lowView?'check':'search',25)}<h3>${t(lowView?'No low-stock items.':!all.length?'No tracked items.':'No matching items.')}</h3><p>${lowView?'Set stock targets for items you want to keep on hand.':!all.length?'Open chests in Minecraft or import an inventory export.':'Try another item name, stock filter or dimension.'}</p>${lowView?button('targets','Set stock targets','plus','small'):!all.length?button('connect','Connect Minecraft','link','small'):button('reset-filters','Clear filters','','small')}</div></td></tr>`}</tbody></table></div><div class="table-foot"><span>${t('Showing {shown} of {total} item types',{shown:num(rows.length),total:num(filteredCount)})} · ${esc(dimensionLabel(state.dimension))}</span>${overview?button('inventory','View inventory','arrow','quiet small'):filteredCount>state.limit?button('more','Show more','plus','quiet small'):`<span>${t('Last observed counts')}</span>`}</div></div>`;
}
function renderBottom(){
  const projects=projectList().slice(0,3);
  return `<section class="saved-builds"><div class="section-head"><h2>${t('Saved builds')}</h2>${button('planner','Build planner','hammer','quiet small')}</div><div class="panel">${projects.length?projects.map(p=>{const rows=recipePlan(p,1).rows,percent=coverage(rows);return `<div class="project-mini"><div class="project-art">${icon('hammer',25)}</div><button data-action="project" data-id="${esc(p.id)}"><h3>${esc(p.name)}${p.sample?`<span class="label-badge">${t('Example')}</span>`:''}</h3><p>${t('{count} material types',{count:num(p.materials.length)})} · ${t('{count} items',{count:num(rows.reduce((s,r)=>s+r.needed,0))})}</p></button><div class="project-percent">${percent}%<small>${t('Available')}</small></div></div>`;}).join(''):`<div class="empty"><p>No saved builds.</p>${button('upload','Upload schematic','upload','small')}</div>`}</div></section>`;
}
function recipeControls() {
  const fromGame = recipeCatalogs[state.data.minecraftVersion] && !world().manualTeam;
  const fuels = [['coals','Coal + charcoal'],['all','All supported fuels'],['minecraft:coal','Coal'],['minecraft:charcoal','Charcoal'],
    ['minecraft:coal_block','Coal blocks'],['minecraft:blaze_rod','Blaze rods'],['minecraft:dried_kelp_block','Dried kelp blocks'],['minecraft:lava_bucket','Lava buckets']];
  return `<section class="recipe-controls" aria-label="${t('Recipe substitutions')}">
    <div class="recipe-control-row">
      <label class="switch-line"><input id="recipe-substitutions" role="switch" type="checkbox" ${state.recipeSubstitutions?'checked':''}>${t('Recipe substitutions')}</label>
      <label class="switch-line"><input id="smelting-enabled" role="switch" type="checkbox" ${state.smelting?'checked':''}
        ${state.recipeSubstitutions?'':'disabled'}>${t('Smelting & fuel')}</label>
      <label><input id="blast" type="checkbox" ${state.blast?'checked':''} ${state.recipeSubstitutions && state.smelting?'':'disabled'}>${t('Allow blast furnace')}</label>
      <label>${t('Fuel')} <select id="fuel-choice" ${state.recipeSubstitutions && state.smelting?'':'disabled'}>
        ${fuels.map(([id,label])=>`<option value="${id}" ${state.fuelChoice===id?'selected':''}>${t(label)}</option>`).join('')}
      </select></label>
      ${fromGame?`<span class="small muted">${t('Vanilla {version} recipes',{version:recipeVersion()})}</span>`
        :`<label class="recipe-version">${t('Minecraft recipes')} <select id="recipe-version" class="control-select">
        ${Object.keys(recipeCatalogs).map(version=>`<option value="${version}" ${version===recipeVersion()?'selected':''}>${version}</option>`).join('')}
      </select></label>`}
    </div><p>${t(state.recipeSubstitutions?'Counts what you can craft from the selected storage. Directly required items are reserved first.'
      :'Checks finished materials already in storage. Turn on to include what you can craft.')}</p>
  </section>`;
}
function renderCraftingPlan(plan){
  if(!state.recipeSubstitutions)return '';
  return `<section id="crafting-plan" class="crafting-plan"><div class="section-head"><h2>${t('Crafting plan')} <span class="count-label">${t('{count} steps',{count:num(plan.steps.length)})}</span></h2>${button('crafting-csv','Export crafting steps','download','quiet small',plan.steps.length?'':'disabled')}</div><p class="recipe-note">${t('Follow these steps in order. Fuel and ingredients come from your selected storage.')}</p>${plan.limited?`<div class="warning-note">${t('Some recipe paths reached the search limit. Craftable totals may be lower than what is possible.')}</div>`:''}${plan.steps.length?`<div class="table-card"><div class="table-wrap"><table><thead><tr>${['Step','Make','Use','Workstation'].map(label=>`<th class="upper-label">${t(label)}</th>`).join('')}</tr></thead><tbody>${plan.steps.map((step,i)=>`<tr><td class="number">${i+1}</td><td><div class="item-cell">${itemImage(step.output)}<div><strong>${num(step.count)} ${esc(humanize(step.output))}</strong><div class="item-sub">${t('{count} batches',{count:num(step.batches)})}</div></div></div></td><td><div class="recipe-ingredients">${[...step.ingredients,...(step.fuels||[]).map(i=>({...i,fuel:true}))].map(item=>`<span>${itemArt(item.id)}${num(item.count)} ${esc(humanize(item.id))}${item.fuel?` (${t('Fuel')})`:''}</span>`).join('')}</div>${(step.remainders||[]).map(i=>`<p>${t('Returns {count} {item}',{count:num(i.count),item:humanize(i.id)})}</p>`).join('')}</td><td>${esc(humanize(step.station))}${step.ticks?`<p>${t('{duration} seconds · one workstation',{duration:num(Math.round(step.ticks/2)/10)})}</p>`:''}</td></tr>`).join('')}</tbody></table></div></div>`:`<div class="recipe-empty">${t(plan.rows.some(r=>r.missing)?'No craftable substitutions found in the selected storage.':'All required items are already in storage.')}</div>`}</section>`;
}
const materialColumns = {
  name:'Material',
  needed:'Needed',
  physical:'Physical stock',
  reserved:'Reserved elsewhere',
  available:'Free stock',
  craftable:'Can craft',
  missing:'Missing'
};

function materialHeading(key) {
  const active = state.materialSort?.key === key;
  const order = active ? state.materialSort.order : 'original';
  const labels = key === 'name' ? ['A to Z','Z to A'] : ['Lowest first','Highest first'];
  const options = ['ascending','descending','original'].map((value, i) => ({
    value, label:localized(language, i === 2 ? 'Original order' : labels[i])
  }));
  return `<th scope="col" ${active ? `aria-sort="${order}"` : ''}>${choice(`material-${key}`,options,order,materialColumns[key])}</th>`;
}

function visibleMaterials(rows) {
  const visible = rows.filter(row => !state.missingOnly || row.missing);
  const sort = state.materialSort;
  if (!sort || (sort.key === 'craftable' && !state.recipeSubstitutions)) return visible;
  const collator = new Intl.Collator(languages[language].locale,{numeric:true,sensitivity:'base'});
  const factor = sort.order === 'ascending' ? 1 : -1;
  return visible.sort((a,b) => factor * (sort.key === 'name'
    ? collator.compare(a.name || humanize(a.id), b.name || humanize(b.id))
    : (a[sort.key] ?? 0) - (b[sort.key] ?? 0)));
}

const materialChecklistKey = projectId => `stockpile-material-checklist:${projectId}`;

function setMaterialCollected(id, collected) {
  const p = project();
  const material = p?.materials.find(row => row.id === id);
  if (!material || state.view !== 'planner') return;
  const key = materialChecklistKey(p.id);
  const workspaces = readLocal(key, {});
  const checklist = {...workspaces[state.worldId]};
  if (collected) checklist[id] = material.count * state.copies;
  else delete checklist[id];
  writeLocal(key, {...workspaces, [state.worldId]:checklist});
  render();
}

function renderPlannerMaterials(p, rows) {
  const columns = Object.keys(materialColumns).filter(key => key !== 'craftable' || state.recipeSubstitutions);
  const visible = visibleMaterials(rows);
  const checklist = readLocal(materialChecklistKey(p.id), {})[state.worldId] ?? {};
  const collected = new Set(rows.filter(r => Number.isSafeInteger(checklist[r.id]) && checklist[r.id] >= r.needed).map(r => r.id));
  return `<div class="section-head"><h2>${t('Material checklist')}
    <span class="count-label">${t('{checked} of {total} checked',{checked:num(collected.size),total:num(rows.length)})}</span></h2>
    ${button('missing-csv','Missing materials','download','quiet small')}</div>
  <div class="table-card">
    <div class="materials-tools"><div class="materials-toggle-group">${packedControl()}
      <label class="material-filter"><input type="checkbox" id="missing-only" ${state.missingOnly?'checked':''}>${t('Only show missing materials')}</label>
    </div>${dimensionOptions()}</div>
    <div class="table-wrap"><table class="materials-table"><thead><tr>
      <th scope="col" class="upper-label collected-cell">${t('Collected')}</th>
      ${columns.map(materialHeading).join('')}<th scope="col" class="upper-label">${t('Storage')}</th>
    </tr></thead><tbody>
      ${visible.map(r=>`<tr class="${collected.has(r.id)?'material-collected':''}">
        <td class="collected-cell"><label class="material-checkbox"><input type="checkbox" id="collected-${esc(r.id)}"
          data-material-check="${esc(r.id)}" aria-label="${t('Collected {count} × {item}',{count:num(r.needed),item:r.name||humanize(r.id)})}"
          ${collected.has(r.id)?'checked':''}></label></td>
        <td><div class="item-cell">${itemImage(r.id)}<button class="item-button" data-action="details" data-id="${esc(r.id)}">${esc(r.name||humanize(r.id))}</button></div></td>
        <td class="number">${num(r.needed)}</td><td class="number">${num(r.physical)}</td>
        <td class="number">${num(r.reserved)}</td><td class="number">${num(r.available)}</td>
        ${state.recipeSubstitutions?`<td class="number craftable-count">${num(r.craftable)}</td>`:''}
        <td class="number ${r.missing?'material-missing':'material-check'}">${r.missing?num(r.missing):icon('check',15)}</td>
        <td>${!r.available&&r.craftable
          ? `<a class="location-button" href="#crafting-plan">${t('Crafting plan')}</a>`
          : `<button class="location-button" data-action="details" data-id="${esc(r.id)}">${icon('pin',12)}${t('Find item')}${icon('chevron',10)}</button>`}</td></tr>`).join('') ||
        `<tr><td colspan="${columns.length+2}"><div class="empty">${t('All listed materials are covered.')}</div></td></tr>`}
    </tbody></table></div>
    <div class="table-foot"><span>${t(state.source==='demo'?'Compared with example inventory':'Based on last-observed chest contents')}</span>
      <span>${t(state.recipeSubstitutions?'Includes the crafting plan below':'Direct materials · recipe substitutions off')}</span></div>
  </div>`;
}

function positionMaterialMenu(menu) {
  const panel = menu.querySelector('.choice-options');
  const anchor = menu.querySelector('summary').getBoundingClientRect();
  const scroller = menu.closest('.table-wrap').getBoundingClientRect();
  const {clientWidth,clientHeight,dir} = document.documentElement;
  if (anchor.right <= scroller.left || anchor.left >= scroller.right || anchor.bottom < 0 || anchor.top > clientHeight) {
    menu.open = false;
    return;
  }
  const left = dir === 'rtl' ? anchor.right - panel.offsetWidth : anchor.left;
  const top = anchor.bottom + panel.offsetHeight + 8 > clientHeight
    ? Math.max(8,anchor.top - panel.offsetHeight - 5)
    : anchor.bottom + 5;
  panel.style.left = `${Math.max(8,Math.min(left,clientWidth-panel.offsetWidth-8))}px`;
  panel.style.top = `${top}px`;
}

function plannerPanel(id, label, body, opened = false) {
  return `<details id="${id}" class="planner-panel" ${opened ? 'open' : ''}>
    <summary>${t(label)}${icon('chevron',11)}</summary>
    <div class="planner-panel-body">${body}</div>
  </details>`;
}

function plannerProjects(selected) {
  const projects = projectList();
  return `<aside class="planner-sidebar">
    <div class="planner-projects"><h2>${t('Saved builds')}</h2>
      <div class="project-list">
        ${projects.map(build=>`<button class="project-entry ${selected?.id===build.id?'active':''}" data-action="project"
          data-id="${esc(build.id)}" aria-pressed="${selected?.id===build.id}">${icon('file',17)}<span>${esc(build.name)}
          <small>${t('{count} material types',{count:num(build.materials.length)})}${build.sample?` · ${t('Example')}`:''}</small></span></button>`).join('')
          || `<p class="small muted">${t('No saved builds.')}</p>`}
      </div>
    </div>
    <div id="dropzone" class="dropzone" role="button" tabindex="0" aria-label="${t('Upload or drop a schematic file')}">
      ${icon('upload',20)}<strong>${t(state.busy?'Reading file…':'Upload a schematic')}</strong>
      <small>${t('Drop a file here or click to browse.')}</small>
    </div>
  </aside>`;
}

function renderBuildSummary(p, rows) {
  const total = rows.reduce((sum,row)=>sum+row.needed,0);
  const short = rows.reduce((sum,row)=>sum+row.missing,0);
  const craftable = rows.reduce((sum,row)=>sum+(row.craftable||0),0);
  const missing = rows.filter(row=>row.missing).length;
  const readiness = missing
    ? t('{count} missing across {types} material types',{count:num(short),types:num(missing)})
    : p.warnings?.length ? t('Listed materials covered · review the notes')
    : craftable ? t('Materials covered · craft {count} items first',{count:num(craftable)})
    : t('All listed materials in storage');
  return `<div class="planner-intro"><div class="planner-title"><div><h2>${esc(p.name)}</h2>
    <p>${esc(p.filename||localized(language,'Material list'))} · ${t('{count} items',{count:num(total)})}
      · ${esc(dimensionLabel(state.dimension))}${p.sample?` · ${t('Example')}`:''}</p></div>
    <div class="build-actions">
      ${button('export-materials','','download','quiet icon-only',`aria-label="${t('Export material checklist')}" title="${t('Export material checklist')}"`)}
      ${!p.sample?button('delete-project','','trash','quiet icon-only',`aria-label="${t('Delete build')}" title="${t('Delete build')}"`):''}
    </div></div>
    <div class="progress-head"><span>${t('{covered} of {total} materials covered',{covered:num(total-short),total:num(total)})}</span>
      <strong>${num(coverage(rows))}%</strong></div>
    <progress value="${total-short}" max="${total||1}" aria-label="${t('Materials available')}"></progress>
    <div class="planner-bottom"><span class="readiness ${missing||p.warnings?.length?'short':''}">
      ${icon(missing?'alert':p.warnings?.length?'info':'check',14)}${readiness}</span>
      <label class="copies">${t('Build copies')} <input type="number" id="copies" min="1" max="999" step="1" value="${state.copies}"></label></div>
  </div>`;
}

function renderPlanner() {
  const p = project();
  const plan = recipePlan(p);
  const staging = schematicChestPanel(p);
  return `<div class="title-row"><div><h1>${t('Build materials')}</h1>
    <p>${t('Compare schematic requirements with your stored items.')}</p></div></div>
    ${mapControls()}
    ${plannerPanel('planner-source-options','Source filters',containerControls()+staleControls())}
    <div class="planner-layout">${plannerProjects(p)}<section class="planner-main">
      ${state.uploadError?`<div class="warning-note" role="alert">${esc(state.uploadError)}</div>`:''}
      ${p ? `${renderBuildSummary(p,plan.rows)}
        ${p.warnings?.length?`<div class="warning-note"><strong>${t('Check these before building')}</strong><ul>${p.warnings.map(w=>`<li>${esc(w)}</li>`).join('')}</ul></div>`:''}
        ${plannerPanel('planner-recipe-options','Crafting options',recipeControls())}
        ${renderPlannerMaterials(p,plan.rows)}
        ${renderCraftingPlan(plan)}`
        : `<div class="empty large">${icon('hammer',37)}<h3>${t('No schematic loaded.')}</h3>
          <p>${t('Upload a schematic to compare its materials with your storage.')}</p>
          ${button('upload','Choose schematic','upload','primary')}</div>`}
      <div class="planner-tools">
        ${plannerPanel('planner-reservations','Build reservations',reservationPanel(p))}
        ${staging ? plannerPanel('planner-schematic-chest','Schematic chest',staging) : ''}
      </div>
    </section></div>`;
}

function renderContainers(){
  const list=rawContainers().filter(c=>!state.query||`${c.name} ${c.position.join(' ')} ${c.dimension}`.toLowerCase().includes(state.query.toLowerCase()));
  return `<div class="title-row"><div><h1>${t('Containers')}</h1><p>${t('{count} remembered containers in {world}.',{count:num(rawContainers().length),world:world().name})}</p></div>${button('export','Export inventory','download')}</div>${containerControls()}<div class="table-tools"><label class="search">${icon('search',15)}<input id="inventory-search" type="search" placeholder="${t('Search chests or coordinates…')}" value="${esc(state.query)}" aria-label="${t('Search containers')}"></label>${dimensionOptions()}</div><div class="example-note">${icon('info',13)}Move or break a chest? Remove its old record here, then open it in its new location.</div><div class="container-grid">${list.map(c=>`<article class="container-card ${chestExcluded(c,exclusions())?'excluded':''}"><div class="container-top">${icon(containerKind(c)==='ender'?'ender':containerKind(c)==='shulker'?'shulker':containerKind(c)==='barrel'?'barrel':'box',20)}<h3>${esc(c.name)}</h3>${chestExcluded(c,exclusions())?'<span class="placed-badge">EXCLUDED</span>':''}${containerKind(c)==='shulker'?'<span class="placed-badge">PLACED</span>':''}</div><p>${esc(dimensionLabel(c.dimension))} · ${esc(originLabel(c))}</p><div class="coord">${esc(c.position.join(' / '))}${button('copy-coord','','copy','quiet icon-only',`data-pos="${esc(c.position.join(' '))}" aria-label="Copy coordinates"`)}</div><p>${num([...aggregate([c],{includePacked:state.includePacked}).values()].reduce((s,i)=>s+i.count,0))} items · ${aggregate([c],{includePacked:state.includePacked}).size} types${c.packedItems?.length?' · shulker contents tracked':''}</p><div class="container-icons">${c.items.slice(0,6).map(i=>itemImage(i.id)).join('')||'<span class="small muted">Empty when last opened</span>'}</div><div class="button-row">${mapHighlight(c)}${exclusionButton(c)}</div><div class="container-foot"><span class="${stale(c,state.staleHours)?'stale':''}">${stale(c,state.staleHours)?'May be outdated · ':''}Last checked ${ago(c.lastSeen)}</span>${!world().shared?button('forget','','trash','quiet small',`data-id="${esc(c.id)}" aria-label="Remove saved container"`):''}</div></article>`).join('')||`<div class="empty"><h3>${t('No containers found.')}</h3><p>Open storage containers in Minecraft to remember them.</p></div>`}</div>`;
}
function modal(title,body){const el=$('#modal');el.innerHTML=`<div class="modal-head"><h2>${localized(language,title)}</h2>${button('close','','close','quiet icon-only',`aria-label="${t('Close dialog')}"`)}</div><div class="modal-body">${body}</div>`;if(!el.open)el.showModal();}
function connect(){modal('Minecraft connection',`<p>Stockpile is a client-side Fabric mod for <strong>Minecraft Java 26.2 and 26.3</strong>.</p><ol><li>Install Fabric Loader <strong>0.20.1+</strong> and Fabric API for your Minecraft version.</li><li>Choose the matching Stockpile JAR below. Replace any older Stockpile JAR in your Minecraft <code>mods</code> folder, then launch the game.</li><li>Join your world and open a few chests. Run <code>/stockpile open</code> to open the dashboard now. <code>/stockpile start</code> resumes tracking and prints a link in chat. You can also use <strong>Copy link</strong> and paste the address into your browser. Automatic browser opening is off by default.</li></ol><div class="button-row">${state.source==='local'?button('close','Close','check','primary'):`<a class="button primary" href="./downloads/stockpile-0.3.4+26.2.jar" download>${icon('download',14)}Download for 26.2</a><a class="button" href="./downloads/stockpile-0.3.4+26.3.jar" download>${icon('download',14)}Download for 26.3</a>${button('import','Import inventory','upload')}`}</div><p class="small muted">Fabric API: <a href="https://modrinth.com/mod/fabric-api/versions?g=26.2" target="_blank" rel="noopener">26.2 versions</a> · <a href="https://modrinth.com/mod/fabric-api/versions?g=26.3" target="_blank" rel="noopener">26.3 versions</a>. Install one matching Stockpile JAR.</p><div class="modal-note">The connected dashboard runs on the same computer as Minecraft. For this hosted page, use <strong>Export inventory</strong> in the local dashboard, then import the JSON here. Hosted imports are snapshots; they do not sync automatically.<br><br>Inventory and schematic files stay in your browser or on your computer. No Minecraft login is needed.<br><br><code>/stockpile settings</code> controls the startup message, shortcut and automatic browser opening. <code>/stop</code> and <code>/stockpile stop</code> pause local tracking.</div><div class="modal-note"><strong>Build status:</strong> compiled against each supported Minecraft version. In-game behavior still needs a playtest.</div>`);}
function findButton(c,id){
  const reason=world().manualTeam?'Manual team files provide coordinates only.':findUnavailable({source:state.source,connected:state.data.connected,syncError:state.syncError});
  return button('find-game','Highlight chest','pin','small',`data-id="${esc(id)}" data-container="${esc(c.id)}" data-world="${esc(state.worldId)}" data-packed="${state.includePacked}" ${reason?`disabled title="${esc(reason)}"`:''}`);
}
function details(id){
  const row=allRows().find(r=>r.id===id)||{id,name:humanize(id),count:0,target:targets()[id]||0,locations:[]};
  modal(esc(row.name||humanize(id)),`<div class="details-summary">${itemImage(id)}<div><h3>${num(row.count)}</h3><p>items in ${row.locations.length} ${row.locations.length===1?'container':'containers'} · ${esc(dimensionLabel(state.dimension))}</p></div></div><p class="mono">${esc(id)}</p><p class="small muted">${esc(findUnavailable({source:state.source,connected:state.data.connected,syncError:state.syncError})||'Highlight chest shows this location in Minecraft. Change the outline in Storage map → Highlight settings.')}</p>${row.packedCount?`<p class="packed-summary">${num(row.looseCount||0)} loose · ${num(row.packedCount)} inside shulker boxes</p>`:''}${row.locations.map(c=>`<div class="detail-location"><div><h3>${esc(c.name)} ${c.placed?'<span class="placed-badge">PLACED SHULKER</span>':''}</h3><p>${esc(originLabel(c))} · ${esc(dimensionLabel(c.dimension))} · <span class="mono">${esc(c.position.join(' / '))}</span></p>${c.looseCount?`<p>${num(c.looseCount)} loose in this container</p>`:''}${packedDropdown(c.packedPaths)}<p>${stale(c,state.staleHours)?'May be outdated · ':''}Checked ${ago(c.lastSeen)}</p></div><div class="right"><span class="number">${num(c.count)}</span>${findButton(c,id)}${exclusionButton(c)}${button('copy-coord','Copy coords','copy','quiet small',`data-pos="${esc(c.position.join(' '))}"`)}</div></div>`).join('')||'<p>This item has not been observed in your selected storage.</p>'}<form class="target-form" id="target-form" data-id="${esc(id)}"><div><div class="field"><label for="target-amount">Low-stock target (0 turns tracking off)</label><input id="target-amount" type="number" min="0" max="${MAX_COUNT}" step="1" required value="${row.target}"></div><button class="button primary" type="submit">Save target</button></div></form>`);
}
function showTargets(){const t=targets();modal('Stock targets',`<p>Choose a minimum quantity for each supply. Items below it show as low stock; twice the target counts as plenty.</p><form id="targets-form">${Object.entries(t).filter(([,n])=>n>0).map(([id,n])=>`<label class="targets-row"><span>${itemImage(id)}</span><span class="target-label">${esc(humanize(id))}</span><input type="number" min="0" max="${MAX_COUNT}" step="1" value="${n}" data-target-id="${esc(id)}" aria-label="${esc(humanize(id))} minimum"></label>`).join('')||'<p>No stock targets yet. Add your first essential below.</p>'}<div class="field new-target"><label for="new-target-id">Add another item ID</label><input id="new-target-id" placeholder="minecraft:iron_ingot" pattern="[a-z0-9_.-]+:[a-z0-9_./-]+"></div><div class="field"><label for="new-target-count">Minimum quantity</label><input id="new-target-count" type="number" min="1" max="${MAX_COUNT}" step="1" value="64"></div><div class="modal-actions">${button('close','Cancel','','quiet')}<button type="submit" class="button primary">Save stock targets</button></div></form>`);}
function download(name,content,type='application/json'){const url=URL.createObjectURL(new Blob([content],{type}));const a=document.createElement('a');a.href=url;a.download=name;a.click();setTimeout(()=>URL.revokeObjectURL(url),1000);}
function navigate(view){if(view==='ender'){state.containerKinds=['ender'];state.dimension='all';}else if(view==='inventory'){state.containerKinds=readLocal('stockpile-storage-kinds',STORAGE_KINDS).filter(k=>STORAGE_KINDS.includes(k));if(!state.containerKinds.length)state.containerKinds=[...STORAGE_KINDS];}writeLocal('stockpile-container-kinds',state.containerKinds);state.view=view;state.query='';state.filter='all';state.limit=20;state.mobile=false;render();window.scrollTo({top:0,behavior:'instant'});}
async function copy(text){try{await navigator.clipboard.writeText(text);toast('Coordinates copied.');}catch{modal('Chest coordinates',`<p>Select and copy:</p><div class="field"><input readonly value="${esc(text)}" aria-label="Coordinates"></div>`);}}
async function api(path,options={}){const r=await fetch(path,{cache:'no-store',...options,headers:{'X-Stockpile-Token':token,...options.headers}});if(!r.ok){const body=await r.json().catch(()=>null);throw new Error(r.status===401?'Run /stockpile start in Minecraft and click its new dashboard link.':body?.message||`Minecraft connection returned ${r.status}.`);}return r.json();}
let syncing=false;
async function sync(){
  if(!local||syncing)return;syncing=true;
  try{
    const data=validateSnapshot(await api('/api/snapshot'));
    let changed=JSON.stringify(data)!==JSON.stringify(state.data)||state.syncError;
    const selection=selectLiveWorld(state.data,data,state.worldId);
    state.data=data;state.source='local';state.syncError='';state.worldId=selection.worldId;
    if(selection.reset){mapDrag=null;state.mapDimension='';state.mapViewport=null;state.mapFocus=[];state.dimension='all';state.query='';state.filter='all';state.limit=20;$('#modal')?.close();}
    if (data.terrainRevision !== undefined && data.terrainRevision !== terrainRevision) {
      try {
        const captures = await api('/api/map/terrain/recent');
        if (Array.isArray(captures)) {
          if (captures.length) rememberTerrain(captures.slice(0, 8));
          terrainRevision = data.terrainRevision;
          changed = true;
        }
      } catch { /* The next poll can retry terrain independently. */ }
    }
    if(changed)render();
    if(openSharingOnSync){openSharingOnSync=false;sharing();}
    if(!state.registry){const registry=await api('/api/registry');if(registry&&Object.keys(registry).length)state.registry=registry;}
  }catch(e){if(state.syncError!==e.message){state.syncError=e.message;render();}}
  finally{syncing=false;}
}
async function importInventory(file){
  try{
    if(file.size>32*1024*1024)throw new Error('Inventory export exceeds 32 MB.');
    const input=JSON.parse(await file.text());
    if(input.format==='stockpile-shared-storage'){
      if(local){
        const checked=mergeShared(null,input);
        const result=await api('/api/shared/import',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(checked)});
        await sync();state.worldId=result.worldId;state.view='inventory';state.dimension='all';$('#modal').close();render();toast('Team observations merged into a local workspace.');return;
      }
      const key=`${input.serverId}:${input.teamId}`,files=readLocal('stockpile-team-files',{});
      const merged=mergeShared(files[key],input);files[key]=merged;
      if(Object.keys(files).length>10)throw new Error('Team file limit reached (10 workspaces). Export and clear unused browser data first.');
      if(!writeLocal('stockpile-team-files',files))throw new Error('Team file could not be saved.');
      const data=validateSnapshot({schemaVersion:1,capturedAt:merged.capturedAt,worlds:[sharedWorkspace(merged)]});
      state.data=data;state.source='import';state.worldId=data.worlds[0].id;state.view='inventory';state.dimension='all';writeLocal('stockpile-inventory',data);$('#modal').close();render();toast('Team observations merged. No live synchronization is active.');return;
    }
    if(local)throw new Error('Import ordinary inventory exports into the hosted dashboard. Team files can be imported here.');
    const data=validateSnapshot(input);
    writeLocal('stockpile-inventory',data);state.data=data;state.source='import';state.worldId=data.worlds[0]?.id||'';state.dimension='all';state.query='';
    $('#modal').close();render();toast(`Imported ${data.worlds.reduce((s,w)=>s+w.containers.length,0)} containers.`);
  }catch(e){modal('Could not import inventory',`<p class="status-error">${esc(e.message)}</p><p>Use an unmodified Stockpile inventory JSON export.</p>${button('import','Choose another export','upload','primary')}`);}
}
async function uploadSchematic(file){
  if(state.busy)return;
  state.view='planner';state.busy=true;state.uploadError='';render();
  let worker;
  try{
    if(file.size>25*1024*1024)throw new Error('Maximum schematic upload size is 25 MB.');
    const buffer=await file.arrayBuffer();
    const parsed=await new Promise((resolve,reject)=>{
      worker=new Worker(new URL('./schematic-worker.js',import.meta.url),{type:'module'});
      const timer=setTimeout(()=>{worker.terminate();reject(new Error('This schematic took too long to read. Split it into smaller regions.'));},25000);
      worker.onmessage=({data})=>{clearTimeout(timer);data.ok?resolve(data.result):reject(new Error(data.error));};
      worker.onerror=()=>{clearTimeout(timer);reject(new Error('The schematic reader could not start in this browser. Try a current version of Safari, Chrome or Firefox.'));};
      worker.postMessage({buffer,filename:file.name,registry:state.registry},[buffer]);
    });
    const id=crypto.randomUUID();const entry={...parsed,id,filename:file.name,createdAt:Date.now()};
    state.projects.unshift(entry);state.projects=state.projects.slice(0,50);state.selectedProject=id;state.copies=1;state.missingOnly=false;
    writeLocal('stockpile-projects',state.projects);toast(`Added ${entry.name}.`);
  }catch(e){state.uploadError=e.message;toast('Schematic could not be read. See the message in Build planner.');}
  finally{worker?.terminate();state.busy=false;render();}
}
document.addEventListener('toggle', event => {
  if (event.target.open && event.target.matches?.('.material-choice')) positionMaterialMenu(event.target);
}, true);
document.addEventListener('scroll', event => {
  const menu = $('.material-choice[open]');
  if (menu && !menu.contains(event.target)) positionMaterialMenu(menu);
}, true);
window.addEventListener?.('resize', () => {
  const menu = $('.material-choice[open]');
  if (menu) positionMaterialMenu(menu);
});
document.addEventListener('click',async e=>{
  for (const menu of document.querySelectorAll?.('details[data-choice][open]') || []) {
    if (!menu.contains(e.target)) menu.open = false;
  }
  const el=e.target.closest('[data-action]');
  if(!el){if(e.target.closest('#dropzone'))$('#schematic-file').click();return;}
  const action=el.dataset.action,id=el.dataset.id;
  if(action==='choose'){
    const menu = el.closest?.('details[data-choice]');
    if (menu) menu.open = false;
    chooseValue(el.dataset.kind, el.dataset.value);
  }
  else if(action==='nav')navigate(el.dataset.view);
  else if(['planner','inventory'].includes(action)){if(action==='planner'&&state.view==='map')state.dimension='all';navigate(action);}
  else if(action==='mobile'){state.mobile=!state.mobile;render();}
  else if(action==='connect')connect();
  else if(action==='sharing')sharing();
  else if(action==='exclude-chest'){el.disabled=true;try{await excludeChest(id,el.dataset.excluded==='true');}catch(err){toast(err.message);}finally{el.disabled=false;}}
  else if(action==='highlight-chest'){
    try{const c=world().containers.find(c=>c.id===id);if(!c)throw new Error('Saved container not found.');const reason=world().manualTeam?'Manual team files provide coordinates only.':findUnavailable({source:state.source,connected:state.data.connected,syncError:state.syncError});if(reason)throw new Error(reason);el.disabled=true;const result=await api('/api/find',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(findRequest({worldId:el.dataset.world,containerId:id,itemId:null,includePacked:false}))});toast(result.message);}catch(err){toast(err.message);}finally{el.disabled=false;}
  }
  else if(action==='highlight-settings')await showHighlightSettings();
  else if(action==='highlight-color'){
    const form = $('#highlight-form');
    if (form) { form.elements.color.value = el.dataset.color; updateHighlightPreview(form); }
  }
  else if(action==='map-capture')await captureTerrain();
  else if(action==='map-mode-pan'||action==='map-mode-select'){
    state.mapMode=action==='map-mode-select'?'select':'pan';
    render();
  }
  else if(action==='map-fit-terrain'){const terrain=terrainForMap();if(terrain){state.mapTerrainVisible=true;state.mapViewport=fitTerrain(terrain);render();}}
  else if(action==='map-terrain-toggle'){state.mapTerrainVisible=!state.mapTerrainVisible;render();}
  else if(action==='map-toggle'){const c=world().containers.find(c=>c.id===id);if(c&&!chestExcluded(c,exclusions())){writeLocal(selectionKey(),toggleChestSelection(mapSelection(),c));state.dimension='all';render();}}
  else if(action==='map-pick'){
    const group=state.mapGroups[Number(el.dataset.index)];if(!group)return;
    if(group.containers.length===1){const c=group.containers[0];if(chestExcluded(c,exclusions())){state.mapFocus=[c.id];render();}else{writeLocal(selectionKey(),toggleChestSelection(mapSelection(),c));state.dimension='all';render();}}
    else if(group.containers.every(c=>c.position[0]===group.containers[0].position[0]&&c.position[2]===group.containers[0].position[2])){state.mapFocus=group.containers.map(c=>c.id);state.limit=20;render();}
    else{state.mapViewport=fitChestGroup(group.containers);state.mapFocus=[];render();}
  }
  else if(action==='map-all'||action==='map-none'){writeLocal(selectionKey(),action==='map-all'?null:[]);render();}
  else if(action==='map-fit'){const candidates=filterContainers(world().containers,{kinds:state.containerKinds}).filter(c=>c.dimension===state.mapDimension);state.mapViewport=fitChestGroup(candidates);state.mapFocus=[];render();}
  else if(action==='map-unfocus'){state.mapFocus=[];render();}
  else if(action==='map-zoom-in'||action==='map-zoom-out'){const model=chestMap(filterContainers(world().containers,{kinds:state.containerKinds}),state.mapDimension,state.mapViewport||(terrainForMap()&&state.mapTerrainVisible?fitTerrain(terrainForMap()):null));state.mapViewport={centerX:model.centerX,centerZ:model.centerZ,span:Math.max(4,Math.min(200000000,model.span*(action==='map-zoom-in'?.5:2)))};render();}
  else if(action==='current-world'){state.worldId=state.data.activeWorldId;state.dimension='all';state.query='';state.filter='all';state.limit=20;$('#modal').close();render();}
  else if(action==='reserve'||action==='release-reservation'||action==='complete-reservation'){
    el.disabled=true;try{await changeReservation(action==='reserve'?'reserve':action==='release-reservation'?'release':'complete',id);}catch(err){toast(err.message);}finally{el.disabled=false;}
  }
  else if(action==='history-csv'){
    const quote=v=>'"'+String(v).replaceAll('"','""')+'"';
    const lines=['Timestamp,Reason,Container,Dimension,Item ID,Observed change'];
    for(const e of planning().history)for(const c of e.changes)lines.push([new Date(e.at).toISOString(),e.reason,e.containerId,e.dimension,c.itemId,c.delta].map(quote).join(','));
    download('stockpile-history.csv',lines.join('\r\n'),'text/csv;charset=utf-8');
  }
  else if(action==='crafting-csv'){download('stockpile-crafting-steps.csv',craftingCsv(recipePlan()),'text/csv;charset=utf-8');}
  else if(action==='close')$('#modal').close();
  else if(action==='import')$('#inventory-file').click();
  else if(action==='upload')$('#schematic-file').click();
  else if(action==='export'){download(`stockpile-inventory-${new Date().toISOString().slice(0,10)}.json`,JSON.stringify(state.data,null,2));toast(state.source==='demo'?'Exported example inventory.':'Inventory exported.');}
  else if(action==='filter'){state.filter=el.dataset.filter;state.limit=20;render();}
  else if(action==='reset-filters'){state.query='';state.filter='all';state.dimension='all';render();}
  else if(action==='more'){state.limit+=30;render();}
  else if(action==='details')details(id);
  else if(action==='targets')showTargets();
  else if(action==='copy-coord')await copy(el.dataset.pos);
  else if(action==='find-game'){
    try{
      const reason=world().manualTeam?'Manual team files provide coordinates only.':findUnavailable({source:state.source,connected:state.data.connected,syncError:state.syncError});
      if(reason)throw new Error(reason);
      const request=findRequest({worldId:el.dataset.world,containerId:el.dataset.container,itemId:id,includePacked:el.dataset.packed==='true'});
      el.disabled=true;
      const result=await api('/api/find',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(request)});
      toast(result.message);
    }catch(err){toast(err.message);}finally{el.disabled=false;}
  }
  else if(action==='project'){state.selectedProject=id;state.copies=1;state.missingOnly=false;state.view='planner';render();window.scrollTo(0,0);}
  else if(action==='delete-project'){const p=project();modal('Remove this build?',`<p>Remove <strong>${esc(p.name)}</strong> and its saved material list from this browser?</p><div class="modal-actions">${button('close','Keep build','','quiet')}${button('confirm-delete-project','Remove build','trash','danger',`data-id="${esc(p.id)}"`)}</div>`);}
  else if(action==='confirm-delete-project'){
    if(planning().reservations[id]?.status==='active'){
      try{await changeReservation('release',id);}catch(err){toast(err.message);return;}
    }
    state.projects=state.projects.filter(p=>p.id!==id);
    if(writeLocal('stockpile-projects',state.projects)) localStorage.removeItem(materialChecklistKey(id));
    state.selectedProject='';$('#modal').close();render();toast('Build removed.');
  }
  else if(action==='forget'){const c=world().containers.find(c=>c.id===id);modal('Forget this container?',`<p>Remove the saved contents of <strong>${esc(c.name)}</strong> at <span class="mono">${esc(c.position.join(' / '))}</span>? Open it again in Minecraft to track it again.</p><div class="modal-actions">${button('close','Keep record','','quiet')}${button('confirm-forget','Forget container','trash','danger',`data-id="${esc(id)}"`)}</div>`);}
  else if(action==='confirm-forget'){
    try{
      if(local){await api('/api/forget',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({worldId:state.worldId,containerId:id})});await sync();}
      else{world().containers=world().containers.filter(c=>c.id!==id);if(state.source==='import')writeLocal('stockpile-inventory',state.data);}
      $('#modal').close();render();toast('Saved container removed.');
    }catch(err){toast(err.message);}
  }else if(action==='export-materials'||action==='missing-csv'){
    const rows=comparison().filter(r=>action!=='missing-csv'||r.missing>0);
    download(`stockpile-${action==='missing-csv'?'missing':'materials'}.csv`,materialsCsv(rows),'text/csv;charset=utf-8');toast(action==='missing-csv'&&!rows.length?'All listed materials are available.':'Material checklist exported.');
  }else if(action==='licenses')modal('Licenses',`<p>Stockpile’s original code and artwork are MIT licensed. Minecraft textures and models retain their separate rights.</p><p>Stockpile is independent. Mojang and Microsoft do not approve, endorse, or sponsor it.</p><p>Chest Tracker Port is an optional install under LGPL-3.0-only.</p><div class="modal-actions"><a class="button small" href="https://github.com/danzaa/StockPile/blob/main/LICENSE" target="_blank" rel="noopener noreferrer">MIT License</a><a class="button small" href="https://www.minecraft.net/en-us/usage-guidelines" target="_blank" rel="noopener noreferrer">Minecraft Usage Guidelines</a></div>`);
  else if(action==='about')modal('How counts work',`<p>Stockpile remembers the contents the server sends when you open a supported chest, barrel, shulker box or Ender Chest. Items inside shulker boxes are included by default. The item details show the parent chest and shulker slot. Player inventory and bundles are excluded.</p><p>Counts are grouped by item ID. Names, enchantments and other item components do not create separate material types. A double chest is one container. Your Ender Chest is counted once per world and player, and has its own view. Container-source checkboxes determine which totals and building materials are included. A PLACED badge marks items in shulker boxes placed in the world.</p><p>Your own item moves update automatically while a tracked container is open and when it closes. Closed chests can change without your client knowing. Reopen them to refresh, and remove old records if you move or break a container. Custom server menu plugins can change how containers behave.</p><p>Low-stock targets and uploaded build lists are saved in this browser. Exports let you back up inventory and material checklists.</p><div class="modal-note">Schematic checks compare direct placement materials. Recipe substitutions can calculate vanilla crafting, stonecutting and optional smelting with fuel from the selected storage. The crafting plan shares ingredients across this build and reserves its directly required items first. It assumes access to the listed workstations. Supported cooking plans budget ingredients and selected fuel together and assume uninterrupted processing. Custom recipes, entities and schematic container contents are excluded. Use explicit reservations to protect a project’s physical supplies from other builds. Already-placed blocks are not verified. Review any import notes before gathering materials.</div>`);
});
document.addEventListener('input',e=>{
  if (e.target.form?.id === 'highlight-form') updateHighlightPreview(e.target.form);
  if(e.target.id==='inventory-search'){state.query=e.target.value;state.limit=20;render();}
});
document.addEventListener('change',e=>{
  const el=e.target;
  if (el.form?.id === 'highlight-form') updateHighlightPreview(el.form);
  if(el.id==='map-dimension'){state.mapDimension=el.value;state.mapViewport=null;state.mapFocus=[];render();}
  else if(el.id==='stale-hours'){const n=Number(el.value);if(Number.isInteger(n)&&n>=1&&n<=8760){state.staleHours=n;writeLocal('stockpile-stale-hours',n);render();}}
  else if(el.id==='exclude-stale'){state.excludeStale=el.checked;writeLocal('stockpile-exclude-stale',el.checked);render();}
  else if(el.id==='schematic-chest-enabled') saveSchematicSetting(el,'enabled');
  else if(el.id==='schematic-chest-auto-clear') saveSchematicSetting(el,'autoClear');
  else if(el.id==='schematic-destination' && project()){
    saveSchematicDestination(project(), el.value);
    el.form.querySelector('button[type=submit]').disabled = !canReviewSchematicChest(el.value);
  }
  else if(el.id==='recipe-substitutions'){state.recipeSubstitutions=el.checked;writeLocal('stockpile-recipe-substitutions',state.recipeSubstitutions);render();}
  else if(el.id==='smelting-enabled'){state.smelting=el.checked;writeLocal('stockpile-smelting',state.smelting);render();}
  else if(el.id==='blast'){state.blast=el.checked;writeLocal('stockpile-blast',el.checked);render();}
  else if(el.id==='fuel-choice'){state.fuelChoice=el.value;writeLocal('stockpile-fuel',el.value);render();}
  else if(el.id==='dimension'){state.dimension=el.value;render();}
  else if(el.id==='sort'){state.sort=el.value;render();}
  else if(el.id==='recipe-version'){state.recipeVersion=el.value;writeLocal('stockpile-recipe-version',state.recipeVersion);render();}
  else if(el.id==='copies'){const n=Number(el.value);if(Number.isInteger(n)&&n>=1&&n<=999){try{compareMaterials(project().materials,inventory(),n);state.copies=n;}catch(err){toast(err.message);}}else toast('Choose between 1 and 999 copies.');render();}
  else if(el.dataset?.materialCheck) setMaterialCollected(el.dataset.materialCheck,el.checked);
  else if(el.matches('[data-container-kind]')){const kind=el.dataset.containerKind;state.containerKinds=el.checked?[...new Set([...state.containerKinds,kind])]:state.containerKinds.filter(k=>k!==kind);writeLocal('stockpile-container-kinds',state.containerKinds);const storage=state.containerKinds.filter(k=>k!=='ender');if(storage.length)writeLocal('stockpile-storage-kinds',storage);if(state.view==='ender'&&sourceScope(state.containerKinds)!=='ender')state.view='inventory';else if(state.view==='inventory'&&sourceScope(state.containerKinds)==='ender')state.view='ender';state.limit=20;render();}
  else if(el.id==='include-packed'){state.includePacked=el.checked;writeLocal('stockpile-include-packed',state.includePacked);render();}
  else if(el.id==='missing-only'){state.missingOnly=el.checked;render();}
  else if(el.id==='inventory-file'&&el.files[0])importInventory(el.files[0]);
  else if(el.id==='schematic-file'&&el.files[0])uploadSchematic(el.files[0]);
});
document.addEventListener('submit',async e=>{
  if (e.target.id === 'schematic-chest-form') {
    e.preventDefault();
    try { reviewSchematicFill(e.target.elements.destination.value); } catch (error) { toast(error.message); }
    return;
  }
  if (e.target.id === 'confirm-schematic-fill') {
    e.preventDefault();
    e.target.querySelector('button').disabled = true;
    await fillSchematicChest();
    return;
  }
  if(e.target.id==='highlight-form'){
    e.preventDefault();
    const fields=e.target.elements;
    try {
      await api('/api/highlight/settings',{
        method:'POST',headers:{'Content-Type':'application/json'},
        body:JSON.stringify({color:fields.color.value,seconds:Number(fields.seconds.value),throughWalls:fields.throughWalls.checked})
      });
      $('#modal').close();
      toast('Highlight settings saved.');
    } catch(error) {toast(error.message);}
    return;
  }
  if(e.target.id==='shared-export-form'){
    e.preventDefault();try{
      const settings={teamId:$('#share-team').value.trim(),serverId:$('#share-server').value.trim(),name:$('#share-name').value.trim()};
      const data=sharedExport({...world(),excludedLocations:exclusions(),excludedContainerIds:[]},settings);writeLocal('stockpile-share-settings',settings);download('stockpile-team-storage.json',JSON.stringify(data,null,2));toast('Storage-only team file exported.');
    }catch(err){toast(err.message);}
  }else if(e.target.id==='target-form'){
    e.preventDefault();const n=Number($('#target-amount').value);if(!Number.isInteger(n)||n<0||n>MAX_COUNT)return;
    const t=targets();if(n)t[e.target.dataset.id]=n;else delete t[e.target.dataset.id];writeLocal(targetKey(),t);$('#modal').close();render();toast('Stock target saved.');
  }else if(e.target.id==='targets-form'){
    e.preventDefault();const t=targets();for(const input of e.target.querySelectorAll('[data-target-id]')){const n=Number(input.value);if(n)t[input.dataset.targetId]=n;else delete t[input.dataset.targetId];}
    const id=$('#new-target-id').value.trim(),n=Number($('#new-target-count').value);if(id){if(!validId(id)||!Number.isInteger(n)||n<1||n>MAX_COUNT){toast('Enter a valid item ID and minimum quantity.');return;}t[id]=n;}
    writeLocal(targetKey(),t);$('#modal').close();render();toast('Stock targets saved.');
  }
});
document.addEventListener('keydown',e=>{
  if (handleChoiceKey(e)) return;
  if((e.key==='Enter'||e.key===' ')&&e.target.closest?.('[data-action="map-pick"]')){e.preventDefault();e.target.closest('[data-action="map-pick"]').dispatchEvent(new MouseEvent('click',{bubbles:true}));return;}
  if(e.key==='/'&&!$('#modal').open&&!['INPUT','TEXTAREA'].includes(e.target.tagName)){e.preventDefault();$('#inventory-search')?.focus();}
  if((e.key==='Enter'||e.key===' ')&&e.target.id==='dropzone'){e.preventDefault();$('#schematic-file').click();}
});
$('#modal').addEventListener('click',e=>{if(e.target===$('#modal')){const r=e.target.getBoundingClientRect();if(e.clientX<r.left||e.clientX>r.right||e.clientY<r.top||e.clientY>r.bottom)e.target.close();}});
render();
if(local){sync();setInterval(sync,2500);}

globalThis.addEventListener?.('storage',e=>{if([planningKey(),selectionKey(),exclusionKey()].includes(e.key))render();});

document.addEventListener('error',e=>{const el=e.target;if(el instanceof HTMLImageElement&&!el.src.endsWith('/ui/unknown.svg')){el.src='./ui/unknown.svg';}},true);

function mapPoint(event, transform) {
  return new DOMPoint(event.clientX, event.clientY).matrixTransform(transform);
}

document.addEventListener('pointerdown', event => {
  const svg = event.target.closest?.('#storage-map');
  if (!svg || mapDrag || event.button !== 0) return;
  const select = state.mapMode === 'select' || event.shiftKey;
  if (!select && event.target.closest('[data-action]')) return;
  const matrix = svg.getScreenCTM();
  if (!matrix) return;
  const transform = matrix.inverse();
  const bounds = svg.viewBox.baseVal;
  mapDrag = {
    svg, pointer: event.pointerId, select, transform, start: mapPoint(event, transform),
    screenX: event.clientX, screenY: event.clientY,
    left: bounds.x, top: bounds.y, width: bounds.width, height: bounds.height,
    world: state.worldId, dimension: state.mapDimension, moved: false
  };
});

document.addEventListener('pointermove', event => {
  const drag = mapDrag;
  if (!drag || drag.pointer !== event.pointerId || !drag.svg.isConnected) return;
  if (!drag.moved) {
    if (Math.hypot(event.clientX - drag.screenX, event.clientY - drag.screenY) < 5) return;
    drag.moved = true;
    drag.svg.setPointerCapture(event.pointerId);
  }
  const point = mapPoint(event, drag.transform);
  if (!drag.select) {
    const left = drag.left - (point.x - drag.start.x);
    const top = drag.top - (point.y - drag.start.y);
    drag.viewport = {centerX: left + drag.width / 2, centerZ: top + drag.height / 2, span: drag.width};
    drag.svg.setAttribute('viewBox', `${left} ${top} ${drag.width} ${drag.height}`);
    return;
  }
  const box = mapBox(drag.start, point);
  const rectangle = drag.svg.querySelector('#map-selection-box');
  rectangle.setAttribute('x', box.left);
  rectangle.setAttribute('y', box.top);
  rectangle.setAttribute('width', box.right - box.left);
  rectangle.setAttribute('height', box.bottom - box.top);
  rectangle.setAttribute('visibility', 'visible');
});

function finishMapDrag(event) {
  const drag = mapDrag;
  if (!drag || drag.pointer !== event.pointerId) return;
  mapDrag = null;
  if (!drag.moved || !drag.svg.isConnected) return;
  if (event.type === 'pointerup' && state.worldId === drag.world && state.mapDimension === drag.dimension) {
    if (drag.select) {
      const box = mapBox(drag.start, mapPoint(event, drag.transform));
      const candidates = filterContainers(world().containers, {kinds: state.containerKinds});
      const {selected, added} = addChestsInBox(candidates, drag.dimension, box, mapSelection(), exclusions());
      if (added && writeLocal(selectionKey(), selected)) {
        state.dimension = 'all';
        toast(added === 1 ? 'Added 1 chest to the build selection.' : `Added ${added} chests to the build selection.`);
      } else if (!added) toast('No new included chests in this area.');
    } else if (drag.viewport) {
      state.mapViewport = drag.viewport;
    }
  }
  // Replacing the SVG also prevents the finished drag from clicking a marker.
  render();
}
document.addEventListener('pointerup', finishMapDrag);
document.addEventListener('pointercancel', finishMapDrag);
