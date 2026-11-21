import {validId} from './core.js';

export function findUnavailable({source,connected,syncError}) {
  if(source!=='local')return 'Connect the local Minecraft dashboard to find items in-game.';
  if(syncError)return 'Reconnect to Minecraft before finding an item.';
  if(!connected)return 'Join the saved world in Minecraft first.';
  return '';
}
export function findRequest({worldId,containerId,itemId,includePacked}) {
  if(typeof worldId!=='string'||!worldId||worldId.length>200||typeof containerId!=='string'||!containerId||containerId.length>500||(itemId!==null&&(!validId(itemId)||itemId.length>256))||typeof includePacked!=='boolean')throw new Error('This saved location is invalid. Refresh the inventory and try again.');
  return {worldId,containerId,itemId,includePacked};
}
