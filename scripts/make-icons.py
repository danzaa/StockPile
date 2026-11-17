import io
import json
import math
import sys
import subprocess
import zipfile
from functools import lru_cache
from pathlib import Path
import numpy as np
from PIL import Image, ImageDraw, ImageEnhance

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / 'dist'
ITEMS = OUT / 'items'
ITEMS.mkdir(exist_ok=True)
UI = OUT / 'ui'
UI.mkdir(exist_ok=True)


def make_logo():
    image = Image.new('RGBA', (32, 32))
    draw = ImageDraw.Draw(image)
    for rect, color in [((3, 7, 28, 28), '#1e241b'), ((5, 9, 26, 26), '#8c5d2d'), ((5, 9, 26, 15), '#c1934d'),
                        ((5, 16, 26, 18), '#332b20'), ((7, 20, 24, 24), '#a47338'), ((8, 25, 23, 26), '#735027'),
                        ((13, 15, 18, 23), '#252a1d'), ((14, 16, 17, 21), '#e3ca75'), ((7, 10, 24, 11), '#d4a95d'),
                        ((21, 2, 25, 6), '#1e241b'), ((22, 3, 24, 5), '#a4c569'), ((26, 2, 30, 6), '#1e241b'),
                        ((27, 3, 29, 5), '#a4c569'), ((26, 7, 30, 11), '#1e241b'), ((27, 8, 29, 10), '#a4c569')]:
        draw.rectangle(rect, fill=color)
    image.resize((256, 256), Image.Resampling.NEAREST).save(OUT / 'logo.png')
    image.save(OUT / 'favicon.png')
    target = ROOT / 'src/main/resources/assets/stockpile/icon.png'
    target.parent.mkdir(parents=True, exist_ok=True)
    image.resize((128, 128), Image.Resampling.NEAREST).save(target)
    rects = []
    for y in range(32):
        for x in range(32):
            r, g, b, a = image.getpixel((x, y))
            if a:
                rects.append(f'<rect x="{x}" y="{y}" width="1" height="1" fill="#{r:02x}{g:02x}{b:02x}"/>')
    (OUT / 'favicon.svg').write_text('<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 32 32" shape-rendering="crispEdges">' + ''.join(rects) + '</svg>')
    return image


logo = make_logo()
(UI / 'unknown.svg').write_text('<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 16 16" shape-rendering="crispEdges"><path fill="#82916e" d="M4 1h8v2H4zM2 3h2v3H2zm10 0h2v5h-2zm-2 5h2v2h-2zm-3 2h3v2H7zm0 3h3v3H7z"/></svg>')
archive = zipfile.ZipFile(sys.argv[1])
texture_overrides = {}


@lru_cache(None)
def read_json(name):
    try:
        return json.loads(archive.read(name))
    except KeyError:
        return {}


@lru_cache(None)
def texture(name):
    if name in texture_overrides:
        return texture_overrides[name]
    if name.startswith('#'):
        return None
    namespace, path = name.split(':', 1) if ':' in name else ('minecraft', name)
    try:
        image = Image.open(io.BytesIO(archive.read(f'assets/{namespace}/textures/{path}.png'))).convert('RGBA')
    except KeyError:
        return None
    if image.height > image.width:
        image = image.crop((0, 0, image.width, image.width))
    return image


@lru_cache(None)
def model(name):
    namespace, path = name.split(':', 1) if ':' in name else ('minecraft', name)
    raw = read_json(f'assets/{namespace}/models/{path}.json')
    inherited = model(raw['parent']) if raw.get('parent') and not raw['parent'].startswith('builtin/') else {}
    return {**inherited, **raw, 'textures': {**inherited.get('textures', {}), **raw.get('textures', {})}}


def resolve_texture(key, textures):
    for _ in range(20):
        if isinstance(key,dict):
            key=key.get('sprite','')
        if not isinstance(key,str) or not key:
            return None
        if not key.startswith('#'):
            return texture(key)
        key = textures.get(key[1:], '')
        if not key:
            break
    return None


def project(points):
    p = np.asarray(points, float) - [8, 8, 8]
    p[:, 2] *= -1
    screen = np.column_stack(((p[:, 0] - p[:, 2]) * .8660254, (p[:, 0] + p[:, 2]) * .5 - p[:, 1]))
    return screen * .86 + [16, 16], np.mean(p[:, 0] + p[:, 2] + p[:, 1] * .5)


