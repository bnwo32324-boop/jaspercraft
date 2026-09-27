# Mobile Vehicles Update (JasprTanks 1.1.0)

Players on phones and tablets get an advantage that makes up for touch controls: **double health**, and a vehicle they can swap at any time, the **Tank** or the **Orbital Sentinel**.

## What mobile players get

- **Automatic start:** the touch controls announce themselves once the server advertises tanks.
  - The player sees a "Mobile player detected" notice and starts in their last vehicle (Tank by default).
  - **Mode** swaps vehicles at any time. **Tank** hops out or back in. Both choices are remembered.
- **Edit Profile toggle (client 20260927-vehicle1):** a **Mobile: Tank / Mobile: Orbital Sentinel** button sits under
  Add Skin / Clear List, so the vehicle can be picked before joining (also on a first join).
  - The choice is kept in the browser (`localStorage` `jaspr.vehicle.v1`) and rides along with the claim
    (`/tank mobile sentinel`); the server switches a vehicle that already started on join.
  - In game, a switch made with **Mode** (or `/tank mode`) becomes the saved choice. Against an older plugin the client
    asks once with `/tank mode <choice>` about 1.5 s after the vehicle appears.
  - Diagnostics: `JasprTankDiagnostics.status().vehicle` gives `{choice, pending, requests, button}`.
- **Double health:** +20 maximum health while playing from a phone or tablet (`mobile.health-bonus`).
- **Camera:** both vehicles start in third person. **View** switches to first person, and a centered reticle marks the aim.
- **Inventory, stats and HUD are unchanged:** the player walks or flies inside the model, so they are never mounted on anything.

### Tank

- **Driving:** the DRIVE stick moves in any direction; pushing it fully forward sprints.
  - Speed: 9.7 blocks/s, 12.6 sprinting (average/fast horse). This uses walk speed, so the view never widens.
  - Climbs 1-block steps without jumping.
  - Plating: +6 armor and full knockback resistance.
- **Cannon:** **FIRE** (the swap-hands key; F on desktop) shoots an infinite supply of TNT shells, reloading in 1.5 s.
  - Shells fly at 50 blocks/s to whatever the crosshair is on, up to 96 blocks, curving toward the creature they were fired at.
  - Each is a power-6 blast credited to the driver.
- **Riders:** up to 4 other players hop on by right-clicking (touch: Use) the driver. They sit on the track guards; sneak hops off.

### Orbital Sentinel

- **Flight:** a flying drone (pod, four rotors, red-eyed sensor pod). Move with the stick; **Up** and **Down** replace Jump and Sneak. It takes no fall damage.
- **Drone strike:** **STRIKE** sends 3 drones from the sentinel onto the crosshair point, each a power-4 blast.
  - The drones spread around the point, or all chase the creature aimed at.
  - Reload is 2.5 s.
- **Tap to grab:** tap an item on screen and a tractor beam pulls it in.
  - **Pickup: Auto** instead pulls items within 8 blocks.
  - Items still go through normal pickup rules, and never the player's own drops.
- **Tap to follow:** tap a player to lock on. The sentinel follows them 3 blocks above and 2 behind, and treats them as friendly.
  - Tap them again, or use `/tank unlock`, to stop.
  - While locked, tapping players never hurts them.
- The Orbital Sentinel carries no riders; switching to it sets riders down.

## First join (client 20260927-join1)

- **Cause:** a new browser used to auto-join while still unpacking the 15 MB asset pack, and the handshake timed out ("Handshake timed out", then Back showed the character screen).
- **New players** (no saved JasperCraft data) now start on **Edit Profile** ("create a character"), and **Done** joins.
- **Returning players** still join straight away. If a join fails, **Back** retries it once.
- The page marks an account (`.jasprJoined` setting) once it reaches the world.

## Safety

- **Blast rules:** shells never break blocks, item frames, paintings, minecarts, boats, dropped items or decorative armor stands (`cannon.break-blocks: false`).
- **Friendly fire:** drivers, their riders, pets and followed friends are immune to their shells. Other players are hurt only where PvP is on.
- **Commands:** `/tank [on|off|mode tank|mode sentinel|pickup auto|pickup tap|unlock|status]` and `/tank mobile` are public; they only change the caller's own vehicle.
- **Desktop browsers:** a browser that reports a desktop user agent is refused (operators excepted). Through the Jaspr.chat relay no user agent arrives, so there the touch controls' claim decides.
- **Logs:** `TANKS_READY`, `TANK_ENTER`, `TANK_EXIT`, `TANK_MODE`, `TANK_FOLLOW`, `TANK_RIDE` and `TANK_CLAIM_REFUSED` record names and states only. The browser exposes `JasprTankDiagnostics.status()`.

## How it works

- **Model:** two invisible marker armor stands wear the models on iron-axe bands 1-4:
  - 1 tank hull, 2 tank turret, 3 sentinel drone, 4 sentinel pod;
  - every other axe stays vanilla.
- **Positioning:** only clients are told the stands ride the player, so teleports keep working and the model follows with no lag.
  - The client stage (`JASPR_TANK_V1`) aligns the body to the player's body yaw and the top part to the head yaw, places riders, and steps 1 block.
- **Advertisement:** a hidden scoreboard objective (`jtk`, sent as packets once per connection) carries the reload, the mode and the pickup setting to the browser.

## Build and test

- **Build:** `scripts/build-tanks-plugin.ps1`, `node scripts/build-tank-assets.cjs`, `node scripts/build-tank-client.cjs` (which refreshes the installed block) and `node scripts/build-firstrun-client.cjs`.
- **Tests:** `node --test tests/tanks.test.cjs tests/first-join.test.cjs`.
- **Lightweight browser fixture:**
  - `node scripts/tank-preview.cjs`: EaglerXServer + JasprTanks only, world saving off, below-normal priority.
  - `node scripts/tank-cdp-probe.cjs`: headless phone at 10% resolution, render distance 2, 15 fps. `TANK_PROBE_DESKTOP=1` drives a desktop player.
- **Verified in the fixture:**
  - notice and double hearts;
  - mode switch both ways;
  - flight (y 64 → 77);
  - tap grab and auto-pickup;
  - drone strike kill;
  - follow lock tracking a friend over two teleports;
  - first-run Edit Profile → Done → joined;
  - returning auto-join;
  - Back retry after a refused first join.
