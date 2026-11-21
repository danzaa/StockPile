import argparse
from collections import Counter
from functools import lru_cache
import json
from pathlib import Path
import zipfile

ROOT = Path(__file__).resolve().parent.parent


def catalog(path, version):
    with zipfile.ZipFile(path) as archive:
        names = set(archive.namelist())

        @lru_cache(None)
        def tag(name, parents=()):
            if name in parents:
                return ()
            namespace, value = name.split(':', 1)
            path = f'data/{namespace}/tags/item/{value}.json'
            if path not in names:
                return ()
            values = json.loads(archive.read(path)).get('values', [])
            result = set()
            for value in values:
                value = value.get('id', '') if isinstance(value, dict) else value
                if value.startswith('#'):
                    result.update(tag(value[1:], (*parents, name)))
                elif ':' in value:
                    result.add(value)
            return tuple(sorted(result))

        def options(value):
            if isinstance(value, str):
                return tag(value[1:]) if value.startswith('#') else (value,)
            if isinstance(value, list):
                return tuple(sorted({item for child in value for item in options(child)}))
            if isinstance(value, dict):
                if 'item' in value:
                    return options(value['item'])
                if 'tag' in value:
                    return tag(value['tag'])
            return ()

        recipes = []
        skipped = Counter()
        for path in sorted(names):
            if not path.startswith('data/minecraft/recipe/') or not path.endswith('.json'):
                continue
            raw = json.loads(archive.read(path))
            kind = raw.get('type', '').removeprefix('minecraft:')
            result = raw.get('result', {})
            if kind not in {'crafting_shaped', 'crafting_shapeless', 'stonecutting', 'smelting', 'blasting'} or not isinstance(result, dict) or result.get('components'):
                skipped[kind] += 1
                continue
            if kind == 'crafting_shaped':
                symbols = Counter(''.join(raw['pattern']).replace(' ', ''))
                ingredients = [(raw['key'][symbol], amount) for symbol, amount in symbols.items()]
            elif kind == 'crafting_shapeless':
                ingredients = [(ingredient, 1) for ingredient in raw['ingredients']]
            else:
                ingredients = [(raw['ingredient'], 1)]
            grouped = Counter()
            for ingredient, amount in ingredients:
                grouped[options(ingredient)] += amount
            if not grouped or () in grouped or not result.get('id') or result.get('count', 1) < 1:
                skipped['unsupported_ingredient'] += 1
                continue
            recipes.append({
                'id': 'minecraft:' + Path(path).stem,
                'output': result['id'],
                'count': result.get('count', 1),
                'station': {'stonecutting':'stonecutter','smelting':'furnace','blasting':'blast_furnace'}.get(kind,'crafting'),
                **({'cookTicks': raw.get('cookingtime',100 if kind=='blasting' and version=='26.2' else 200) // (2 if kind=='blasting' and version=='26.3' else 1)} if kind in {'smelting','blasting'} else {}),
                'ingredients': [{'items': list(items), 'count': amount} for items, amount in sorted(grouped.items())],
            })
        return {'minecraftVersion': version, 'fuels': [{'id':'minecraft:'+name,'ticks':ticks,**({'remainder':'minecraft:bucket'} if name=='lava_bucket' else {})} for name,ticks in [('coal',1600),('charcoal',1600),('coal_block',16000),('blaze_rod',2400),('dried_kelp_block',4001),('lava_bucket',20000)]], 'recipes': recipes, 'excludedTypes': dict(sorted(skipped.items()))}


def main():
    parser = argparse.ArgumentParser(description='Extract vanilla crafting, stonecutting and cooking recipes from official Minecraft clients.')
    parser.add_argument('--jars-directory', type=Path, default=ROOT / 'build/standalone-deps')
    args = parser.parse_args()
    targets = json.loads((ROOT / 'minecraft-targets.json').read_text())
    catalogs = {version: catalog(args.jars_directory / f'minecraft-{version}.jar', version) for version in targets}
    output = ROOT / 'dist/recipes-data.js'
    output.write_text('export const recipeCatalogs=' + json.dumps(catalogs, separators=(',', ':')) + ';\n')
    for version, data in catalogs.items():
        print(version, len(data['recipes']), 'crafting/stonecutting/cooking recipes')


if __name__ == '__main__':
    main()
