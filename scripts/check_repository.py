"""Fast structural checks, complementing Android behavior tests and secret scanning."""
import json
from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[1]
errors = []


def require(condition, message):
    if not condition:
        errors.append(message)


spec = json.loads((ROOT / 'app/src/main/assets/openapi.json').read_text())
legacy = json.loads((ROOT / 'app/src/main/assets/legacy.json').read_text())
require(bool(spec.get('paths')), 'Empty bundled OpenAPI contract')
require(len(legacy) > 100, 'Legacy catalogue unexpectedly truncated')
for item in legacy:
    require(item['path'].startswith('/api/'), f'Invalid legacy path: {item}')
    require(item['method'] in {'GET', 'POST', 'PUT', 'PATCH', 'DELETE'}, f'Invalid method: {item}')
for file in (ROOT / 'examples').glob('*.json'):
    plugin = json.loads(file.read_text())
    require(plugin.get('apiVersion') == 1, f'Invalid plugin version: {file}')
    for op in plugin['operations']:
        require(op['path'].startswith('/api/'), f'External plugin route: {file}')
        require(op['path'] in spec['paths'], f'Example targets missing route: {file}')
manifest = (ROOT / 'app/src/main/AndroidManifest.xml').read_text()
require('android:allowBackup="false"' in manifest, 'Credential backups must be disabled')
require('android:exported="false"' in manifest, 'Plugin view must not be exported')
require('io.github.astrbot.mobile' in (ROOT / 'app/build.gradle.kts').read_text(), 'Stable package ID missing')
for name in ['README.md', 'README.en.md', 'AGENTS.md', 'agent.md', 'LICENSE', 'SECURITY.md']:
    require((ROOT / name).is_file(), f'Missing {name}')
for path in ROOT.rglob('*'):
    if any(p in {'.git', '.gradle', 'node_modules', 'build', 'dist', 'cache'} for p in path.parts):
        continue
    require(not path.name.endswith(('.jks', '.keystore', '.p12', '.apk')), f'Forbidden committed artifact: {path}')
for path in (ROOT / 'docs').rglob('*.md'):
    for link in re.findall(r'\]\((/[^)#]+)(?:#[^)]*)?\)', path.read_text()):
        require((ROOT / 'docs' / (link.lstrip('/') + '.md')).exists(), f'Broken docs link: {path}: {link}')
if errors:
    print('\n'.join(errors), file=sys.stderr)
    sys.exit(1)
print(f'Repository checks passed: {len(spec["paths"])} contract paths, {len(legacy)} legacy operations')
