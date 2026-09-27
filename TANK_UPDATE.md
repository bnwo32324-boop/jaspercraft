# Mobile Tanks Update (JasprTanks 1.0.0)

Players on phones and tablets drive a miniature tank, an advantage that makes up for touch controls.

## What players get

- **Automatic:** the touch controls announce themselves once the server advertises tanks, and the player spawns in a tank. **Tank** hops out or back in; the choice is remembered.
- **Driving:** move in any direction with the DRIVE stick (visible knob). Pushing all the way forward sprints.
  - Speed matches an average horse: 9.7 blocks/s, 12.6 when sprinting. It raises walk speed, not the speed attribute, so the view never widens.
  - The tank climbs 1-block steps without jumping.
  - Tank plating: +6 armor and full knockback resistance.
- **Camera:** drivers start in third person. **View** switches to first person; F5 also works. A centered reticle marks the aim in third person, where vanilla draws no crosshair.
- **Cannon:** infinite TNT shells, fired by **FIRE** (the swap-hands key; F on desktop). The FIRE button shows the reload.
  - Reload is 1.5 s. Shells fly at 50 blocks/s to whatever the crosshair is on, up to 96 blocks, with a forgiving hit margin, and curve toward the creature they were fired at.
  - Each shell is a power-6 blast (TNT is 4) credited to the driver.
- **Riders:** up to 4 other players hop on by right-clicking (touch: Use) a driver and sit on the track guards. Sneak hops off. Riders come along through the driver's teleports.
- **Inventory:** unchanged. The player walks inside the tank model, so the normal inventory, armor, crafting, food and XP bars all stay.

## Safety

- **Blast rules:** shells never break blocks, and never destroy item frames, paintings, minecarts, boats, dropped items or decorative armor stands (`cannon.break-blocks: false`).
- **Friendly fire:** drivers, their riders and their tamed pets are immune to the driver's own shells. Other players are hurt only in worlds with PvP on.
- **Commands:** `/tank [on|off|status]` and `/tank mobile` are on TestServerControl's public allowlist. They affect only the caller's own tank; every other command stays gated.
- **Desktop browsers:** a browser that reports a desktop user agent is refused (operators excepted). Through the Jaspr.chat relay no user agent arrives, so there the touch controls' own claim decides.
- **Logs:** `TANKS_READY`, `TANK_ENTER`, `TANK_EXIT`, `TANK_RIDE`, `TANK_CLAIM_REFUSED` and `TANK_BUILD_FAILED` record player names and reasons only. User agents are classified, never logged. The browser exposes `JasprTankDiagnostics.status()` (state, counts, speed).

## How it works

- **Tank body:** two invisible marker armor stands wear the hull and turret models, on iron-axe bands 1 and 2; the selector keeps every other axe vanilla.
  - The server keeps them as independent entities, so plugin teleports keep working.
  - Clients are told the stands ride the driver, so the model follows the player with no lag.
- **Client stage** (`scripts/build-tank-client.cjs`, four fenced `JASPR_TANK_V1` hooks in `site/classes.js`):
  - The hull follows the driver's body yaw and the turret the head yaw.
  - The driver's own client steps 1 block.
  - Riders are placed on their seats.
  - The riding-yaw clamp is skipped for tank parts.
- **Server advertisement:** a hidden scoreboard objective (`jtk`, sent as packets once per connection) tells the browser that the server has tanks and the reload time.

## Build and test

- `powershell -File scripts/build-tanks-plugin.ps1`, `node scripts/build-tank-assets.cjs`, `node scripts/build-tank-client.cjs` (candidates under `candidate/`).
- `node --test tests/tanks.test.cjs`: models and selector, client hooks, touch wiring, plugin math and config clamps, `/tank` command policy.
- Browser fixture:
  - `node scripts/tank-preview.cjs` starts a loopback Paper server with only EaglerXServer and JasprTanks, world saving off and below-normal priority.
  - `node scripts/tank-cdp-probe.cjs <steps.json> <out> <url>` drives a headless phone at 10% resolution, render distance 2 and 15 fps. `TANK_PROBE_DESKTOP=1` drives a desktop rider instead.
- Verified in the fixture:
  - auto-enrol and claim;
  - third-person start and the View switch;
  - FIRE with a zombie killed and kill credit;
  - adjacent stone and glass intact;
  - no self-damage from a point-blank shot;
  - 9.7 / 12.6 blocks/s;
  - a 2-step climb;
  - a desktop rider boarding, being carried through a 100-block teleport, and ejected safely when the driver left.
