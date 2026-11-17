import {decodeSchematic} from './schematic.js';
self.onmessage=async ({data})=>{
  try{self.postMessage({ok:true,result:await decodeSchematic(data.buffer,data.filename,data.registry)});}
  catch(error){self.postMessage({ok:false,error:error.message||'Could not read this schematic.'});}
};
