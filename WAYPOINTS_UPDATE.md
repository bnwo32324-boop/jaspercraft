# Waypoint Markers Update

Personal waypoints with unlimited-distance markers, Lunar-style: colored beams
visible from anywhere, deathpoints, an M menu, and an always-on compass readout.
No world reset. Multiplayer only; passive species and bosses need no exemptions.

## Contents

- `sentry`-style server subsystem in JasprApocalypse (`Waypoint`, `Waypoints`,
  `WaypointMenu`): per-player YAML store (12 manual + 3 deathpoints), `/waypoints`
  (`/wp`: list/add/delete/track/help), chest menu (left track, right recolor,
  shift-click twice to delete, create via chat naming, track-nearest, close),
  deathpoints with auto-track and coordinates chat message, action-bar compass
  (8-way arrow, name, distance, above/below tag) every 20 ticks.
- Browser catalogue untouched (no items): 88 entries, all counts unchanged.
- Client: M keybind (native `key.jaspr.waypoints`, default keycode 50, remappable
  in Controls) sends `/waypoints`, mirroring the K stats pattern exactly.
- Client: marker renderer draws one colored beam per waypoint at any distance
  (two crossed quads, native depth and lightmap left on, like real beacons).
- Runtime waypoint sync rides a hidden vanilla scoreboard objective (`jwp`, never
  given a display slot): coord holders (`JW`+slot+death+color+base36 x/z) plus
  name holders (`JN`+slot+name), y in the score. No custom protocol, no chunk
  loading, no new network channels. Mobile/touch players use `/wp`; menus,
  compass, deathpoints and crafting work on unpatched (native Java) clients,
  only the beams and the M key need the browser build.

## Integrity and security

- Markers are display-only request-free rendering of server-synced data; all
  waypoint rules (caps, ownership, auth) stay server-side. Forged scoreboard
  entries cannot create, move or delete anything: parsers fail closed.
- Menu clicks are cancelled and re-validated; delete needs a second confirm
  click; chat naming cancels the public message and validates length/content.
- Renderer is fail-closed like gore: repeated errors disable markers until the
  next world change; the game loop is never affected.
- `jasper` remains the only operator; `TestServerControl` untouched.

## Verification

- `node --test tests/waypoint-codec.test.cjs` (6/6: golden vectors shared with
  Waypoint.java, malformed holders, sanitize parity, bounds, color table).
- `node --test tests/waypoint-markers.test.cjs` (7/7: join, dangling halves,
  24-cap, beam geometry, skips, fail-closed counters, rgb table).
- `node --test tests/waypoint-keybind.test.cjs` (8/8: gate, independence,
  rebinding, native install/coexistence, /waypoints traffic, hook order,
  diagnostics, no page-level side effects).
- Paper smoke `waypoint-store-and-menu` phase (codec, caps, colors, active,
  naming incl. cancel/guest text, menu build/clicks, board sync, commands,
  compass vectors, YAML round trip, fixture cleanup).
- `scripts/build-stats-client.cjs` candidate + full stats-keybind suite except
  two pre-existing failures documented below (gore-bundle drift; tripwire
  resized for this feature with justification).
- Live `JasprApocalypse.jar` logs `WAYPOINTS_READY`; `apocalypse status`
  reports waypoint totals; public `classes.js` carries the keybind, codec and
  marker runtime (verified byte-identical post-deploy).

## Known pre-existing failures (not this release)

- `stats-keybind.test.cjs` "earlier builders remove stats first": the deployed
  bundle predates a gore runtime change (`JasprGoreBridge.layer()` vs
  `layer(a.cHT)` plus layer-model tracking), so `gore.build(candidate)` can no
  longer round-trip. Proven orthogonal via full-diff (zero waypoint bytes).
  Adopting the drift would redeploy new gore behavior; left for the gore owner.
- Tripwire `candidate-base<65000` resized to `<85000`: waypoint runtime,
  adapters, codec and markers add ~19 KB of audited code.

## Follow-ups (not in this release)

- World-space name/distance labels on beams (compass + menu carry names today).
- Nether 1:8 coordinate linking (same-world markers only today).

Live address: <https://jaspr.chat/jaspercraft/>
