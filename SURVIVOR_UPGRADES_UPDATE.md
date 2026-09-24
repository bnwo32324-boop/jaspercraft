# Survivor Upgrades and the expanded arsenal

Live verified: **2026-09-08 08:18 UTC** at `https://jaspr.chat/jaspercraft/`. The server returned to always-on service after a brief restart. Installation preserved all 233 protected terrain/player/plugin-data files byte-for-byte; all 72 player NBT, stats and advancement files were still unchanged after startup. No world reset.

Pricing refresh live verified: **2026-09-11 03:19 UTC**. The Paper plugin was rebuilt and restarted in maintenance mode; no world, player, inventory, XP, or life-checkpoint data was reset or copied.

## Playing

Press **K** anywhere while alive in the game, or enter **/stats** in chat (also available on mobile). Change the key under **Options → Controls → Gameplay → Upgrade Stats**. It uses the existing account-saved native settings, not a new browser-only preference. Typing, menus, death screens, lost focus and disconnected clients do not trigger the key.

Select a stat in the menu, then click its emerald purchase button. Purchases use current XP **points**, not levels or the historical total-experience counter. Every stat starts at rank zero, has ten ranks and costs `1 + r` points for its next rank, where `r` is its current rank. The first rank costs 1 XP; all ten ranks of one stat cost 55 XP.

| Stat | Bonus per rank |
| --- | --- |
| Vitality | +1 heart of maximum health; does not heal on purchase |
| Power | +4% direct melee, arrow and firearm damage |
| Agility | +2% of ordinary base movement speed |
| Fortitude | +0.5 armor |
| Haste | +2% of ordinary melee attack speed; does not shorten gun/reload cooldowns |
| Stability | +2.5 percentage points of knockback resistance |

Ranks persist across reconnects, clearing browser data, account sign-in and server restarts. **Dying resets all six ranks to zero, even with keepInventory.** Normal Minecraft statistics, inventory rules, gamemode, account identity and client preferences are not replaced. Bonuses and purchases apply only to authenticated Survival/Adventure players, in every dimension. Creative players can inspect the menu but cannot buy or use its bonuses.

The next-rank price is intentionally lightweight: `1 + r` spendable XP points, where `r` is the current rank of that stat. The first rank costs 1 XP, the second costs 2 XP, and each later rank adds exactly 1 XP. Taking one stat from rank 0 through rank 10 costs 55 XP; all six stats cost 330 XP. The server and menu use the same authoritative rule, so the displayed price and the charged price cannot diverge.

## Structure weapons

The catalogue now contains **35 guns and 24 melee weapons**, including 24 new guns and 16 new melee weapons. New firearms include Sepulcher, Turnstile, Tunnel Rat, Black Box, Watchtower, Bellringer, Witchlight, Null Point, Stormcoil and Dead Frequency. New melee includes Railworker's Armor Pick, Ward Cleaver, Ash Pilgrim's Lance, Ossuary Chain Flail and Final Verdict Execution Sword. See [the equipment catalogue](apocalypse-pack/EQUIPMENT.md) for all entries and mechanics.

- Tier 2+ armories guarantee a custom melee weapon. Tier 2 uses a restricted lighter pool.
- Tier 4/5 armories also guarantee a gun and at least 48 cartridges. Tier 5 can add a second distinct gun.
- Tier 4/5 guarded vaults contain two distinct guns, one melee weapon, armor and at least 64 cartridges.
- Tier 3 vaults contain two distinct melee weapons; medical, supply, relic and Fold caches have additional melee opportunities.
- Weapon picks are without replacement within a chest. Military/industrial, occult and anomalous ruins bias their respective weapon families without eliminating variety.
- Guns remain late-game tier 4/5 rewards and start with empty magazines. Existing recipes, identities, orientation and original weapon specifications stay unchanged.

The new loot applies when unopened generated caches are first claimed. **Already claimed/emptied chests do not refill. No terrain reset or loot-journal reset is part of this release.**

## Persistence and safety

Rank tags and the XP deduction are saved in the same native player NBT file and read back before a purchase is acknowledged. UUID-keyed, fsynced atomic life checkpoints prevent an older pre-death player save from restoring prior-life ranks. Ambiguous or failed saves lock upgrades and emit diagnostic events instead of silently granting bonuses. Death never changes XP/drop policy itself. Shutdown preserves boosted current health through Paper's final save; reconnect rebuilds namespaced modifiers without stacking them. A live plugin disable still removes its bonuses.

Only this system's deterministic attribute UUIDs are modified. Existing base attributes, equipment bonuses and potions are retained. Damage passes through the existing cancellable combat pipeline. Inventory holder/owner identity, click types, price, XP and authentication are rechecked on the server. There is no per-frame overlay, browser polling, or per-tick disk save. Menus and online profiles refresh every ten server ticks; input is handled by the native client input loop.

Diagnostics: `STATS_PURCHASE`, `STATS_DEATH_RESET`, `STATS_*_FAILED`, and `/apocalypse status` from the console or an authorized operator. Logs contain player UUIDs and bounded event data, never account credentials.

## Verification

- Pure JVM XP conservation, caps, prices and checkpoint validation: 939,689 checks.
- Isolated Paper, native player saves and two-JVM restart: 706 assertions, including save fault injection, auth/menu exploits, cancelled gun damage and stale pre-death NBT.
- Six additional JVM boots, with a real online CraftPlayer: 144 assertions cover normal shutdown, JVM shutdown hook, 36/40 HP retained after restart, exactly one owned modifier, and removal on manual plugin disable.
- Browser-native K opening, native Controls remapping, closing/reopening the client with the same key/rank, and actual death/respawn reset were exercised through the game UI; the current server-side pricing rule is covered by the updated isolated Paper probes.
- 4,000 role/tier loot rolls include every gun/melee ID, distinct selections, ammo and one-chest slot limits. The combined native structure fixture passed 98,446 assertions across 47 natural sites.
- 140 firearm and 96 melee held-orientation cases; 45 existing non-selector models and every original weapon specification unchanged.
- Existing worldgen, finite loot, Fold, gore and preservation suites passed; the staged SSO/settings/mobile/audio suite passed 14 tests.

Generated release artifacts: `candidate/stats-qa-report.json`, `candidate/stats-shutdown-report.json`, `candidate/stats-shutdown-hook-report.json`, `candidate/survivor-release-plan.json`, the selected release's `offline-proof.json`, and `candidate/survivor-release-report.json`. The live report is written only after exact public asset hashes, plugin readiness, 24/7 gateway settings and protected player files pass verification. Physical power-loss behavior and actual iOS hardware were not tested during this update.
