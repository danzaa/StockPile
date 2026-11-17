import argparse
import json
import os
from pathlib import Path
import shutil
import subprocess

root = Path(__file__).resolve().parent.parent
properties = dict(line.split('=', 1) for line in (root / 'gradle.properties').read_text().splitlines() if '=' in line and not line.startswith('#'))
parser = argparse.ArgumentParser(description='Test the selected Stockpile build against its Minecraft dependencies.')
targets = json.loads((root / 'minecraft-targets.json').read_text())
parser.add_argument('--minecraft-version', choices=targets, default=properties['minecraft_version'])
version = parser.parse_args().minecraft_version
build = root / 'build' / 'targets' / version
cache = build / 'classpath.txt'
if not cache.exists():
    raise SystemExit('Run python3 scripts/build-standalone.py first.')
java_dir = Path(os.environ['JAVA_HOME']) / 'bin' if os.environ.get('JAVA_HOME') else None
javac = str(java_dir / ('javac.exe' if os.name == 'nt' else 'javac')) if java_dir else shutil.which('javac')
java = str(java_dir / ('java.exe' if os.name == 'nt' else 'java')) if java_dir else shutil.which('java')
classpath = os.pathsep.join([str(build / 'classes/java/main'), str(build / 'resources/main'), cache.read_text()])
out = build / 'test-classes'
out.mkdir(parents=True, exist_ok=True)
generated = build / 'test-generated'
generated.mkdir(parents=True, exist_ok=True)
render_test = generated / 'SettingsRenderSmoke.java'
render_test.write_text((root / 'tests/SettingsRenderSmoke.java.template').read_text().replace('__RENDER_PIPELINE__', targets[version]['test_render_pipeline']))
subprocess.run([javac, '--release', '25', '-proc:none', '-cp', classpath, '-d', str(out), str(root / 'tests/BackendSmoke.java'), str(root / 'tests/ShulkerScannerSmoke.java'), str(root / 'tests/SettingsCommandsSmoke.java'), str(render_test)], check=True)
subprocess.run([java, '-cp', str(out) + os.pathsep + classpath, 'BackendSmoke'], check=True)

subprocess.run([java, '-Dstockpile.test.minecraft=' + version, '-cp', str(out) + os.pathsep + classpath, 'ShulkerScannerSmoke'], check=True)

subprocess.run([java, '-cp', str(out) + os.pathsep + classpath, 'SettingsCommandsSmoke'], check=True)

subprocess.run([java, '-cp', str(out) + os.pathsep + classpath, 'SettingsRenderSmoke'], check=True)
