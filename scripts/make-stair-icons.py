import argparse
import io
import json
import math
import zipfile
from functools import lru_cache
from pathlib import Path

import numpy as np
from PIL import Image


ROOT = Path(__file__).resolve().parent.parent
NORMALS = {'down': [0, -1, 0], 'up': [0, 1, 0], 'north': [0, 0, -1],
           'south': [0, 0, 1], 'west': [-1, 0, 0], 'east': [1, 0, 0]}
CORNERS = {
    'down': [(0, 0, 1), (0, 0, 0), (1, 0, 0), (1, 0, 1)],
    'up': [(0, 1, 0), (0, 1, 1), (1, 1, 1), (1, 1, 0)],
    'north': [(1, 1, 0), (1, 0, 0), (0, 0, 0), (0, 1, 0)],
    'south': [(0, 1, 1), (0, 0, 1), (1, 0, 1), (1, 1, 1)],
    'west': [(0, 1, 0), (0, 0, 0), (0, 0, 1), (0, 1, 1)],
    'east': [(1, 1, 1), (1, 0, 1), (1, 0, 0), (1, 1, 0)],
}


def rotation_matrix(angles):
    x, y, z = map(math.radians, angles)
    rx = np.array([[1, 0, 0], [0, math.cos(x), -math.sin(x)], [0, math.sin(x), math.cos(x)]])
    ry = np.array([[math.cos(y), 0, math.sin(y)], [0, 1, 0], [-math.sin(y), 0, math.cos(y)]])
    rz = np.array([[math.cos(z), -math.sin(z), 0], [math.sin(z), math.cos(z), 0], [0, 0, 1]])
    return rx @ ry @ rz