def render_elements(data):
    faces = []
    for element in data.get('elements', []):
        x, y, z = element['from']; X, Y, Z = element['to']
        vertices = {'up': [[x,Y,z],[X,Y,z],[X,Y,Z],[x,Y,Z]], 'down': [[x,y,Z],[X,y,Z],[X,y,z],[x,y,z]],
                    'north': [[X,Y,z],[x,Y,z],[x,y,z],[X,y,z]], 'south': [[x,Y,Z],[X,Y,Z],[X,y,Z],[x,y,Z]],
                    'east': [[X,Y,Z],[X,Y,z],[X,y,z],[X,y,Z]], 'west': [[x,Y,z],[x,Y,Z],[x,y,Z],[x,y,z]]}
        for direction, face in element.get('faces', {}).items():
            tex = resolve_texture(face.get('texture', ''), data.get('textures', {}))
            if tex is None:
                continue
            points = np.asarray(vertices[direction], float)
            rotation = element.get('rotation')
            if rotation:
                axis = {'x':0, 'y':1, 'z':2}[rotation['axis']]
                radians = math.radians(rotation['angle']); c, s = math.cos(radians), math.sin(radians)
                matrix = np.eye(3); a, b = (axis+1)%3, (axis+2)%3
                matrix[a,a]=matrix[b,b]=c; matrix[a,b]=-s; matrix[b,a]=s
                origin = np.array(rotation.get('origin',[8,8,8]))
                points = (points-origin) @ matrix.T + origin
            screen, depth = project(points)
            edge_a, edge_b = screen[1]-screen[0], screen[3]-screen[0]
            area = edge_a[0]*edge_b[1]-edge_a[1]*edge_b[0]
            if abs(area) < .01:
                continue
            uv = face.get('uv', [0,0,16,16]); u,v,U,V=uv
            coords=np.array([[u,v],[U,v],[U,V],[u,V]],float)*[tex.width/16,tex.height/16]
            coords=np.roll(coords, face.get('rotation',0)//90, axis=0)
            design=np.column_stack((screen[[0,1,3]],np.ones(3)))
            try:
                coefficients=np.linalg.solve(design,coords[[0,1,3]])
            except np.linalg.LinAlgError:
                continue
            mapped=tex.transform((32,32), Image.Transform.AFFINE, tuple(coefficients[:,0])+tuple(coefficients[:,1]), Image.Resampling.NEAREST)
            mask=Image.new('L',(32,32));ImageDraw.Draw(mask).polygon([tuple(p) for p in screen],fill=255)
            rgba=np.array(mapped)
            if 'tintindex' in face:
                rgba[:,:,:3]=(rgba[:,:,:3]*[.6,.8,.36]).astype(np.uint8)
            brightness={'up':1,'down':.5,'north':.8,'east':.62,'south':.75,'west':.65}[direction]
            rgba[:,:,:3]=(rgba[:,:,:3]*brightness).astype(np.uint8)
            rgba[:,:,3]=np.minimum(rgba[:,:,3],np.array(mask))
            faces.append((depth,Image.fromarray(rgba)))
    if not faces:
        return None
    image=Image.new('RGBA',(32,32))
    for _, face in sorted(faces,key=lambda pair:pair[0]):
        image.alpha_composite(face)
    return image


def select_model(spec):
    if isinstance(spec,str):
        return {'model':spec}
    if not isinstance(spec,dict):
        return None
    if spec.get('model') and isinstance(spec['model'],str):
        return spec
    if spec.get('fallback'):
        return select_model(spec['fallback'])
    for field in ['on_false','on_true','models','cases','entries']:
        entry=spec.get(field)
        if isinstance(entry,list):
            for candidate in entry:
                result=select_model(candidate.get('model',candidate))
                if result:
                    return result
        elif isinstance(entry,dict):
            result=select_model(entry)
            if result:
                return result
    return spec if spec.get('base') else None


manifest={}
for name in archive.namelist():
    if not name.startswith('assets/minecraft/items/') or not name.endswith('.json'):
        continue
    item=Path(name).stem;spec=select_model(read_json(name).get('model',{}));image=None
    data=model(spec.get('model') if isinstance(spec.get('model'),str) else spec.get('base','')) if spec else {}
    textures=data.get('textures',{})
    if 'layer0' in textures:
        image=Image.new('RGBA',(32,32))
        for layer in range(6):
            if f'layer{layer}' not in textures:
                continue
            tex=resolve_texture(textures[f'layer{layer}'],textures)
            if tex is not None:
                image.alpha_composite(tex.resize((32,32),Image.Resampling.NEAREST))
    elif data.get('elements'):
        image=render_elements(data)
    if image is None:
        tex=texture('minecraft:item/'+item)
        if tex is not None:
            image=tex.resize((32,32),Image.Resampling.NEAREST)
    if image is None and 'chest' in item:
        image=logo
    if image is None and item.endswith('shulker_box'):
        color=item.replace('_shulker_box','') if item!='shulker_box' else ''
        tex=texture('minecraft:entity/shulker/shulker'+('_'+color if color else ''))
        if tex is not None:
            top='stockpile:block/'+item+'_top';side='stockpile:block/'+item+'_side'
            texture_overrides[top]=tex.crop((16,0,32,16))
            texture_overrides[side]=tex.crop((0,16,16,32))
            image=render_elements({'textures':{'top':top,'side':side},'elements':[{'from':[0,0,0],'to':[16,16,16],'faces':{d:{'texture':'#top' if d=='up' else '#side'} for d in ['up','north','east','south','west','down']}}]})
    if image is None or image.getbbox() is None:
        continue
    image.save(ITEMS/(item+'.png'),optimize=True)
    manifest['minecraft:'+item]='./items/'+item+'.png'
for special,prefix in [('compass','compass_'),('clock','clock_')]:
    if not (ITEMS/(special+'.png')).exists():
        tex=texture('minecraft:item/'+prefix+('16' if special=='compass' else '00'))
        if tex is not None:
            tex.resize((32,32),Image.Resampling.NEAREST).save(ITEMS/(special+'.png'))
            manifest['minecraft:'+special]='./items/'+special+'.png'
(OUT/'item-icons.js').write_text('export const itemIcons='+json.dumps(manifest,sort_keys=True,separators=(',',':'))+';\n')
print('Minecraft item icons:',len(manifest),'Original chest logo:',OUT/'logo.png')
subprocess.run([sys.executable, str(ROOT / 'scripts/make-stair-icons.py'), sys.argv[1]], check=True)
