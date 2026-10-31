"""Resource regressions; RESOURCE_ROOT permits a RED run on the unmodified source."""
import importlib.util
import json
import os
from pathlib import Path
import unittest

BASE = Path(__file__).resolve().parents[1]
ROOT = Path(os.environ.get('RESOURCE_ROOT', BASE / 'build/resources'))

def setUpModule():
    if not ROOT.exists() and 'RESOURCE_ROOT' not in os.environ:
        spec = importlib.util.spec_from_file_location('prepare', BASE / 'prepare_resources.py')
        prepare = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(prepare)
        prepare.stage(BASE.parent / 'fabric/src/main/resources', ROOT)


def read(path):
    return json.loads((ROOT / path).read_text())

class Repairs(unittest.TestCase):
    def test_sign_size(self):
        signs = read('assets/mtr/mtr_custom_resources.json')['signs']
        self.assertFalse(any('isSmall' in s for s in signs))
        self.assertEqual(293, sum(s.get('small', False) for s in signs))

    def test_manifest_texture_repairs(self):
        vehicles = read('assets/mtr/mtr_custom_resources.json')['vehicles']
        for vehicle in vehicles:
            for model in vehicle.get('models', []):
                texture = model.get('textureResource', '')
                self.assertNotIn('tgv_duplex_motrice_argent.png', texture)
                if vehicle['id'] in ['urbosa','urbosb','urbosc','urbosd','urbose']:
                    self.assertEqual('mtr:urbos/luxtram_albedo_opacity.png', texture)

    def test_property_prefix_repairs(self):
        for folder in ['s43','urbos3']:
            for p in (ROOT / ('assets/mtr/' + folder)).glob('properties*.json'):
                self.assertNotIn('mtr:s43tec_lrt2010/', p.read_text())
                self.assertNotIn('mtr:none_gangway/', p.read_text())

    def test_caf_textures(self):
        forward = ROOT / 'assets/mtr/urbos3/cafforward'
        self.assertEqual((BASE.parent / 'fabric/src/main/resources/assets/mtr/urbos3/cafforward/display.png').read_bytes(), (forward / 'displays.png').read_bytes())
        self.assertEqual((ROOT / 'assets/mtr/urbos3/display.png').read_bytes(), (forward / 'display.png').read_bytes())

    def test_native_announcements(self):
        import re
        for event, entry in read('assets/mtrfranceaddon/sounds.json').items():
            self.assertRegex(event, r'^[a-z0-9/._-]+$')
            for sound in entry['sounds']:
                name = sound if isinstance(sound, str) else sound['name']
                namespace, path = name.split(':', 1)
                self.assertRegex(path, r'^[a-z0-9/._-]+$')
                self.assertTrue((ROOT / ('assets/' + namespace + '/sounds/' + path + '.ogg')).exists(), name)

    def test_switch_sign_faces_use_existing_sign_texture(self):
        model = read('assets/mtrfranceaddon/models/block/switchohsignblock.json')
        self.assertEqual('mtrfranceaddon:block/pole', model['textures']['12'])
        self.assertEqual('mtrfranceaddon:block/switchohsignblock', model['textures']['13'])
        for texture in ('pole', 'switchohsignblock'):
            self.assertTrue((ROOT / ('assets/mtrfranceaddon/textures/block/' + texture + '.png')).is_file())
        self.assertEqual(6, sum(face['texture'] == '#13' for element in model['elements'] for face in element.get('faces', {}).values()))
        for element in model['elements']:
            for face in element.get('faces', {}).values():
                self.assertIn(face['texture'].removeprefix('#'), model['textures'])

    def test_switch_particle_key(self):
        textures = read('assets/mtrfranceaddon/models/block/switchohsignblock.json')['textures']
        self.assertEqual('mtrfranceaddon:block/pole', textures.get('particle'))
        self.assertNotIn('partice', textures)

    def test_native_pack(self):
        self.assertEqual(6, read('pack.mcmeta')['pack']['pack_format'])

