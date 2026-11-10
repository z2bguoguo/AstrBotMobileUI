import importlib.util
import json
from pathlib import Path
import tempfile
import unittest

spec=importlib.util.spec_from_file_location('core_bundle',Path(__file__).parents[1]/'build_core.py')
builder=importlib.util.module_from_spec(spec)
spec.loader.exec_module(builder)

class CoreBundleTest(unittest.TestCase):
    def test_bundle_contains_exact_embedded_scripts_and_architecture(self):
        with tempfile.TemporaryDirectory() as folder:
            root=Path(folder)
            metadata={'abi':1,'architecture':'a'*64,'version':42}
            (root/'metadata.json').write_text(json.dumps(metadata))
            for name in ('main.js','ui.js','z-extra.js','locales.json','fields.json'):
                (root/name).write_text('fixture '+name,encoding='utf-8')
            result=json.loads(builder.payload(root/'metadata.json',root))
            self.assertEqual(result['architecture'],metadata['architecture'])
            self.assertEqual(result['version'],42)
            self.assertEqual(result['main.js'],'fixture main.js')
            self.assertEqual(result['ui.js'],'fixture ui.js')
            self.assertEqual(result['z-extra.js'],'fixture z-extra.js')
            self.assertEqual(result['locales.json'],'fixture locales.json')
