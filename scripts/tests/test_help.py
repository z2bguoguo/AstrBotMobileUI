import importlib.util
import json
from pathlib import Path
import unittest
from unittest.mock import patch

ROOT=Path(__file__).resolve().parents[2]
spec=importlib.util.spec_from_file_location('help_builder', ROOT/'scripts/build_help.py')
builder=importlib.util.module_from_spec(spec)
spec.loader.exec_module(builder)

class HelpTest(unittest.TestCase):
    def test_committed_output_matches_reviewed_inputs(self):
        builder.build(check=True)

    def test_new_unreviewed_field_fails_instead_of_getting_type_only_padding(self):
        original=Path.read_text
        def read(path,*args,**kwargs):
            text=original(path,*args,**kwargs)
            if path.name=='field-names.json':
                data=json.loads(text)
                data['future_unreviewed_field']={'zh':'新字段','en':'New field'}
                return json.dumps(data)
            return text
        with patch.object(Path,'read_text',read):
            with self.assertRaisesRegex(ValueError,'Unreviewed field'):
                builder.generate()

    def test_bundle_fits_existing_native_loader_limits(self):
        assets=ROOT/'app/src/main/assets/core'
        data={p.name:p.read_text() for p in assets.iterdir() if p.suffix in ('.js','.json')}
        # Upper bound includes escaped JSON source literals and signed envelope.
        payload=json.dumps(data,ensure_ascii=False)
        source='var CoreData=JSON.parse('+json.dumps(json.dumps({k:json.loads(v) for k,v in data.items() if k.endswith('.json')},ensure_ascii=False),ensure_ascii=False)+');'+''.join(v for k,v in data.items() if k.endswith('.js'))
        self.assertLess(len(source.encode('utf-16-le'))//2,4*1024*1024)
        self.assertLess(len(payload.encode())*4//3+2048,8*1024*1024)