class Closure(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        if 'RESOURCE_ROOT' in os.environ:
            raise unittest.SkipTest('closure mutation tests use staged resources only')
        spec = importlib.util.spec_from_file_location('prepare', BASE / 'prepare_resources.py')
        cls.prepare = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(cls.prepare)

    def test_directory_closure(self):
        self.assertEqual([], self.prepare.validate(ROOT)['errors'])

    def test_removed_id_rejected(self):
        path = ROOT / 'assets/mtr/mtr_custom_resources.json'
        original = path.read_bytes()
        try:
            value = json.loads(original)
            value['vehicles'].pop()
            path.write_text(json.dumps(value))
            self.assertIn('vehicles ID set changed or duplicated', self.prepare.validate(ROOT)['errors'])
        finally:
            path.write_bytes(original)

    def test_new_missing_texture_rejected(self):
        path = ROOT / 'assets/mtr/tgv_pse/tgv_pse_carmillon.png'
        original = path.read_bytes()
        try:
            path.unlink()
            errors = self.prepare.validate(ROOT)['errors']
            self.assertTrue(any('unexpected direct:' in e and 'tgv_pse_carmillon.png' in e for e in errors))
        finally:
            path.write_bytes(original)

    def test_used_obj_material_texture_rejected(self):
        path = ROOT / 'assets/mtr/urbos3/cafforward/displays.png'
        original = path.read_bytes()
        try:
            path.unlink()
            self.assertTrue(any('unexpected modelMaterial:' in e and 'displays.png' in e for e in self.prepare.validate(ROOT)['errors']))
        finally:
            path.write_bytes(original)

    def test_recording_byte_change_rejected(self):
        path = next((ROOT / 'assets/mtrfranceaddon/sounds').rglob('*.ogg'))
        original = path.read_bytes()
        try:
            path.write_bytes(original + b'changed')
            self.assertTrue(any('recording missing or altered:' in e for e in self.prepare.validate(ROOT)['errors']))
        finally:
            path.write_bytes(original)

    def test_native_sign_face_graph_rejected(self):
        path = ROOT / 'assets/mtrfranceaddon/models/block/switchohsignblock.json'
        original = path.read_bytes()
        try:
            value = json.loads(original)
            value['elements'][1]['faces']['north']['texture'] = '#17'
            path.write_text(json.dumps(value))
            self.assertTrue(any('undefined/cyclic native model texture #17' in e for e in self.prepare.validate(ROOT)['errors']))
        finally:
            path.write_bytes(original)

    def test_gtnhlib_rejects_old_pack_format(self):
        path = ROOT / 'pack.mcmeta'
        original = path.read_bytes()
        try:
            value = json.loads(original)
            value['pack']['pack_format'] = 1
            path.write_text(json.dumps(value))
            self.assertIn('GTNHLib rejects blockstates with pack_format below 4', self.prepare.validate(ROOT)['errors'])
        finally:
            path.write_bytes(original)

    def test_removed_announcement_rejected(self):
        path = ROOT / 'assets/mtrfranceaddon/sounds.json'
        original = path.read_bytes()
        try:
            value = json.loads(original)
            value.pop('idfm_jingle')
            path.write_text(json.dumps(value))
            self.assertIn('announcement IDs or definitions changed', self.prepare.validate(ROOT)['errors'])
        finally:
            path.write_bytes(original)

    def test_dangling_announcement_event_rejected(self):
        path = ROOT / 'assets/mtrfranceaddon/sounds.json'
        original = path.read_bytes()
        try:
            value = json.loads(original)
            value['idfm_jingle']['sounds'] = [{'name': 'mtrfranceaddon:review_missing_event', 'type': 'event'}]
            path.write_text(json.dumps(value))
            errors = self.prepare.validate(ROOT)['errors']
            self.assertIn('announcement IDs or definitions changed', errors)
            self.assertTrue(any('undefined announcement event: mtrfranceaddon:review_missing_event' in e for e in errors))
        finally:
            path.write_bytes(original)

    def test_built_jar_and_duplicate_entries(self):
        import tempfile
        import warnings
        import zipfile
        with tempfile.TemporaryDirectory() as temp:
            jar = Path(temp) / 'addon.jar'
            with zipfile.ZipFile(jar, 'w') as archive:
                for path in ROOT.rglob('*'):
                    if path.is_file():
                        archive.write(path, path.relative_to(ROOT).as_posix())
            self.assertEqual([], self.prepare.validate(jar)['errors'])
            with zipfile.ZipFile(jar, 'a') as archive, warnings.catch_warnings():
                warnings.simplefilter('ignore', UserWarning)
                archive.writestr('pack.mcmeta', '{}')
            with self.assertRaisesRegex(ValueError, 'duplicate ZIP entry'):
                self.prepare.validate(jar)

if __name__ == '__main__':
    unittest.main()
