import {compareMaterials, MAX_COUNT, validId} from './core.js';

const indexes = new WeakMap();
const MAX_WORK = 6000;
const MAX_DEPTH = 12;

function indexRecipes(catalog) {
  if (indexes.has(catalog)) return indexes.get(catalog);
  const index = new Map();
  for (const recipe of catalog.recipes) {
    if (!validId(recipe.output) || !Number.isInteger(recipe.count) || recipe.count < 1 || !recipe.ingredients.length) continue;
    if (!index.has(recipe.output)) index.set(recipe.output, []);
    index.get(recipe.output).push(recipe);
  }
  indexes.set(catalog, index);
  return index;
}

function clone(context) {
  return {pool:new Map(context.pool), steps:[...context.steps]};
}

export function planMaterials(materials, inventory, multiplier, catalog) {
  const rows = compareMaterials(materials, inventory, multiplier).map(row => ({...row, direct:row.allocated, craftable:0}));
  const recipes = indexRecipes(catalog);
  const context = {pool:new Map([...inventory].map(([id, item]) => [id, item.count])), steps:[]};
  for (const row of rows) {
    row.direct = Math.min(context.pool.get(row.id) || 0, row.needed);
    row.allocated = row.direct;
    row.missing = row.needed - row.direct;
    context.pool.set(row.id, (context.pool.get(row.id) || 0) - row.direct);
  }
  let work = 0, limited = false;
  const spend = () => {
    if (++work <= MAX_WORK) return true;
    limited = true;
    return false;
  };

  function take(id, amount, context, parents) {
    const held = Math.min(context.pool.get(id) || 0, amount);
    context.pool.set(id, (context.pool.get(id) || 0) - held);
    let delivered = held;
    if (held === amount || parents.includes(id)) return delivered;
    if (parents.length >= MAX_DEPTH) { limited = true; return delivered; }
    const path = [...parents, id];
    while (delivered < amount && spend()) {
      let best = null;
      for (const recipe of recipes.get(id) || []) {
        if (!spend()) break;
        const maximum = Math.min(Math.ceil((amount - delivered) / recipe.count), Math.floor(MAX_COUNT / recipe.count));
        let low = 0, high = maximum, success = null;
        while (low < high && spend()) {
          const batches = low === 0 && high === maximum ? maximum : Math.floor((low + high + 1) / 2);
          const trial = attempt(recipe, batches, context, path);
          if (trial) { low = batches; success = trial; }
          else high = batches - 1;
        }
        if (!success) continue;
        const produced = low * recipe.count;
        const usable = Math.min(produced, amount - delivered);
        const cost = success.steps.slice(context.steps.length).reduce((sum, step) => sum + step.ingredients.reduce((n, i) => n + i.count, 0), 0);
        if (!best || usable > best.usable || usable === best.usable && cost < best.cost) best = {context:success, usable, cost};
      }
      if (!best) break;
      context.pool = best.context.pool;
      context.steps = best.context.steps;
      context.pool.set(id, (context.pool.get(id) || 0) - best.usable);
      delivered += best.usable;
    }
    return delivered;
  }

  function attempt(recipe, batches, original, path) {
    if (!batches || !spend()) return null;
    const trial = clone(original), ingredients = [];
    for (const ingredient of [...recipe.ingredients].sort((a, b) => a.items.length - b.items.length)) {
      let needed = ingredient.count * batches;
      if (!Number.isSafeInteger(needed) || needed > MAX_COUNT) return null;
      const options = [...ingredient.items].sort((a, b) => (trial.pool.get(b) || 0) - (trial.pool.get(a) || 0) || a.localeCompare(b));
      for (const id of options) {
        const direct = Math.min(trial.pool.get(id) || 0, needed);
        if (!direct) continue;
        trial.pool.set(id, trial.pool.get(id) - direct);
        ingredients.push({id, count:direct});
        needed -= direct;
        if (!needed) break;
      }
      if (needed) for (const id of options) {
        const acquired = take(id, needed, trial, path);
        if (acquired) ingredients.push({id, count:acquired});
        needed -= acquired;
        if (!needed) break;
      }
      if (needed) return null;
    }
    const produced = batches * recipe.count;
    const total = (trial.pool.get(recipe.output) || 0) + produced;
    if (!Number.isSafeInteger(total) || total > MAX_COUNT) return null;
    trial.pool.set(recipe.output, total);
    const usage=new Map();
    for (const ingredient of ingredients) usage.set(ingredient.id,(usage.get(ingredient.id)||0)+ingredient.count);
    trial.steps.push({recipe:recipe.id, output:recipe.output, count:produced, batches, station:recipe.station, ingredients:[...usage].map(([id,count])=>({id,count}))});
    return trial;
  }

  for (const row of rows) {
    row.craftable = take(row.id, row.missing, context, []);
    row.allocated = row.direct + row.craftable;
    row.missing = Math.max(0, row.needed - row.allocated);
  }
  const steps = [];
  for (const step of context.steps) {
    const previous = steps.at(-1);
    if (previous?.recipe === step.recipe) {
      previous.count += step.count;
      previous.batches += step.batches;
      for (const ingredient of step.ingredients) {
        const entry = previous.ingredients.find(i => i.id === ingredient.id);
        if (entry) entry.count += ingredient.count;
        else previous.ingredients.push({...ingredient});
      }
    } else steps.push({...step, ingredients:step.ingredients.map(i => ({...i}))});
  }
  return {rows, steps, limited, minecraftVersion:catalog.minecraftVersion};
}

export function craftingCsv(plan) {
  const quote = value => '"' + String(value).replaceAll('"', '""') + '"';
  return ['Step,Recipe,Workstation,Output ID,Output quantity,Ingredients', ...plan.steps.map((step, i) => [i + 1, step.recipe, step.station, step.output, step.count, step.ingredients.map(item => `${item.count} ${item.id}`).join('; ')].map(quote).join(','))].join('\r\n');
}
