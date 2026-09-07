# Nodes Overlay for Xaero's Maps

A client-side Fabric addon by **Fond__** that adds territory, node,
port, and war information directly to Xaero's World Map and Minimap.

## Territory Map

- Displays the exact chunk shape of every mapped territory.
- Uses the town or nation's official map color.
- Distinguishes owned, annexed, claimed, captured, occupied, and unowned land.
- Shows captured land with occupier-colored diagonal stripes over the original
  owner's color.
- Uses stronger outside borders and lighter borders between territories owned
  by the same town or nation.
- Makes core-territory outlines 1.5× more visible than normal borders.
- Switches to a clean political overview at low zoom: broad nation-colored
  land, outside borders, ports, war markers, and sparse nation or
  independent-town labels remain while core and node details wait for the
  detailed zoom level.
- Keeps overview names visible until detailed territory labels take over,
  without a blank label range between zoom levels.
- Supports hiding individual territories from the overlay.

## Core Territories and Nodes

- Shows core chunks when zoomed in so players can plan the best route to them.
- Supports always-visible, zoomed-in, attacked-only, and disabled core modes.
- Displays node icons and production information from the map data.
- Can show territory IDs, town names, nation names, and node labels.
- Provides removable node and core waypoints.
- Includes commands to toggle, remove, or clear addon-created waypoints.

## Ports

- Loads known ports from the public `ports.json` data.
- Displays port markers on the world map and minimap.
- Learns updated port information from in-game `/port info` responses.
- Saves newer coordinates, owner, group, ally, neutral, enemy, and access data.
- Shows the port's chunk position normally and its exact block position when
  sufficiently zoomed in.

## Live War Updates

- Reads supported war messages directly from chat.
- Reads multi-line in-game territory information responses, including the
  territory ID, owning town, and current occupier.
- Detects attacks plus defended, defeated, captured, and liberated outcomes
  for chunks and territories.
- Advances live war markers with the battle: a chunk capture replaces the
  territory's attack marker, and a territory capture replaces its earlier
  attack and chunk-capture markers.
- Treats an attack defense/defeat as complete cleanup: the attack icon and
  waypoint disappear without a replacement defense icon.
- Renders live-war icons and automatic war waypoints only while an attack is
  active. Captures use diagonal occupation stripes; all resolved outcomes are
  icon-free.
- Keeps an active attack and its waypoint until a matching server outcome
  finishes it; the cleanup timer never removes an attack in progress.
- Applies standalone chunk and territory capture messages without requiring the
  client to have seen the original attack.
- Buffers bounded, deduplicated war messages received during login or server
  transfer and replays them after access is approved, while discarding them for
  blacklisted clients.
- Matches chunk outcomes to their exact attack chunk, allowing simultaneous
  flag attacks inside one large territory.
- Updates the local overlay immediately without waiting for another complete
  map download.
- Displays a sword for every active attack, using a diamond sword for attacks
  against your own nation or an ally and an iron sword for hostile or
  unresolved attacks, with optional relationship-aware badge coloring.
- Uses 2×-sized live-war icons on both the world map and minimap.
- Creates temporary world-space attack waypoints.
- Allows war waypoints to be removed for individual territories.
- Includes delayed test commands for every supported message and a complete
  timed lifecycle suite. Tests emit visible local chat and use the real parser.

Supported messages include:

```text
[War] <player> is attacking <town> at (x, y, z)
[War] Attack at (x, y, z) defended by <player>
[War] Attack at (x, y, z) defeated by <player>
[War] <player> captured chunk (cx, cz) from <town>!
[War] <player> defended chunk (cx, cz) against <occupier>!
[War] <player> liberated chunk (cx, cz) from <occupier>!
[War] <player> captured territory (id=ID) from <town>!
[War] <player> liberated territory (id=ID) from <occupier>!
```

## Waypoint Clutter Reduction

- World waypoints remain at their normal size within 20 blocks.
- Every Xaero world waypoint farther than 20 blocks is reduced to two-thirds
  size.
- The icon, name, and distance label are all scaled together.

## Map Interaction

- Right-click a territory on Xaero's World Map to open its information panel.
- View ownership, nation, occupier, production, neighbors, core coordinates,
  node information, and current war activity.
- Focus the map on a territory's core.
- Copy coordinates directly from the territory panel.
- Track attacked territories and manage their waypoints.

## Data and Live Updating

- Uses public `towns.json`, `world.json`, and `ports.json` data.
- Downloads and parses updates away from Minecraft's render thread.
- Uses conditional requests so unchanged map files are not repeatedly
  downloaded.
- Keeps a validated last-known-good cache if the website is unavailable.
- Separates settings and cached data for each Minecraft server.
- Periodically refreshes map data without allowing it to overwrite newer
  chat-derived occupation outcomes.
- Treats in-game chunk and territory captures, defenses, and liberations as the
  session-authoritative state whenever they differ from `towns.json`.
- Treats an in-game territory information response as the newest
  territory-wide occupation baseline and refreshes both Xaero overlays
  immediately.
- Layers exact chunk outcomes over territory-wide outcomes, so a liberated
  chunk can remain clear inside a captured territory and a newly captured chunk
  can remain striped inside a liberated territory.
- Keeps live occupation overrides active until a newer matching in-game outcome
  or disconnect instead of dropping them on a timer or map refresh.
- Treats `towns.json` captured-territory entries as coarse occupation data.
  Once exact chunk messages are available, only those captured chunks are
  striped instead of incorrectly hatching the entire non-annexed node.

## UUID Access Blacklist

- Supports local authenticated Minecraft UUID blacklist entries.
- Does not use `towns.json`, account names, towns, or nations for access.
- Resolves access as soon as the local session UUID is available.
- Disables overlays, chat updates, waypoints, commands, and settings after a
  match.
- Starts with an empty blacklist; administrators add UUIDs per server profile.
- Stores additional entries in each server profile's `settings.json`.
- Acts as client-side deterrence and is not tamper-proof against a modified
  mod JAR.

## Performance

- Uses indexed territory and region lookups instead of scanning the entire map
  every frame.
- Caches combined territory fills, borders, and occupation stripes by chunk.
- Limits expensive core-hatching work at extreme zoom levels.
- Uses screen-space indexing to prevent overlapping labels efficiently.
- Avoids duplicate minimap overlay registration.
- Restricts marker processing to visible map regions.

## Customization

- Independent world-map and minimap overlay toggles.
- Configurable fill, border, stripe, and marker appearance.
- Configurable territory, town, nation, node, port, core, and war visibility.
- Configurable core zoom threshold and display mode.
- Configurable outcome/capture lifetime and world-waypoint range.
- Friendly, allied, hostile, and neutral marker behavior.
- Includes a darker map preset.
- Accessible through the in-game settings screen, Mod Menu, or `/xnma`.

## Compatibility

- Minecraft Java Edition `1.21.11`
- Java `21`
- Fabric Loader `0.17.3` through the latest stable release
- Fabric API `0.141.3+1.21.11` or newer compatible release
- Xaero's World Map `1.41.x` through `1.44.x`
- Xaero's Minimap `26.1.x` through `26.4.x`
- Mod Menu support is optional

The addon is completely client-side and does not need to be installed on the
Minecraft server.
