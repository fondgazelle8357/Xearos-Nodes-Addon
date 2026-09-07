# Nodes Overlay

Nodes Overlay is a client-side Fabric addon that displays territories,
ownership, nodes, core chunks, and live war activity on Xaero's
World Map and Xaero's Minimap.

The addon reads the configured public map data at runtime:

- [Town data](https://map.crusalis.net/nodes/towns.json)
- [World data](https://map.crusalis.net/nodes/world.json)
- [Port data](https://map.crusalis.net/nodes/ports.json)
- [Web map](https://map.crusalis.net/)

It does not need to be installed on the server.

## War-time performance (1.1.1)

Live war messages update markers and occupation state immediately. Highlight
changes are batched once per client tick and limited to affected map regions;
attack icons do not invalidate territory pixels. The minimap uses cached
active-attack lists and skips world-map overview-label construction.

War-triggered background map checks are limited to one start per 15 seconds.
`/xnma refresh` still requests an immediate refresh when no request is in flight.
Identical responses retain the current snapshot, and town/port-only changes reuse
unchanged world geometry. These changes target war-related stalls; frame-time
improvement still needs testing in an actual multiplayer war session.

## Features

- Exact chunk-based territory shapes with nation or town colors.
- A low-zoom political overview that keeps broad nation-colored land, outside
  borders, ports, war markers, and stable nation or independent-town labels
  while hiding core and node clutter until the detailed zoom level.
- Ownership states for owned, annexed, captured, claimed, and unowned land.
- Diagonal occupier-colored hatching over the original owner's base color.
- Node icons and production details sourced from `world.json`.
- Port markers sourced from `ports.json`, with newer coordinates, owner and
  access details learned from in-game `/port info` responses.
- Core-chunk outlines that can be always visible, zoom-dependent,
  attack-only, or disabled.
- World-map territory selection with ownership, production, neighbors, core,
  and current war information.
- Immediate local updates from supported war chat messages.
- Double-sized live-war icons on the world map and minimap, plus temporary
  world-space attack markers.
- All distant world waypoints shrink to two-thirds size beyond 20 blocks.
- Asynchronous downloads, conditional HTTP requests, last-known-good caching,
  and periodic reconciliation.
- Per-server settings and caches so data cannot leak between servers.

## Supported versions

Minecraft and Fabric remain pinned, but the Xaero integration supports the
compatible 1.21.11 release families shown below.

| Component | Version |
| --- | --- |
| Minecraft Java Edition | `1.21.11` |
| Java | `21` |
| Fabric Loader | `0.17.3` through the latest stable release |
| Fabric API | `0.141.3+1.21.11` or newer compatible release |
| Xaero's World Map | `>=1.41.0 <1.46.0` |
| Xaero's Minimap | `>=26.1.0 <26.5.0` |
| Mod Menu (optional) | `17.0.0` |

The addon is compatibility-checked against the published matching pairs
`1.41.0`/`26.1.0`, `1.41.2`/`26.1.4`, `1.42.0`/`26.2.0`,
`1.43.0`/`26.3.0`, `1.44.2`/`26.4.2`, and `1.45.0`/`26.4.2`. Use Xaero World Map and Minimap
versions released together. Versions outside the ranges above are not
supported by this build.

## Installation

1. Create or select a Fabric `1.21.11` client instance using Fabric Loader
   `0.17.3` or newer and Java 21.
2. Download Fabric API `0.141.3+1.21.11` or newer.
3. Install a supported matching pair of Xaero's World Map and Xaero's Minimap
   from their official pages:
   - [Xaero's World Map](https://modrinth.com/mod/xaeros-world-map)
   - [Xaero's Minimap](https://modrinth.com/mod/xaeros-minimap)
4. Put both Xaero JARs, Fabric API, and the Nodes Overlay JAR in the
   instance's `mods` directory.
5. Optionally install Mod Menu `17.0.0` for direct access to the settings
   screen.

Xaero's JARs are required external dependencies. They are not bundled with or
redistributed inside this addon.

## Usage

Join a configured server. The addon loads the last valid per-server
cache immediately, then refreshes data in the background. Open Xaero's World
Map normally to view the complete overlay; the nearby overlay also appears on
Xaero's Minimap.

Press `N` by default to open the settings screen. With Mod Menu installed, the
same screen is available from the mod list while joined to a world or server.
Opening it from the title screen shows a join-required notice because settings
and caches belong to a specific server profile.

The client-side `/xnma` command provides:

| Command | Action |
| --- | --- |
| `/xnma` | Open settings |
| `/xnma refresh` | Refresh map data now |
| `/xnma status` | Show activation, cache, and synchronization status |
| `/xnma clear-cache` | Remove this server's cached map data |
| `/xnma toggle` | Enable or disable this server profile |
| `/xnma waypoint toggle <territory>` | Add/remove a node or core waypoint |
| `/xnma waypoint remove <territory>` | Remove one node waypoint |
| `/xnma waypoint clear` | Remove all node waypoints |
| `/xnma test-war attack [defend-seconds]` | Emit an attack now and optionally defend it after the delay |
| `/xnma test-war attack-defended [delay]` | Emit the attack-defended result at the player |
| `/xnma test-war chunk-captured [delay]` | Emit a capture in the player's chunk (`chunk` is an immediate alias) |
| `/xnma test-war chunk-defended [delay]` | Emit a defended result in the player's chunk |
| `/xnma test-war chunk-liberated [delay]` | Emit a liberated result in the player's chunk |
| `/xnma test-war territory-captured [id] [delay]` | Emit a territory capture (`territory` is an immediate alias) |
| `/xnma test-war territory-liberated [id] [delay]` | Emit a territory liberation |
| `/xnma test-war suite [id] [step-seconds]` | Run all lifecycle messages in sequence (default step: 2 seconds) |
| `/xnma test-war clear` | Clear live/test events and cancel delayed test messages |
| `/xnma diagnose` | Explain the current chunk's overlay data |

These commands are handled on the client and are not sent to the server.

### Territory selection

Right-click a territory or node on Xaero's World Map to open its information panel.
The panel groups available information into:

- General: territory ID, node, size, core block and chunk coordinates, and
  neighboring territory IDs.
- Ownership: original town and nation, colors, occupier, and ownership state.
- Production: income, ores, crops, animals, costs, priority, and modifiers.
- War status: active attacker, event age, recent captures, and attack location.

Panel actions include creating or removing a waypoint, focusing the core,
copying coordinates, hiding the territory, and tracking its attacks.

Detailed labels and core markers are zoom-gated by default to avoid clutter.

### Port updates

Port markers use the center of the containing chunk at normal zoom. Above the
configured exact-position threshold, the marker moves to the exact block
coordinates. Running `/port info` on the server updates or adds the displayed
port after the complete multi-line response is received. These observations
are stored in `ports-live.json` and override the downloaded baseline without
being removed by an ordinary map-cache refresh.

## Live war chat support

The chat listener removes Minecraft formatting and accepts minor spacing or
punctuation differences around these messages. Both `[War]` and `[Warzone]`
prefixes are accepted:

```text
[War] <player> is attacking <town> at (x, y, z)
[War] <player> is liberating <town> at (x, y, z)
[Warzone] <player> is capturing warzone <town> at (x, y, z)
[War] Attack at (x, y, z) defended by <player>
[War] Attack at (x, y, z) defeated by <player>
[Colonization] AI defenders broke the flag at (x, y, z)
[War] <player> captured chunk (cx, cz) from <town>!
[War] <player> defended chunk (cx, cz) against <occupier>!
[War] <player> liberated chunk (cx, cz) from <occupier>!
[War] <player> captured territory (id=ID) from <town>!
[War] <player> liberated territory (id=ID) from <occupier>!
```

The addon also reads the opening ownership rows from the server's multi-line
territory information response:

```text
Territory (id = ID):
- Town: <owner>
- Occupier: <occupier>
```

That in-game response becomes the live territory-wide occupation baseline even
when it differs from `towns.json`. A later exact chunk capture, defense, or
liberation can still override the corresponding chunk.

An attack message is converted from block to chunk coordinates, matched through
the precomputed territory index, and shown immediately with a temporary marker
and waypoint. A chunk capture replaces the territory's attack marker, while a
territory capture replaces all earlier attack and chunk-capture markers for
that territory. Only an active attack renders a live-war icon or automatic
waypoint. Captures use diagonal occupation stripes, while defenses and
liberations update or remove provisional state without adding flag or shield
icons. Every ownership-changing result schedules a background map refresh, but
the newer in-game result remains authoritative whenever the downloaded data
disagrees. Live occupation state resets on disconnect.

A capture message does not require the client to have seen the original attack.
War messages arriving during login or a server transfer are held briefly until
the map profile is active, then replayed in order. This allows a player joining
late to see a captured chunk immediately from the capture line alone.

War debug commands display a real local `[War]` chat line and feed that text
through the same client listener and parser used for server messages. Delay
arguments are measured in seconds; for example, `test-war attack 10` displays
an attack immediately and its defended result 10 seconds later.

Chat events are provisional: the next successful data synchronization remains
authoritative. A server message cannot always identify the attacker's town or
nation. When that relationship cannot be resolved, the addon retains a neutral
marker instead of guessing. If coordinates cannot be matched to a territory,
the coordinate event is still retained for its waypoint and diagnostic status.

## Configuration

Settings are local and separated by normalized server address:

```text
config/
└── nodesoverlay/
    └── servers/
        └── <sanitized-host>-<hash>/
            ├── settings.json
            └── cache/
                ├── towns.json
                ├── world.json
                └── http-metadata.json
```

The settings screen controls:

- World-map and minimap overlays.
- Fill, border, and occupation-stripe appearance.
- Territory IDs, town names, nation names, and node icons.
- Port visibility, labels, and the exact-position zoom threshold.
- A dark map preset with stronger outlines and sparse labels.
- Core-marker mode and zoom threshold.
- War icons and world-space attack waypoints.
- Waypoint range and historical outcome expiration. Active attacks, captures,
  and liberation overrides do not expire on this timer; they remain until a
  newer matching in-game outcome or disconnect.
- Friendly, allied, hostile, and neutral marker behavior.
- Data endpoint URLs, a per-server enable switch, refresh interval, and forced
  reconciliation interval.

It also includes **Refresh Map Data** and **Clear Local Cache** buttons.
The important defaults are:

| Setting | Default |
| --- | --- |
| Lightweight data check | 5 minutes |
| Forced full reconciliation | 20 minutes |
| Failed-request retry | 90 seconds |
| Outcome/capture lifetime | 5 minutes |
| World-waypoint maximum distance | 8,000 blocks |
| Distant world-waypoint scale | Two-thirds size beyond 20 blocks |
| Core-marker mode | Zoomed in |
| Core-marker zoom threshold | `0.55` |

The addon enables itself automatically for the default map server domain
`crusalis.net` and its subdomains.
Other direct IPs, alternate domains, and development servers get their own
profile and can be enabled there without sharing settings or cached data.

### Cache behavior

- `towns.json`, `world.json`, and `ports.json` are fetched after joining a matching server
  when no usable cache exists.
- Conditional requests use saved `ETag` and `Last-Modified` values so unchanged
  responses do not need to be downloaded again.
- Network access and JSON parsing run away from Minecraft's render thread.
- New responses are validated before replacing the last-known-good cache.
- A failed, unavailable, or malformed response leaves the previous valid data
  active and retries later with a delay.
- Downloaded map data provides the baseline, while newer live chat outcomes
  override conflicting occupation data for the rest of the client session.
- Multi-line territory command responses update the owning town and occupier
  immediately and invalidate Xaero's cached overlay colors.
- Chunk outcomes finish only an attack in the same chunk; simultaneous flag
  attacks elsewhere in the same territory remain active.
- Clearing the cache only affects the currently selected server profile.

The default endpoints expect a normal browser-like request context. Keep the
default endpoint and request settings unless a map-data mirror or development
server requires an override.

## Xaero integration and compatibility

Xaero's maps do not expose Fabric entrypoints for registering these overlay
layers or extending the World Map's territory interaction. The addon therefore
uses six narrowly scoped client Mixins with `remap = false`:

- `WorldMapHighlighterRegistryMixin` injects at `HEAD` in
  `xaero.map.highlight.HighlighterRegistry#end()` and registers
  one cached highlighter containing the territory base, borders, and occupation
  stripes.
- `MinimapHighlighterRegistryMixin` injects at `HEAD` in
  `xaero.common.minimap.highlight.HighlighterRegistry#end()`. It reuses Xaero's
  World Map bridge when present and installs one fallback highlighter otherwise.
- `WorldMapHighlighterBridgeMixin` keeps the World Map and minimap toggles
  independent when Xaero wraps World Map highlighters for minimap rendering.
- `WorldMapElementRenderHandlerMixin` appends the core, node, label, and live
  war-marker renderer to Xaero's World Map element handler.
- `MinimapOverMapRendererHandlerMixin` appends the matching minimap marker
  renderer and exposes Xaero's already-computed rotation, zoom, and clipping
  values to it.
- `GuiMapInteractionMixin` handles right-clicks over indexed map chunks,
  opens the territory information panel, and provides the panel's focus-core
  navigation bridge.

Each highlighter-registry injection has a unique once-only guard. Fill, borders,
and diagonal occupation stripes are pre-composited and cached per chunk so Xaero
only performs one 256-pixel blend pass. The minimap registry also detects
Xaero's World Map adapter to avoid rendering the same overlay twice. Each
element renderer is added once to its newly constructed Xaero handler.
The world-map implementations extend `xaero.map.highlight.AbstractHighlighter`;
the minimap implementations use the parallel
`xaero.common.minimap.highlight.AbstractHighlighter` class. They request coverage
outside discovered terrain and render only in `World.OVERWORLD`.

The highlighters use O(1) snapshot chunk lookups, preindexed populated regions,
and Xaero's BBGGRRAA highlight color encoding. Same-nation or same-town internal
borders are weaker than outside borders, and occupation bands are anchored to
global block coordinates so their diagonal pattern remains continuous between
chunks. The pixel highlighter also paints the configured one-to-four-block
inward border thickness without obscuring the map terrain.

The element renderers query only visible indexed territories, suppress
colliding detail labels, hide cores and labels below the configured zoom, and
keep icons screen-upright on the rotating minimap. Hovering an interactive
World Map marker shows the core block and chunk coordinates. Temporary
world-space markers use the Xaero third-party waypoint API with their own
origin, so they can be removed without touching a user's saved waypoints.
The core hatch renderer has a fixed per-marker draw budget, so extreme Xaero
zoom levels cannot turn a scaled 16-by-16 core chunk into thousands of draw
calls per frame.

Overlay revisions invalidate Xaero's cached results through
`MapWorld#clearAllCachedHighlightHashes` for the world map and the minimap
dimension highlight handler's `requestRefresh` method. All current Mixins are
declared in `src/main/resources/nodesoverlay.mixins.json`; that file is the
source of truth for injected classes. Changes to any of these Xaero internals
can break the hooks, which is why both Xaero dependencies are exact runtime
pins.

**XaeroPlus is unsupported.** This project does not depend on it and does not
promise compatibility with it. Xaero's official project pages warn against
using XaeroPlus alongside the map mods.

## Performance model

Remote JSON is parsed into an immutable snapshot off-thread. The snapshot
includes a chunk-to-territory lookup, spatial query index, merged outer borders,
and lower-detail geometry. Xaero's cached region highlighters use O(1) chunk
lookups and its normal low-zoom tile reduction, while marker hooks query only
visible indexed territories instead of scanning every territory every frame.
Snapshot-derived region sets are reused across live war updates and ordinary
setting changes.

## Development

The build uses:

| Tooling | Version |
| --- | --- |
| Yarn mappings | `1.21.11+build.6` |
| Fabric Loom | `1.17.17` |
| Gradle wrapper | `9.5.0` |

Build on Windows:

```powershell
.\gradlew.bat build --no-daemon
```

Build on Linux or macOS:

```sh
./gradlew build --no-daemon
```

The remapped mod and sources JARs are written to `build/libs/` as
`xearos-nodes-<version>.jar` and `xearos-nodes-<version>-sources.jar`. Xaero's
official Maven is used to expose the pinned map mods during development. The
build does not call Loom's `include(...)`, shade Xaero classes, or place Xaero
JARs inside the output.

When changing either Xaero pin, re-audit every integration hook and test both
the full world map and minimap before publishing a build.

## License and attribution

Nodes Overlay is copyright 2026 Fond__ and is released under the
[ARR (All Rights Reserved) license](LICENSE).

Xaero's World Map and Xaero's Minimap are separate works by Xaero96 and are
licensed **All Rights Reserved**. This project's ARR license does not apply to
them. Their JARs, code, assets, and other content are not copied, modified,
embedded, shaded, or redistributed by this project. Users must obtain the
required versions from the official
[World Map](https://modrinth.com/mod/xaeros-world-map) and
[Minimap](https://modrinth.com/mod/xaeros-minimap) pages and follow their terms.

Map data is fetched from the configured endpoint at runtime and is not included in
the project distribution. This project is not affiliated with or endorsed by
Xaero's Maps or the map-data provider.
