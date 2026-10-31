# V1.2.0 resource staging and closure

`prepare_resources.py` uses Python standard library only. The original upstream tree is unchanged. Source IDs and manifest content are preserved except the demonstrated sign-size and texture repairs; a canonical manifest checksum rejects additional changes, including silently removing incomplete PSE model references.

## Reproduce

From the addon directory:

```sh
python3 gtnh/prepare_resources.py fabric/src/main/resources gtnh/build/resources --mtr /absolute/path/to/MTR-development.jar --report gtnh/resource-validation.json
python3 -m unittest discover -s gtnh/tests -v
python3 gtnh/prepare_resources.py --validate /absolute/path/to/final-addon.jar --mtr /absolute/path/to/MTR-development.jar --report /absolute/path/to/final-resource-validation.json
```

The two positional arguments are also sufficient for staging. Without `--mtr`, exact built-in resources previously verified against `.16` are deferred and `mtrBuiltinClosureChecked` is **false**. Supply the actual installed/current MTR JAR to complete dependency closure. A passing allowlist check means no unexpected failures; it does not mean the upstream addon has no missing resources.

## Demonstrated repairs

- Convert all 209 old `isSmall` fields to `small`, preserving their values. Across the full sign set, 293 signs are small (52 from the old fields, 241 already using `small`).
- Repair six Duplex argent textures and five Urbos manifest fallbacks to their demonstrated existing PNGs.
- Repair S43/CAF gangway texture prefixes in exactly the documented 12/4 properties files.
- Retain the original CAF forward 512×512 PNG as `displays.png`, then restore the 16×16 forward `display.png` from the existing root texture. The two 512×512 CAF images have identical pixels but different encoded bytes; staging preserves the original forward recording of those pixels.
- Resolve three announcement spelling mistakes to existing recordings. Normalize announcement keys, directory/file paths and references with lowercase followed by replacing characters outside `[a-z0-9/._-]` with `_`; reject collisions. All 347 original OGG byte hashes are checked, including 47 announcements. Every renamed recording's original path, packaged path and hash is shipped in the ledger.
- Repair ten `mtr:/idf_s43/` announcement/door declarations to `mtr:idf_s43/`. Their exact recordings already exist without the leading slash; a JAR ZIP lookup cannot resolve the resulting `sounds//idf_s43/` name. This additional native packaging defect was identified by the closure check.
- Normalize locale filenames, preserve all 33 JSON translation dictionaries exactly, preserve upstream `pack_format=6`, preserve upstream MIT `LICENSE.txt` verbatim. Translation namespace/key alias handling belongs to root's `LegacyTranslations` integration.
- Exclude Fabric bootstrap metadata/mixins; native bootstrap overlays belong to root.

## Native switch-sign correction and block/item closure

Root's native decoder reproduced missing sign faces in every facing and item form of `switchohsignblock`. The original model defines `12=mtrfranceaddon:block/pole` and `13=mtrfranceaddon:block/switchohsignblock`; both exact PNGs exist. Its six sign faces incorrectly reference undefined `#17`. Staging changes those six face references to `#13`, preserving geometry, UVs, credit and IDs. It also corrects the model's sole `partice` typo to `particle`, preserving the existing pole texture value. No other model is rewritten.

Two regressions first failed against the immutable original. The validator now traverses addon blockstates, parent inheritance and active face/particle/generated-layer texture edges across **255 block/item model files, 253 concrete roots**. Abstract sign/platform templates are resolved through their concrete children. A mutation reintroducing `#17` is rejected, including through the item parent. Minecraft-owned parent/texture edges remain the native model bridge's responsibility.

Thirty-eight absent `layer0` declarations belong to item models inheriting explicit 3D geometry, so generated layers are inactive. Their exact paths/model users remain separate warnings in the ledger. No missing item icon is invented or substituted.

## Native pack-format correction

The plan originally requested `pack_format=1` based on vanilla 1.7.10. Native screenshots then showed every addon block missing. Root exported the transformed `FileResourcePack` bytecode: `modifyExpressionValue$boo000$gtnhlib$nhlib$blockOldModels` checks specifically `assets/<namespace>/blockstates/*.json` and rejects them when the pack format is below **4** (bytecode offsets 225–230). The exact evidence is `work/france-addon-port/native/file-resourcepack.javap.txt`, lines 232–355. Model files and custom vehicle manifests are outside that filter, explaining their separate behavior.

