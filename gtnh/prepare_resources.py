#!/usr/bin/env python3
"""Stage V1.2.0 resources, or validate a staged directory/final JAR.

No Minecraft/JVM required. --mtr must identify the exact runtime/development JAR
for sound closure; without it staging checks local closure and reports that limit.
"""
import argparse
from collections import defaultdict
import hashlib
import json
from pathlib import Path, PurePosixPath
import re
import shutil
import sys
import zipfile

HERE = Path(__file__).resolve().parent
MANIFEST = 'assets/mtr/mtr_custom_resources.json'
LEDGER = 'france-upstream-resource-gaps.json'


def load_json(data):
    def pairs(items):
        result = {}
        for key, value in items:
            if key in result:
                raise ValueError('duplicate JSON key: ' + key)
            result[key] = value
        return result
    return json.loads(data, object_pairs_hook=pairs)


def write_json(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')


def normalize(value):
    return ':'.join(re.sub(r'[^a-z0-9/._-]', '_', part.lower()) for part in value.split(':'))


def identifier(value, default='mtr'):
    return value if ':' in value else default + ':' + value


def resource_path(value, extension=None, default='mtr'):
    value = normalize(identifier(value, default))
    if extension:
        value = value.split('.')[0] + '.' + extension
    namespace, name = value.split(':', 1)
    return 'assets/' + namespace + '/' + name


class Resources:
    def __init__(self, location):
        self.location = Path(location)
        self.zip = zipfile.ZipFile(location) if self.location.is_file() else None
        names = [i.filename for i in self.zip.infolist() if not i.is_dir()] if self.zip else [p.relative_to(self.location).as_posix() for p in self.location.rglob('*') if p.is_file()]
        if len(names) != len(set(names)):
            raise ValueError('duplicate ZIP entry')
        self.names = set(names)

    def has(self, name):
        return name in self.names

    def read(self, name):
        return self.zip.read(name) if self.zip else (self.location / name).read_bytes()

    def json(self, name):
        return load_json(self.read(name))


def walk(value):
    if isinstance(value, dict):
        for key, child in value.items():
            yield key, child
            yield from walk(child)
    elif isinstance(value, list):
        for child in value:
            yield from walk(child)


def stage(source, target):
    source, target = Path(source), Path(target)
    if target.resolve() == source.resolve() or source.resolve() in target.resolve().parents or target.resolve() in source.resolve().parents:
        raise ValueError('staging destination must be outside source resources')
    if target.exists():
        shutil.rmtree(target)
    shutil.copytree(source, target)
    for name in ('fabric.mod.json', 'mtrfranceaddon.mixins.json'):
        (target / name).unlink(missing_ok=True)
    baseline = load_json((HERE / 'upstream-resource-gaps.json').read_bytes())
    fixes = baseline['knownPathFixes']
    manifest = load_json((target / MANIFEST).read_bytes())
    for sign in manifest['signs']:
        if 'isSmall' in sign:
            if 'small' in sign:
                raise ValueError('ambiguous sign size')
            sign['small'] = sign.pop('isSmall')
    replacements = {fixes['duplexTexture']['from']: fixes['duplexTexture']['to']}
    replacements.update({p: fixes['urbosTexture']['to'] for p in fixes['urbosTexture']['from']})
    def rewrite(value):
        if isinstance(value, dict):
            return {k: rewrite(v) for k,v in value.items()}
        if isinstance(value, list):
            return [rewrite(v) for v in value]
        return replacements.get(value, value) if isinstance(value, str) else value
    write_json(target / MANIFEST, rewrite(manifest))
    for fix in fixes['propertyPrefixRewrites']:
        for name in fix['propertiesFiles']:
            path = target / name
            path.write_text(path.read_text().replace(fix['from'], fix['to']), encoding='utf-8')
    for fix in fixes['cafTextureCopies']:
        shutil.copyfile(target / fix['from'], target / fix['to'])
    # Native decoder demonstrated missing sign faces in all facings and item.
    switch_path = target / 'assets/mtrfranceaddon/models/block/switchohsignblock.json'
    switch = load_json(switch_path.read_bytes())
    corrected_faces = 0
    for element in switch.get('elements', []):
        for face in element.get('faces', {}).values():
            if face.get('texture') == '#17':
                face['texture'] = '#13'
                corrected_faces += 1
    if corrected_faces != 6:
        raise ValueError('upstream switch sign must have exactly six erroneous #17 faces')
    switch['textures']['particle'] = switch['textures'].pop('partice')
    write_json(switch_path, switch)
    # Native identifiers cannot contain uppercase or accents. Keep recording bytes
    # intact and record every original path/hash rather than re-encoding audio.
    renames = []
    sound_root = target / 'assets/mtrfranceaddon/sounds'
    destinations = {}
    for path in sorted(sound_root.rglob('*.ogg')):
        old = path.relative_to(target).as_posix()
        new = normalize(old)
        if new in destinations:
            raise ValueError('sound path normalization collision: ' + old + ' / ' + destinations[new])
        destinations[new] = old
    # Read first, then remove originals: case-insensitive host filesystems need this.
    recordings = [(old, new, (target / old).read_bytes()) for new, old in destinations.items()]
    shutil.rmtree(sound_root)
    for old, new, data in recordings:
        (target / new).parent.mkdir(parents=True, exist_ok=True)
        (target / new).write_bytes(data)
        if old != new:
            renames.append({'original': old, 'packaged': new, 'sha256': hashlib.sha256(data).hexdigest()})
    repair_names = {f['from']: f['existingFile'].split('/sounds/',1)[1][:-4] for f in fixes['announcementFileRepairs']}
    sound_path = target / 'assets/mtrfranceaddon/sounds.json'
    sounds = load_json(sound_path.read_bytes())
    normalized_sounds = {}
    event_renames = {}
    for event, entry in sounds.items():
        normalized_event = normalize(event)
        if normalized_event in normalized_sounds:
            raise ValueError('sound event normalization collision: ' + event)
        if event != normalized_event:
            event_renames[event] = normalized_event
        for index, sound in enumerate(entry['sounds']):
            name = sound if isinstance(sound, str) else sound['name']
            name = identifier(name, 'mtrfranceaddon')
            if name in repair_names:
                name = 'mtrfranceaddon:' + repair_names[name]
            name = normalize(name)
            if isinstance(sound, str):
                entry['sounds'][index] = name
            else:
                sound['name'] = name
        normalized_sounds[normalized_event] = entry
    write_json(sound_path, normalized_sounds)
    # Ten upstream declarations have an accidental leading slash. Their exact
    # recordings already exist under sounds/idf_s43; a ZIP has no // aliases.
    mtr_sounds_path = target / 'assets/mtr/sounds.json'
    mtr_sounds = load_json(mtr_sounds_path.read_bytes())
    for _, entry in mtr_sounds.items():
        for sound in entry.get('sounds', []):
            if isinstance(sound, dict) and sound.get('name', '').startswith('mtr:/idf_s43/'):
                sound['name'] = sound['name'].replace('mtr:/', 'mtr:', 1)
    write_json(mtr_sounds_path, mtr_sounds)
    # Preserve upstream format 6. Native GTNHLib filters blockstates from
    # FileResourcePack when pack_format < 4, despite Minecraft being 1.7.10.
    locale_renames = {}
    for path in list((target / 'assets/mtrfranceaddon/lang').glob('*.json')):
        new = path.with_name(path.name.lower())
        if new != path:
            data = path.read_bytes()
            path.unlink()
            new.write_bytes(data)
            locale_renames[path.name] = new.name
    license_source = HERE.parent / 'LICENSE.txt'
    shutil.copyfile(license_source, target / 'LICENSE.txt')
    ledger = dict(baseline)
    ledger['recordingRenames'] = renames
    ledger['eventRenames'] = event_renames
    ledger['localeRenames'] = locale_renames
    write_json(target / LEDGER, ledger)
    return ledger


def validate(location, mtr=None):
    local = Resources(location)
    builtin = Resources(mtr) if mtr else None
    baseline = load_json((HERE / 'upstream-resource-gaps.json').read_bytes())
    errors, gaps = [], defaultdict(lambda: defaultdict(set))
    checked = defaultdict(int)
    def exists(path):
        return local.has(path) or (builtin is not None and builtin.has(path))
    def read(path):
        return local.read(path) if local.has(path) else builtin.read(path)
    def data(path):
        return load_json(read(path))
    def missing(category, path, vehicle=''):
        gaps[category][path].add(vehicle)
    def require(path, vehicle='', category='direct'):
        if not exists(path) and not (not builtin and path in baseline['verifiedBuiltinResources']):
            missing(category, path, vehicle)
            return False
        return True
    for name in sorted(local.names):
        try:
            if name.endswith(('.json', '.bbmodel', '.mcmeta')):
                local.json(name)
            if name.endswith('.png') and not local.read(name).startswith(b'\x89PNG\r\n\x1a\n'):
                errors.append('invalid PNG: ' + name)
            if name.endswith('.ogg') and not local.read(name).startswith(b'OggS'):
                errors.append('invalid OGG: ' + name)
        except (ValueError, UnicodeError) as exc:
            errors.append('invalid JSON ' + name + ': ' + str(exc))
    manifest = local.json(MANIFEST)
    manifest_hash = hashlib.sha256(json.dumps(manifest, sort_keys=True, separators=(',', ':'), ensure_ascii=False).encode()).hexdigest()
    if manifest_hash != baseline['expectedManifestSha256']:
        errors.append('manifest changed outside proven V1.2.0 repairs')
    for kind, expected in baseline['expectedIds'].items():
        ids = [x['id'] for x in manifest[kind]]
        if len(ids) != len(set(ids)) or set(ids) != set(expected):
            errors.append(kind + ' ID set changed or duplicated')
        checked[kind] = len(ids)
    if any('isSmall' in s for s in manifest['signs']):
        errors.append('obsolete isSmall sign key')
    pack_format = local.json('pack.mcmeta')['pack']['pack_format']
    if pack_format < baseline['packCompatibility']['gtnhlibMinimumBlockstateFormat']:
        errors.append('GTNHLib rejects blockstates with pack_format below 4')
    if pack_format != baseline['packCompatibility']['preservedUpstreamFormat']:
        errors.append('preserve upstream pack_format 6 for native modern models')
    if not local.has('LICENSE.txt') or local.read('LICENSE.txt') != (HERE.parent / 'LICENSE.txt').read_bytes():
        errors.append('upstream license missing or altered')
    # Resolve loader-normalized references and validate each actual model pair.
    extensions = {'textureResource': 'png', 'modelPropertiesResource': 'json', 'positionDefinitionsResource': 'json'}
    for kind in ('vehicles', 'signs', 'rails'):
        for item in manifest[kind]:
            for key, value in walk(item):
                if key in extensions and value:
                    require(resource_path(value, extensions[key]), item['id'])
                elif key == 'modelResource' and value:
                    require(resource_path(value, 'obj' if value.lower().endswith('.obj') else 'bbmodel'), item['id'])
    model_users = defaultdict(set)
    pairs = set()
    for vehicle in manifest['vehicles']:
        vid = vehicle['id']
        for field in ('models', 'bogie1Models', 'bogie2Models'):
            for model in vehicle.get(field, []):
                if model.get('modelResource'):
                    model_users[resource_path(model['modelResource'], 'obj' if model['modelResource'].lower().endswith('.obj') else 'bbmodel')].add(vid)
                prop, definitions = model.get('modelPropertiesResource'), model.get('positionDefinitionsResource')
                if not prop:
                    continue
                pp = resource_path(prop, 'json')
                if not exists(pp):
                    continue
                properties = data(pp)
                if definitions and exists(resource_path(definitions, 'json')):
                    dp = resource_path(definitions, 'json')
                    pairs.add((pp, dp))
                    names = {d['name'] for d in data(dp).get('positionDefinitions', [])}
                    for part in properties.get('parts', []):
                        for name in part.get('positionDefinitions', []):
                            if name not in names:
                                errors.append('undefined position ' + name + ' in ' + pp + ' / ' + dp)
                for key, ref in properties.items():
                    if key.endswith('Resource') and ref and (key.startswith('gangway') or key.startswith('barrier')):
                        active = any(vehicle.get('has' + ('Gangway' if key.startswith('gangway') else 'Barrier') + str(i), False) for i in (1,2))
                        require(resource_path(ref, 'png'), vid, 'activeAuxiliary' if active else 'disabledAuxiliary')
    for rail in manifest['rails']:
        model_users[resource_path(rail['modelResource'], 'obj')].add(rail['id'])
    checked['resolvedPropertyDefinitionPairs'] = len(pairs)
    checked['propertyDefinitionPairs'] = len({(m.get('modelPropertiesResource'), m.get('positionDefinitionsResource')) for v in manifest['vehicles'] for f in ('models','bogie1Models','bogie2Models') for m in v.get(f,[])})
    checked['usedModels'] = len(model_users)
    for path, users in model_users.items():
        if not exists(path) or not path.endswith('.obj'):
            continue
        lines = read(path).decode('utf-8-sig').splitlines()
        used = {line.split(None,1)[1].strip() for line in lines if line.startswith('usemtl ')}
        materials = {}
        for line in lines:
            if line.startswith('mtllib '):
                for ref in line.split()[1:]:
                    mp = same_path(path, ref, 'mtl')
                    if require(mp, ','.join(sorted(users)), 'modelMaterial'):
                        current = None
                        for ml in read(mp).decode('utf-8-sig').splitlines():
                            if ml.startswith('newmtl '):
                                current = ml.split(None,1)[1].strip()
                                materials[current] = []
                            elif ml.startswith('map_Kd ') and current:
                                materials[current].append(same_path(path, ml.split(None,1)[1].strip(), 'png'))
        for material in used:
            if material not in materials:
                errors.append('undefined used material ' + material + ' in ' + path)
            for texture in materials.get(material, []):
                require(texture, ','.join(sorted(users)), 'modelMaterial')
    # Addon blockstate -> model -> parent -> face/particle texture graph.
    # Vanilla parents/textures belong to the native model bridge; addon-owned
    # edges must close within the packaged resources.
    native_prefix = 'assets/mtrfranceaddon/models/'
    native_models = sorted(p for p in local.names if p.startswith(native_prefix) and p.endswith('.json'))
    def model_path(ref):
        namespace, name = identifier(ref, 'minecraft').split(':', 1)
        return 'assets/' + namespace + '/models/' + name + '.json'
    def inherited_model(path, stack=()):
        if path in stack:
            errors.append('native model parent cycle: ' + path)
            return {}
        model = local.json(path)
        parent = model.get('parent')
        inherited = {}
        if parent:
            parent_path = model_path(parent)
            if local.has(parent_path):
                inherited = inherited_model(parent_path, stack + (path,))
            elif parent_path.startswith(native_prefix):
                errors.append('missing addon model parent: ' + parent_path)
        merged = dict(inherited)
        merged.update(model)
        merged['textures'] = dict(inherited.get('textures', {}), **model.get('textures', {}))
        return merged
    native_roots = {p for p in native_models if p.startswith(native_prefix + 'item/')}
    for path in sorted(p for p in local.names if p.startswith('assets/mtrfranceaddon/blockstates/') and p.endswith('.json')):
        for key, ref in walk(local.json(path)):
            if key == 'model' and isinstance(ref, str):
                target_path = model_path(ref)
                if target_path.startswith(native_prefix):
                    if not local.has(target_path):
                        errors.append('missing addon blockstate model: ' + target_path)
                    else:
                        native_roots.add(target_path)
    for path in sorted(native_roots):
        model = inherited_model(path)
        textures = model.get('textures', {})
        refs = [face.get('texture', '') for element in model.get('elements', []) for face in element.get('faces', {}).values()]
        refs += [v for k,v in textures.items() if k == 'particle' or (k.startswith('layer') and not model.get('elements'))]
        for key, ref in textures.items():
            if key.startswith('layer') and model.get('elements') and not ref.startswith('#'):
                namespace, name = identifier(ref, 'minecraft').split(':', 1)
                texture_path = 'assets/' + namespace + '/textures/' + name + '.png'
                if namespace == 'mtrfranceaddon' and not local.has(texture_path):
                    missing('inactiveModelLayerTexture', texture_path, path)
        for ref in refs:
            seen = set()
            while ref.startswith('#'):
                key = ref[1:]
                if key in seen or key not in textures:
                    errors.append('undefined/cyclic native model texture ' + ref + ' in ' + path)
                    break
                seen.add(key)
                ref = textures[key]
            else:
                if ref:
                    namespace, name = identifier(ref, 'minecraft').split(':', 1)
                    texture_path = 'assets/' + namespace + '/textures/' + name + '.png'
                    if namespace == 'mtrfranceaddon' and not local.has(texture_path):
                        errors.append('missing addon model texture: ' + texture_path + ' in ' + path)
    checked['nativeBlockItemModels'] = len(native_models)
    checked['nativeModelRoots'] = len(native_roots)
    # Build event -> file/event graph with addon pack priority, namespace aware.
    events = {}
    for resources in (builtin, local):
        if resources:
            for path in resources.names:
                if re.fullmatch(r'assets/[^/]+/sounds.json', path):
                    ns = path.split('/')[1]
                    for event, entry in resources.json(path).items():
                        full = ns + ':' + event
                        if resources == local and normalize(full) != full:
                            errors.append('invalid native sound event: ' + full)
                        events[full] = (ns, entry)
    reachable = defaultdict(set)
    legacy_events = set()
    inactive_bve = defaultdict(set)
    for vehicle in manifest['vehicles']:
        vid = vehicle['id']
        base = vehicle.get('bveSoundBaseResource', '')
        if base:
            bp = resource_path('sounds/' + base + '/sound.cfg') if ':' not in base else resource_path(base.split(':')[0] + ':sounds/' + base.split(':')[1] + '/sound.cfg')
            if not builtin and base == 'a_train' and not local.has(bp):
                continue
            if require(bp, vid, 'bveConfiguration'):
                section, motor_type, regeneration_limit = '', 5, 0
                configured = []
                for line in read(bp).decode('utf-8-sig').splitlines():
                    line = re.sub(r'\s*(;|#|//).+', '', line.strip())
                    if line.startswith('[') and line.endswith(']'):
                        section = line[1:-1].replace(' ', '').lower()
                    elif '=' in line:
                        key, value = [p.strip().lower() for p in line.split('=',1)]
                        value = re.sub(r'\.wav|\s|.+/', '', value.replace('\\','/'))
                        if not value:
                            continue
                        if section == 'mtr':
                            if key.replace(' ', '') == 'motornoisedatatype':
                                motor_type = int(value)
                            elif key.replace(' ', '') == 'regenerationlimit':
                                regeneration_limit = float(value)
                        elif bve_sound_key(section, key):
                            configured.append((section, key, identifier(base) + '_' + value))
                for dep in (['train.dat'] if motor_type == 4 else ['powervol.csv','powerfreq.csv','brakevol.csv','brakefreq.csv']):
                    require(bp.rsplit('/',1)[0] + '/' + dep, vid, 'bveDependency')
                active_motors = None
                motor_path = bp.rsplit('/',1)[0] + '/train.dat'
                if motor_type == 4 and exists(motor_path):
                    active_motors = set()
                    motor_section = ''
                    for line in read(motor_path).decode('utf-8-sig').splitlines():
                        line = line.strip().lower()
                        if line.startswith('#'):
                            motor_section = line[1:].strip()
                        elif line and motor_section in ('motor_p1','motor_p2','motor_b1','motor_b2'):
                            values = line.split(',')
                            if len(values) >= 3 and float(values[1]) > 0 and float(values[2]) > 0:
                                active_motors.add(int(values[0]))
                for section, key, event in configured:
                    inactive = (section == 'motor' and active_motors is not None and int(key) not in active_motors) or (section == 'others' and key == 'shoe' and regeneration_limit <= 0) or section == 'flange'
                    (inactive_bve if inactive else reachable)[event].add(vid)
        else:
            speed = vehicle.get('legacySpeedSoundBaseResource')
            if speed is not None:
                for index in range(max(0, vehicle.get('legacySpeedSoundCount', 0))):
                    for direction in ('acceleration', 'deceleration'):
                        reachable['mtr:' + speed + '_' + direction + '_' + str(index // 3) + 'abc'[index % 3]].add(vid)
            for door in ('open','close'):
                reachable['mtr:' + vehicle.get('legacyDoorSoundBaseResource', '') + '_door_' + door].add(vid)
    # Classify BVE config dependencies independently from legacy event gaps.
    for vehicle in manifest['vehicles']:
        if not vehicle.get('bveSoundBaseResource', ''):
            speed = vehicle.get('legacySpeedSoundBaseResource')
            if speed is not None:
                for index in range(max(0, vehicle.get('legacySpeedSoundCount', 0))):
                    for direction in ('acceleration', 'deceleration'):
                        legacy_events.add('mtr:' + speed + '_' + direction + '_' + str(index // 3) + 'abc'[index % 3])
            for door in ('open','close'):
                legacy_events.add('mtr:' + vehicle.get('legacyDoorSoundBaseResource', '') + '_door_' + door)
    announcement_path = 'assets/mtrfranceaddon/sounds.json'
    announcements = local.json(announcement_path) if local.has(announcement_path) else {}
    if announcements != baseline['expectedAnnouncementDefinitions']:
        errors.append('announcement IDs or definitions changed')
    checked['announcements'] = len(announcements)
    def visit(event, users, stack=(), announcement=False):
        if event in stack:
            errors.append('sound event cycle: ' + event)
            return
        if event not in events:
            if announcement:
                errors.append('undefined announcement event: ' + event)
            elif builtin:
                for vid in users:
                    missing('undefinedLegacyEvent' if event in legacy_events else 'undefinedBveEvent', event, vid)
            return
        ns, entry = events[event]
        for sound in entry.get('sounds', []):
            name = sound if isinstance(sound, str) else sound['name']
            name = identifier(name, ns)
            if isinstance(sound, dict) and sound.get('type') == 'event':
                visit(name, users, stack + (event,), announcement)
            else:
                namespace, path = name.split(':',1)
                full = 'assets/' + namespace + '/sounds/' + path + '.ogg'
                for vid in users:
                    require(full, vid, 'reachableSoundFile')
    for event, users in reachable.items():
        visit(event, users)
    # All announcement definitions are picker roots, even without vehicle users.
    for event in announcements:
        visit('mtrfranceaddon:' + event, {''}, announcement=True)
    for event, users in inactive_bve.items():
        if builtin and event not in events:
            for vid in users:
                missing('undefinedInactiveBveEvent', event, vid)
    # All declarations are checked separately: out-of-range failures stay warnings.
    for event, (ns, entry) in events.items():
        if local.has('assets/' + ns + '/sounds.json') and event.split(':', 1)[1] in local.json('assets/' + ns + '/sounds.json'):
            for sound in entry.get('sounds', []):
                if isinstance(sound, dict) and sound.get('type') == 'event':
                    continue
                name = identifier(sound if isinstance(sound,str) else sound['name'], ns)
                namespace, path = name.split(':',1)
                full = 'assets/' + namespace + '/sounds/' + path + '.ogg'
                if ns == 'mtrfranceaddon' and normalize(name) != name:
                    errors.append('invalid announcement path: ' + name)
                require(full, '', 'declaredSoundFile')
    checked['reachableSoundEvents'] = len(reachable)
    checked['inactiveConfiguredBveEvents'] = len(inactive_bve)
    allowed = baseline['allowedGaps']
    for category, values in gaps.items():
        expected = set(allowed.get(category, []))
        unexpected = set(values) - expected
        errors.extend('unexpected ' + category + ': ' + p for p in sorted(unexpected))
        expected_users = baseline['allowedAffectedIds'].get(category, {})
        for path, users in values.items():
            if path in expected_users and users - {''} - set(expected_users[path]):
                errors.append('new affected IDs for ' + category + ': ' + path)
    for path, expected in baseline['recordingHashes'].items():
        if not local.has(path) or hashlib.sha256(local.read(path)).hexdigest() != expected:
            errors.append('recording missing or altered: ' + path)
    if not local.has(LEDGER):
        errors.append('upstream gap ledger missing')
    elif any(local.json(LEDGER).get(key) != value for key,value in baseline.items()):
        errors.append('upstream gap ledger changed')
    # Translation locale and content/key preservation (LegacyTranslations registers namespace).
    for path, expected in baseline['translations'].items():
        if not local.has(path) or local.json(path) != expected:
            errors.append('translation content changed or missing: ' + path)
    return {'checked': dict(checked), 'mtrBuiltinClosureChecked': builtin is not None,
            'mtrJarSha256': hashlib.sha256(Path(mtr).read_bytes()).hexdigest() if mtr else None,
            'gaps': {k: {p: sorted(v - {''}) for p,v in sorted(values.items())} for k,values in sorted(gaps.items())}, 'errors': sorted(set(errors))}


def same_path(base, ref, ext):
    if ':' in ref:
        return resource_path(ref, ext)
    namespace = base.split('/')[1]
    relative = PurePosixPath('/'.join(base.split('/')[2:])).parent / ref
    return resource_path(namespace + ':' + str(relative), ext)


def bve_sound_key(section, key):
    limits = {'run':1,'rolling':1,'flange':1,'motor':40,'joint':1,'switch':1}
    if section in limits:
        return key.isdigit() and int(key) < limits[section]
    return key in {'brake': {'bcrelease','bcreleasefull','bcreleasehigh','emergency'}, 'door': {'open','openleft','openright','close','closeleft','closeright'}, 'brakehandle': {'apply','release'}, 'compressor': {'attack','loop','release'}, 'others': {'noise','shoe'}}.get(section, set())


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('source', nargs='?')
    parser.add_argument('destination', nargs='?')
    parser.add_argument('--validate', metavar='DIRECTORY_OR_JAR')
    parser.add_argument('--mtr', metavar='MTR_JAR')
    parser.add_argument('--report', metavar='JSON')
    args = parser.parse_args()
    if args.validate:
        report = validate(args.validate, args.mtr)
    elif args.source and args.destination:
        stage(args.source, args.destination)
        report = validate(args.destination, args.mtr)
        write_json(Path(args.destination) / 'france-resource-validation.json', report)
    else:
        parser.error('provide source destination, or --validate')
    if args.report:
        write_json(Path(args.report), report)
    print(json.dumps({'checked': report['checked'], 'mtrBuiltinClosureChecked': report['mtrBuiltinClosureChecked'], 'gapCounts': {k: len(v) for k,v in report['gaps'].items()}, 'errors': report['errors']}, indent=2))
    return 1 if report['errors'] else 0

if __name__ == '__main__':
    sys.exit(main())
