import argparse
import concurrent.futures
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import urllib.request
import zipfile

PROJECT = Path(__file__).resolve().parent.parent
CACHE = PROJECT / 'build' / 'standalone-deps'
CACHE.mkdir(parents=True, exist_ok=True)


def fetch(url, destination, expected=None, algorithm='sha1'):
    if destination.exists() and (not expected or hashlib.new(algorithm, destination.read_bytes()).hexdigest() == expected):
        return destination
    with urllib.request.urlopen(url, timeout=90) as response:
        data = response.read()
    if expected and hashlib.new(algorithm, data).hexdigest() != expected:
        raise ValueError('Dependency checksum mismatch: ' + destination.name)
    destination.parent.mkdir(parents=True, exist_ok=True)
    temporary = destination.with_suffix(destination.suffix + '.part')
    temporary.write_bytes(data)
    temporary.replace(destination)
    return destination


def official_metadata(url):
    with urllib.request.urlopen(url, timeout=45) as response:
        return json.load(response)


def maven_jar(path, filename):
    url = 'https://maven.fabricmc.net/' + path
    with urllib.request.urlopen(url + '.sha256', timeout=45) as response:
        checksum = response.read().decode().strip().split()[0]
    return fetch(url, CACHE / filename, checksum, 'sha256')


def main():
    properties = dict(line.split('=', 1) for line in (PROJECT / 'gradle.properties').read_text().splitlines() if '=' in line and not line.startswith('#'))
    targets = json.loads((PROJECT / 'minecraft-targets.json').read_text())
    parser = argparse.ArgumentParser(description='Build Stockpile against the selected Minecraft release.')
    parser.add_argument('--minecraft-version', choices=targets, default=properties['minecraft_version'])
    version = parser.parse_args().minecraft_version
    target = targets[version]
    build = PROJECT / 'build' / 'standalone-targets' / version
    mod_version = properties['mod_version'] + '+' + version
    java_dir = Path(os.environ['JAVA_HOME']) / 'bin' if os.environ.get('JAVA_HOME') else None
    javac = str(java_dir / ('javac.exe' if os.name == 'nt' else 'javac')) if java_dir else shutil.which('javac')
    jar = str(java_dir / ('jar.exe' if os.name == 'nt' else 'jar')) if java_dir else shutil.which('jar')
    if not javac or not jar:
        raise SystemExit('Install JDK 25 and set JAVA_HOME before building.')
    manifest = official_metadata('https://piston-meta.mojang.com/mc/game/version_manifest_v2.json')
    version_url = next(v['url'] for v in manifest['versions'] if v['id'] == version)
    metadata = official_metadata(version_url)
    client = metadata['downloads']['client']
    game = fetch(client['url'], CACHE / f'minecraft-{version}.jar', client['sha1'])
    jobs = []
    for library in metadata['libraries']:
        artifact = library.get('downloads', {}).get('artifact')
        if artifact and 'natives' not in library['name']:
            jobs.append((artifact['url'], CACHE / Path(artifact['path']).name, artifact.get('sha1')))
    with concurrent.futures.ThreadPoolExecutor(max_workers=8) as pool:
        libraries = list(pool.map(lambda args: fetch(*args), jobs))
    loader_version = properties['loader_version']
    loader = maven_jar(f'net/fabricmc/fabric-loader/{loader_version}/fabric-loader-{loader_version}.jar', 'fabric-loader.jar')
    api_version = target['fabric_api']
    api = maven_jar(f'net/fabricmc/fabric-api/fabric-api/{api_version}/fabric-api-{api_version}.jar', f'fabric-api-{api_version}.jar')
    mixin = maven_jar('net/fabricmc/sponge-mixin/0.17.5+mixin.0.8.7/sponge-mixin-0.17.5+mixin.0.8.7.jar', 'mixin.jar')
    nested = CACHE / 'api-modules' / version
    nested.mkdir(parents=True, exist_ok=True)
    modules = []
    with zipfile.ZipFile(api) as archive:
        for entry in archive.namelist():
            if entry.startswith('META-INF/jars/') and entry.endswith('.jar'):
                module = nested / Path(entry).name
                module.write_bytes(archive.read(entry))
                modules.append(module)
    menu = target['mod_menu']
    modmenu = fetch(menu['url'], CACHE / ('modmenu-' + menu['version'] + '.jar'), menu['sha512'], 'sha512')
    classpath = os.pathsep.join(str(p) for p in [game, loader, mixin, modmenu, *libraries, *modules])
    classes = build / 'classes' / 'java' / 'main'
    if classes.exists():
        shutil.rmtree(classes)
    classes.mkdir(parents=True, exist_ok=True)
    source_roots = [PROJECT / 'src/main/java', PROJECT / 'src/versions' / version / 'java']
    sources = [str(p) for source in source_roots for p in source.rglob('*.java')]
    subprocess.run([javac, '--release', '25', '-proc:none', '-cp', classpath, '-d', str(classes), *sources], check=True)
    resources = build / 'resources' / 'main'
    if resources.exists():
        shutil.rmtree(resources)
    shutil.copytree(PROJECT / 'src' / 'main' / 'resources', resources, dirs_exist_ok=True)
    shutil.copytree(PROJECT / 'dist', resources / 'stockpile-web', dirs_exist_ok=True, ignore=shutil.ignore_patterns('downloads'))
    mod = resources / 'fabric.mod.json'
    mod.write_text(mod.read_text().replace('${version}', mod_version).replace('${minecraft_version}', version).replace('${loader_version}', loader_version))
    (resources / 'LICENSE_stockpile').write_text((PROJECT / 'LICENSE').read_text())
    (resources / 'NOTICE_stockpile').write_text((PROJECT / 'NOTICE').read_text())
    output = PROJECT / 'build' / 'libs' / f'stockpile-{mod_version}.jar'
    output.parent.mkdir(exist_ok=True)
    subprocess.run([jar, '--create', '--file', str(output), '-C', str(classes), '.', '-C', str(resources), '.'], check=True)
    (build / 'classpath.txt').write_text(classpath)
    print(f'Compiled Minecraft {version} mod:', output)


if __name__ == '__main__':
    main()
