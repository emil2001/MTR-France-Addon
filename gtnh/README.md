# MTR France Addon for GTNH

This port runs MTR France Addon V1.2.0 on Minecraft 1.7.10 / GTNH. It adds the original French rolling stock, station blocks, signs, announcements and rail tools as a separate Forge mod.

Original source: [Team MTR France, V1.2.0](https://github.com/MTR-France-Team/MTR-France-Addon/tree/cb921539f94d27759c029b575be7dfd45294c416). The original MIT license and credits are included in the JAR.

## Install

Install these ordinary production JARs in `mods` on the client and server:

- `MTR-GTNH-4.0.5-gtnh.18.jar`
- `MTR-France-Addon-GTNH-1.2.0-gtnh.0.jar`

Remove the previous MTR JAR before installing `.18`. MTR `.18` includes the dedicated-server ticket-barrier fix; the addon requires `.17` or later. Keep GTNH's UniMixins. Yunzhu Transit Extension is optional. No separate France resource pack is required. Development and native-probe JARs are for testing only.

The original logo crafting recipe is preserved, using native wool metadata. Other blocks and rail tools keep the upstream creative inventory behavior.

The addon preserves 488 vehicle entries, 450 sign entries, three decorative rail styles, 98 blocks and 24 rail tools. Vehicle entries include individual cars, liveries, orientations and pantograph variants; this is not a count of distinct train models. Seats remain decorative, as in the original addon.

## Known source gaps

Twelve special MTR France PSE entries reference 44 metadata files and one livery texture absent from V1.2.0. Those entries retain their original IDs, but their missing artwork has not been replaced with another livery. Several vehicles also reference recordings or sound configurations absent from the original addon and MTR. See [resource-report.md](resource-report.md) and the exact affected-ID ledger, `france-upstream-resource-gaps.json`, inside the JAR.

The 14 optional Joban PIDS presets and their scripts remain packaged. Native MTR does not execute Joban scripts, so those presets need a separate display integration. This port does not include Joban Client Mod.

## Build and check

The native build uses the prepared Forge 1.7.10 mappings project and MTR development JAR, with JDK 21 and Java 8 output. It does not invoke the modern upstream Fabric/Forge build.

Pass this file as a Gradle init script to the prepared mappings project:

```sh
gradlew -p /path/to/Minecraft-Mappings/forge/1.7.10-mapping \
  -I /path/to/MTR-France-Addon/gtnh/port.init.gradle \
  -PfranceRoot=/path/to/MTR-France-Addon \
  -PfranceMtrDev=/path/to/MTR-GTNH-4.0.5-gtnh.18-dev-all.jar \
  franceCheck reobfFranceJar
```

The prepared mappings project must also receive its normal dependency/runtime properties. `franceCheck` exercises block behavior, registry IDs, native packet setup, dynamic names and translations, then validates the production JAR's resource graph against the exact MTR development JAR. Resources are staged from the unchanged upstream tree; demonstrated repairs and unresolved upstream gaps are recorded in the packaged ledger.

Resource tests also run without a JVM:

```sh
python3 -m unittest discover -s gtnh/tests -v
```

The addon JAR contains its own classes and resources. MTR, its mapping backend, Transport Simulation Core and UniMixins remain external dependencies.
