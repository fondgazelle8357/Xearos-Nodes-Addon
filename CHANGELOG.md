# Changelog

All notable changes to Nodes Overlay are documented here.

The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and version numbers follow [Semantic Versioning](https://semver.org/).

## [1.1.1] - 2026-09-07

### Fixed

- Attack/defence marker updates no longer invalidate territory highlight textures.
  Capture and liberation updates are batched per client tick and refresh only
  affected regions, retaining cached pixels elsewhere.
- The minimap no longer builds world-map nation/town overview labels. World-map
  overview labels are reused until their snapshot or relevant settings change.
- War event views and active-attack lists are cached on mutation instead of
  copying and sorting the whole history every rendered frame. Expiry scans run
  at most once per second; reconciliation uses indexed outcome lookups.
- Unchanged HTTP response bodies no longer republish snapshots. Town/port-only
  updates reuse unchanged world geometry and spatial indexes. War-triggered
  background checks are limited to one start per 15 seconds; live chat updates
  and explicit refresh commands remain immediate.

## [1.1] - 2026-07-28

### Added

- Added a `Port owner in labels` setting so port markers can display only the
  port name instead of `Port | Owner`.
- Fixed already-loaded Xaero regions sometimes retaining an empty territory
  highlight texture after map data or settings changed.

- Added a separate `Overview town names` option. Each town label is anchored
  on that town's home/core territory, while nation labels now use the explicit
  capital town from `towns.json` and anchor on the capital's core territory.
- Zoomed-in port markers and labels now show the owner's relationship to the
  local player: green for the same nation, blue for allies, and red for
  enemies. Ports without resolved owner data retain their existing gold color.

### Changed

- Expanded Fabric Loader compatibility from Loader `0.17.3` through the latest
  stable `0.19.3` release, and lowered the Fabric API minimum to
  `0.141.3+1.21.11` so current 1.21.11 profiles do not reject the mod solely
  because they are one compatible API patch behind the development version.
- Made the in-game `/territory` `Town:` row authoritative for the node's owner,
  ownership status, owner color, town/nation labels, and tooltips. The separate
  `Occupier:` row and exact per-chunk war outcomes remain layered above it.
- Renamed versioned build artifacts to `xearos-nodes-<version>.jar`.
- Port relationship colors now follow the current controller of the containing
  node instead of the owner cached by `/port info`. Live war occupation,
  downloaded capture state, and normal/annexed map ownership are resolved in
  that order.
- Friendly and allied attack flags now use Minecraft's diamond-sword sprite;
  hostile and unresolved attacks retain the iron-sword sprite.
- Standardized every active attack on the sword icon. Neutral or unresolved
  relationships now change only the badge color instead of replacing the
  attack with a misleading warning icon.
- Limited map war icons and automatic war waypoints to active attacks only.
  Captures now use occupation stripes, while every resolved outcome is
  icon-free.
- Active attacks now expire automatically after more than 15 minutes, removing
  stale map icons and automatic waypoints when an outcome message was missed.
- Attack allegiance now follows the player who placed the attack rather than
  the occupied target. Same-nation and allied attackers use the friendly green
  badge and diamond sword, including their automatic Xaero waypoint; hostile
  attackers remain red.
- Added a per-server `War icon size` slider from `0.50x` to `2.00x` for both
  Xaero's world map and minimap. Existing profiles retain the current icon size
  as the `1.00x` default, with matching collision and label-spacing bounds.
- Friendly or allied chunk-capture messages inside an already friendly or
  allied-occupied node now resolve the exact attack without replacing the
  node's occupier or creating a conflicting chunk override. Hostile changes
  continue to update the chunk normally.
- Removed replacement shield icons for defended/defeated attacks; those
  messages now clear the attack marker and waypoint completely.
- Added the complete war chat lifecycle: attack defended/defeated, chunk
  defended, chunk liberated, and territory liberated messages now replace
  stale markers and provisional capture coloring correctly.
- Reworked every war debug command to emit visible client-side server-style
  chat through the real parser, with optional delays and a timed full lifecycle
  suite.
- Turned low zoom into a cleaner political overview by withholding core and
  node detail until the detailed zoom level, reducing overview label size, and
  keeping overview labels visible until detailed labels take over.
- Separated overview-label collision handling from war markers so nation names
  do not disappear merely because a nearby live-war icon is present.
- Replaced superseded war markers immediately: a chunk capture clears attacks
  for that territory, and a territory capture clears its earlier attack and
  chunk-capture indicators.
- Expanded Xaero compatibility on Minecraft 1.21.11 to World Map
  `>=1.41.0 <1.45.0` and Minimap `>=26.1.0 <26.5.0`.
- Reduced every world waypoint icon, name, and distance label to two-thirds
  size beyond 20 blocks while preserving normal size nearby.
- Doubled live-war icon size on both Xaero map surfaces and expanded their
  label-collision bounds to match.
- Added a low-zoom political overview with cached, collision-aware nation or
  independent-town labels anchored to each owner's main territory.

### Fixed

- Prevented Xaero's World Map from crashing during proxy shard transfers by
  invalidating loaded overlay regions through Xaero's normal branch update
  path instead of force-refreshing regions while a dimension may be unloading.

- Recognized the server's town-wide `has been conquered` and `has been
  plundered` announcements as terminal war outcomes. All remaining attack
  flags and automatic attack waypoints for that town now disappear, followed
  by a near-term ownership-data refresh.
- Kept an existing node occupation intact when an enemy captures one of its
  chunks. The hostile capture now overrides only that exact chunk instead of
  resetting every other chunk to unoccupied.
- Made `/territory` refresh only the territory-wide occupation baseline.
  Existing exact chunk captures and liberations remain layered above it even
  when their chat messages arrived before the command response.
- Fixed the war-icon size editor by reading its live value during rendering and
  scaling the badge, sword, fallback geometry, and collision bounds with
  explicit pixel dimensions instead of relying on a cached matrix transform.
- Fixed visible `LF` control-character glyphs between Xaero territory and port
  tooltip lines.
- Kept `/xnma`, Mod Menu configuration, and the settings key available while
  a server profile is disabled. Disabled profiles no longer deadlock on an
  access check whose data sync is intentionally stopped, and can be enabled
  again without editing JSON.
- Reconciled Xaero's complete addon-owned attack waypoint collection every
  tick. Restored waypoints from a previous world or session can no longer
  remain after their matching live attack and map flag are gone.
- Replaced the low-detail rectangle sword glyph with Minecraft's real
  iron-sword sprite on both Xaero map surfaces and expanded the world-map
  bounds to fit the full 24-pixel attack badge.
- Capped the sword itself at a fixed 16-pixel size and rotated it so its blade
  points upward without growing as the map zoom changes.
- Made chunk and territory liberation chat events override stale occupation
  data immediately. A liberated chunk now returns to its owner color while
  neighboring chunks remain striped until their own state changes.
- Renamed the client command root to `/xnma` (Xaero's Node Map
  Addon), including every existing subcommand.
- Made active attacks lifecycle-driven instead of timer-driven. Attack icons
  and waypoints now remain until a matching defended, captured, or liberated
  server message completes them.
- Stopped an unrelated chunk capture from clearing every flag attack in the
  same territory. Chunk outcomes now complete only the exact attacked chunk;
  full territory outcomes still clear the whole territory.
- Made in-game occupation outcomes authoritative over conflicting downloaded
  map data for the rest of the client session. Map refreshes now enrich live
  events without deleting captures, defenses, or liberation overrides.
- Added parsing for the server's multi-line territory information response.
  `Town` and `Occupier` rows now update the territory-wide Xaero occupation
  overlay immediately, including cases such as territory `2129` being occupied
  by `Samnium`.
- Limited the territory-information update notification to meaningful
  ownership or occupation changes. Repeating `/territory` with unchanged values
  is now silent.
- Fixed standalone capture messages being lost when they arrived during the
  towns-based access check immediately after login or server transfer. Pending
  war messages are now replayed after approval, so a capture changes the map
  even if the client never saw the original attack.
- Layered exact chunk outcomes over territory-wide outcomes instead of
  accidentally replacing a territory event when its core chunk coordinates
  match. This preserves cleared chunks inside captured territories and newly
  captured chunks inside liberated territories.
- Prevented coarse `towns.json` captured-territory entries from hatching an
  entire non-annexed node after exact captured-chunk chat messages are known.
  Exact chunk capture state now survives data refreshes, and defended chunks
  suppress stale occupation stripes just like liberated chunks.
- Removed duplicate minimap highlighter registration when Xaero already bridges
  the World Map highlighter into its minimap renderer.
- Kept the World Map and minimap overlay toggles independent through Xaero's
  bridged highlighter path.
- Combined territory fill, borders, and occupation stripes into one cached
  chunk-color pass.
- Indexed minimap markers by core position so disconnected territory footprints
  no longer add thousands of invisible markers to every frame.
- Bounded core-chunk hatch rendering at extreme zoom instead of issuing work
  proportional to the scaled chunk's pixel area.
- Replaced quadratic label collision scans with a screen-space grid.
- Increased the bounded chunk-color cache to prevent overview-region churn and
  fixed dark outline coloring on every territory edge.
- Corrected swapped X/Z Xaero region keys that caused most territory
  highlighter regions to be treated as empty while their core markers still
  rendered.
- Treat captured holders that also own the territory as owners; when no
  separate owner exists, the captured holder becomes the effective owner.
- Made town-core territory outlines 1.5 times as opaque as normal territory
  borders, capped at full opacity.

### Added

- Downloaded `ports.json` baseline data and chunk-center/exact-block port
  markers with visibility, label and zoom settings.
- Multi-line `/port info` parsing with persistent per-server coordinate,
  owner, group and access overrides.
- Removable node/core waypoints plus individual and clear-all client commands.
- Client-only war attack, chunk capture, territory capture, suite and clear
  test commands.
- Current-area diagnostics and a dark-outline map preset.

## [0.1.0] - 2026-07-26

### Added

- Initial client-side Fabric project for Minecraft `1.21.11`.
- Exact runtime compatibility pins for Xaero's World Map `1.41.2` and Xaero's
  Minimap `26.1.4`.
- Asynchronous `towns.json` and `world.json` loading with conditional
  HTTP requests, validation, per-server last-known-good caches, and periodic
  reconciliation.
- Parsed immutable territory, town, nation, resident, node, ownership, and
  production models.
- Precomputed chunk and spatial indexes plus zoom-level territory geometry.
- World-map and minimap rendering for territory fill, borders, occupation
  hatching, node markers, core chunks, and live war markers.
- World-map territory selection and detailed information panel.
- Live parsing for attack, chunk-capture, and territory-capture war messages.
- Temporary attack waypoints and provisional in-memory updates between full
  synchronizations.
- Per-server settings screen, default `N` key binding, Mod Menu integration,
  and client-side `/xnma` commands.
- Manual refresh and per-server cache-clearing controls.
- ARR licensing for the addon with explicit external Xaero dependency and
  attribution boundaries.

### Compatibility

- Java `21`.
- Fabric Loader `0.19.3`.
- Fabric API `0.141.4+1.21.11`.
- Yarn mappings `1.21.11+build.6`.
- Fabric Loom `1.17.17`.
- Optional Mod Menu `17.0.0`.
- XaeroPlus is unsupported.
