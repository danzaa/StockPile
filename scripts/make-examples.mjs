import {mkdir,writeFile} from 'node:fs/promises';
import {encodeNbt,fixture} from '../tests/nbt-helper.mjs';
import {demoSnapshot} from '../dist/demo.js';
await mkdir(new URL('../examples/',import.meta.url),{recursive:true});
await writeFile(new URL('../examples/tiny-workshop.litematic',import.meta.url),encodeNbt(fixture(),true));
await writeFile(new URL('../examples/example-inventory.json',import.meta.url),JSON.stringify(demoSnapshot,null,2));
await writeFile(new URL('../examples/material-list.json',import.meta.url),JSON.stringify({name:'Stone path',materials:[{id:'minecraft:stone_bricks',count:192},{id:'minecraft:lantern',count:12}]},null,2));
console.log('Example inventory, material list and .litematic written.');