class StairRenderer:
    def __init__(self, archive, size=128):
        self.archive = archive
        self.size = size

    @lru_cache(None)
    def model(self, name):
        namespace, path = name.split(':', 1) if ':' in name else ('minecraft', name)
        raw = json.loads(self.archive.read(f'assets/{namespace}/models/{path}.json'))
        parent = self.model(raw['parent']) if raw.get('parent') else {}
        return {**parent, **raw,
                'textures': {**parent.get('textures', {}), **raw.get('textures', {})},
                'display': {**parent.get('display', {}), **raw.get('display', {})}}

    @lru_cache(None)
    def texture(self, name):
        namespace, path = name.split(':', 1) if ':' in name else ('minecraft', name)
        image = Image.open(io.BytesIO(self.archive.read(f'assets/{namespace}/textures/{path}.png'))).convert('RGBA')
        if image.height > image.width:
            image = image.crop((0, 0, image.width, image.width))
        return np.array(image)

    def face_texture(self, name, textures):
        for _ in range(20):
            if isinstance(name, dict):
                name = name['sprite']
            if not name.startswith('#'):
                return self.texture(name)
            name = textures[name[1:]]
        raise ValueError('Cyclic model texture reference')

    def render(self, data):
        gui = data['display']['gui']
        rotate = rotation_matrix(gui.get('rotation', [0, 0, 0]))
        scale = np.array(gui.get('scale', [1, 1, 1]))
        translation = np.array(gui.get('translation', [0, 0, 0]))
        pixels = np.zeros((self.size, self.size, 4), dtype=np.uint8)
        depths = np.full((self.size, self.size), -np.inf)
        for element in data.get('elements', []):
            lower, upper = np.array(element['from']), np.array(element['to'])
            element_rotate = np.eye(3)
            origin = np.zeros(3)
            if element.get('rotation'):
                turn = element['rotation']
                angles = [0, 0, 0]
                angles['xyz'.index(turn['axis'])] = turn['angle']
                element_rotate = rotation_matrix(angles)
                origin = np.array(turn.get('origin', [8, 8, 8]))
            for direction, face in element['faces'].items():
                normal = np.array(NORMALS[direction]) @ element_rotate.T @ rotate.T
                if normal[2] <= 1e-8:
                    continue
                points = np.array(CORNERS[direction]) * (upper - lower) + lower
                points = (points - origin) @ element_rotate.T + origin
                camera = ((points - 8) * scale) @ rotate.T + translation
                screen = camera[:, :2] * [1, -1] * (self.size / 16) + self.size / 2
                basis = np.column_stack((screen[1] - screen[0], screen[3] - screen[0]))
                if abs(np.linalg.det(basis)) < 1e-8:
                    continue
                start = np.maximum(0, np.floor(screen.min(axis=0)).astype(int))
                stop = np.minimum(self.size, np.ceil(screen.max(axis=0)).astype(int))
                if np.any(stop <= start):
                    continue
                yy, xx = np.mgrid[start[1]:stop[1], start[0]:stop[0]]
                point_grid = np.stack((xx + .5, yy + .5), axis=-1)
                uv_basis = (point_grid - screen[0]) @ np.linalg.inv(basis).T
                a, b = uv_basis[..., 0], uv_basis[..., 1]
                inside = (a >= -1e-8) & (a <= 1 + 1e-8) & (b >= -1e-8) & (b <= 1 + 1e-8)
                z = camera[0, 2] + a * (camera[1, 2] - camera[0, 2]) + b * (camera[3, 2] - camera[0, 2])
                u, v, U, V = face.get('uv', [0, 0, 16, 16])
                uv = np.array([[u, v], [u, V], [U, V], [U, v]], float)
                uv = np.roll(uv, -(face.get('rotation', 0) // 90), axis=0)
                coords = uv[0] + a[..., None] * (uv[1] - uv[0]) + b[..., None] * (uv[3] - uv[0])
                texture = self.face_texture(face['texture'], data['textures'])
                tx = np.clip(np.floor(coords[..., 0] * texture.shape[1] / 16).astype(int), 0, texture.shape[1] - 1)
                ty = np.clip(np.floor(coords[..., 1] * texture.shape[0] / 16).astype(int), 0, texture.shape[0] - 1)
                sampled = texture[ty, tx].copy()
                light = {'up': 1, 'down': .5, 'north': .8, 'south': .8, 'west': .62, 'east': .62}[direction]
                sampled[..., :3] = np.rint(sampled[..., :3] * light).astype(np.uint8)
                area = np.s_[start[1]:stop[1], start[0]:stop[0]]
                visible = inside & (sampled[..., 3] > 0) & (z > depths[area] + 1e-8)
                pixels[area][visible] = sampled[visible]
                depths[area][visible] = z[visible]
        return Image.fromarray(pixels)


def main():
    parser = argparse.ArgumentParser(description='Render Minecraft stair icons using their GUI model transform.')
    parser.add_argument('client_jar', type=Path)
    parser.add_argument('--size', type=int, default=128)
    parser.add_argument('--output', type=Path, default=ROOT / 'dist/items')
    args = parser.parse_args()
    if not 32 <= args.size <= 512:
        parser.error('--size must be between 32 and 512')
    args.output.mkdir(parents=True, exist_ok=True)
    count = 0
    with zipfile.ZipFile(args.client_jar) as archive:
        renderer = StairRenderer(archive, args.size)
        for path in sorted(archive.namelist()):
            if not path.startswith('assets/minecraft/items/') or not path.endswith('_stairs.json'):
                continue
            spec = json.loads(archive.read(path))['model']
            if spec['type'] != 'minecraft:model':
                raise ValueError('Unsupported stair item model: ' + path)
            image = renderer.render(renderer.model(spec['model']))
            if image.getbbox() is None:
                raise ValueError('Empty stair image: ' + path)
            image.save(args.output / (Path(path).stem + '.png'), optimize=True)
            count += 1
    print(f'Rendered {count} stair icons at {args.size} × {args.size} using Minecraft GUI transforms.')


if __name__ == '__main__':
    main()