Staging now preserves the original upstream format **6**. Two pack regressions were run RED against the then-current format-1 stage: both failed. The behavioral validator explicitly rejects format 1 (and all formats below 4); the preservation check also rejects any departure from upstream 6. This is a correction justified by native evidence, not an identifier or asset change. Root owns the rebuilt-JAR/native rendering confirmation.

## Verification evidence

Six initial regressions were first run against immutable `upstream/fabric/src/main/resources`: **five failed and one errored** (the expected missing CAF texture), demonstrating the initial checks. The initial format-1 expectation was subsequently disproved by the native GTNHLib blockstate filter; its corrected regression preserves upstream format 6.

The prepared tree and a ZIP/JAR fixture then pass **18 tests**. Tests also remove a vehicle ID, remove a manifest texture, remove the used CAF OBJ material PNG, alter OGG bytes and introduce duplicate ZIP entries, checking that each failure is rejected. Two additional review regressions first reproduced acceptance of a removed announcement and a dangling announcement event reference; both are now rejected. All 47 normalized announcement IDs and definitions are pinned, and every announcement is independently rooted through recursive event/file traversal (including dangling-reference and cycle checks). The validator reads final JAR entries directly, without extraction, and rejects duplicate entries/JSON keys.

The explicit closure run against `MTR-GTNH-4.0.5-gtnh.16-dev-all.jar` reports **zero unexpected errors**:

| Checked graph | Result |
|---|---:|
| Vehicles / signs / decorative rails | 488 / 450 / 3 |
| Unique property/definition pairs | 832 |
| Existing property/definition pairs traversed | 809 |
| Used vehicle and rail models | 90 |
| Reachable legacy/BVE sound events | 895 |
| Independently rooted announcement definitions | 47 |

It checks normalized direct references, per-pair position definition names, active gangway/barrier references, used OBJ materials and their diffuse PNGs, legacy index expansion including empty bases, BVE configurations/motor data and sound event dependencies, and event-to-file closure. Disabled auxiliary fields and unreachable declared files remain separately classified. Exact affected vehicle IDs are retained and newly affected IDs are rejected. Recording signatures and hashes, JSON/Blockbench parsing, announcement identifiers, translations, license and pack version are checked in both directory and final-JAR mode.

## Exact unresolved upstream/runtime gaps

`upstream-resource-gaps.json` is the source ledger. Its generated packaged name is `france-upstream-resource-gaps.json`; `france-resource-validation.json` contains the current check result. Repaired paths are excluded from the missing-path allowlist.

| Remaining category | Unique gaps |
|---|---:|
| PSE direct references (44 metadata JSONs and one livery PNG) | 45 |
| Disabled gangway/barrier placeholder textures | 13 |
| Inactive generated item-layer texture declarations | 38 |
| Declared missing MTR sound files (mostly unreachable at configured speeds) | 315 |
| Reachable missing MP59 file, `son_mp59/acceleration/speed_9c.ogg` | 1 |
| Undefined reachable legacy events | 344 |
| Missing BVE configurations (`0`, `ktm`, `urbos`) | 3 |
| Inactive configured MTR built-in BVE events (`a_train_motor0/1/4/5`, `a_train_rub`) | 5 |

The last five are an additional finding beyond the original handoff: the exact `.16` built-in `a_train/sound.cfg` refers to them, but that JAR's `assets/mtr/sounds.json` does not define them. All 329 addon users of `a_train` are identified. These five are configured but inactive: the motor tables use only 2/3/6/7, and the zero regeneration limit keeps shoe volume zero. They are warnings, not demonstrated audible failures. No audio/configuration/livery/geometry has been substituted to hide a gap.

Static closure cannot prove rendering, animation, playback, resource reload or game compatibility. Root owns the actual production-JAR audit and native runs; the ZIP fixture is automated validator coverage, not a native-game claim. Joban presets remain upstream resources and are not claimed to execute natively.
