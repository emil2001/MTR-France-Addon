# MTR France Addon for GTNH

The user requested a source port of MTR France Addon after reviewing its compatibility with the existing MTR GTNH port. The target is GTNH 2.9.0-RC-2, Forge 1.7.10, and the existing MTR 4.0.5 port. Work and game tests stay in copied clients; native tests use a hidden window and silent output. Reverse SSH forwarding is prohibited. Documentation and release descriptions are in English.

## Source and approach

Use upstream V1.2.0, commit `cb921539f94d27759c029b575be7dfd45294c416`, under its MIT license. This version has 98 registered blocks, 122 registered items including 24 rail tools, one block entity, 488 vehicle definitions, 450 signs and three decorative rails. Preserve source IDs, behavior and credits. The two seats are decorative in upstream; the port does not invent a seating system.

The implementation choice is a separate reobfuscated Forge addon using the existing mappings API, native FML lifecycle, sided proxies and UniMixins. A resource pack alone would omit the Java blocks and rail tools. Embedding France classes in MTR would couple two independently maintained mods, so the addon remains separate. These are implementation choices made for this port, not additional preferences supplied by the user.

Only confirmed shared API gaps belong in MTR: missing map colors, explicit blast resistance, no-drop settings, and France language loading. An updated MTR dependency must be delivered if those bridges change. Do not embed another copy of MTR, mappings, Core or UniMixins in the addon.

## Required behavior

- Register the original blocks/items and creative tabs on both sides. Client renderer classes must not be resolved during dedicated-server initialization.
- Keep French platforms compatible with normal train boarding. Preserve invisible-platform collision, culling, unbreakability and no-drop behavior.
- Preserve all 12 extra rail speeds, their 24 tools and the original vanilla/MTR enum ordinals. Apply the arrivals limit of 30 through the original mixin target.
- Preserve ticket machines, ticket barriers, signs and logos through placement, interaction, destruction, resource reload and world reload.
- Load the native MTR vehicle, sign, rail and sound resources through the existing loaders. Correct proven path/schema defects using existing authored assets. Do not invent missing liveries, sounds or geometry, or silently remove vehicle IDs. Keep an exact allowlist and affected-ID report for genuinely missing upstream files; test safe loader behavior for those entries.
- Normalize resource identifiers consistently where modern upstream files contain invalid case or spelling. Keep the MIT license and upstream credits in the JAR.

## Joban displays

The 14 France PIDS presets are optional Joban assets in upstream; France Java does not register a display host or depend on Joban. The user explicitly chose the core addon first and deferred native displays. Their existing resources remain packaged without claiming Joban execution. A later display port needs a separate design and verification matrix for all 14 presets.

## Acceptance

Produce an ordinary addon JAR with consistent version metadata, an updated MTR JAR when required, source commits, SHA-256 and a concise English installation/verification report. Confirm real registry counts and IDs, rail semantics, server-only class loading, block interactions and resource closure. Exercise native loading and representative French vehicles/blocks in an owned copied client, and run a dedicated-server smoke without a client-class dependency. Report unavailable upstream assets and untested behavior explicitly.
