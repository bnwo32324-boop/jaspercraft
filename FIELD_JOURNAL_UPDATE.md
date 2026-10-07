# The Field Journal: a panel in the wide inventory (2026-10-07)

Owner, 2026-10-07: the wide inventory leaves an empty gap beside the crafting grid. "Do you have any ideas of what we could put
here ...?" Picked: **1. event forecast, 3. character summary, 4. active item effects.**

A small dark panel fills that gap in the survival inventory, with three tabs (tap a tab, or tap anywhere else on the panel to flip
to the next one):

| Tab | Shows |
| --- | --- |
| **Soon** | Blood Moon: `in 2 nights` / `tomorrow` / `tonight` / `NOW!` · Invasion: `none marked` / `from day 15` / `any night` / `UNDER WAY` · Disaster: `quiet` / `brewing` / `<kind> now!` · the day and time of day in the footer (`Day 12 Dusk`) |
| **You** | Experience level and bar · `14 ranks` · `9/45 stats` · `Raise 3 now` (stats you can afford right now) or `next: 6 lv` or `all maxed` · an **Open Stats** button (sends `/stats`, the same as the K key) |
| **Perks** | The effects your items keep on you (the "silent" ones the inventory deliberately lists no box for, so they never cover the Easier Crafting search bar): name, level, and a countdown when the effect is a timed one (an item-kept effect shows no time) |

Nothing else changes: the window, the slots and the gear column are as before. On phones the panel exists in the Expand view of the
inventory (it lives in the widened window); Contract (the vanilla window) has no gap and so no panel. A chest or any other window
never shows it.

## What the server tells the client, and what it does not

New plugin **JasprJournal 1.0.0** (`server/custom-plugins/JasprJournal`). The browser says `hello 1` on the plugin channel
`jaspr:journal` when it joins; from then on the server sends that player's panel as one small JSON string (under 600 bytes) whenever
it changes, and at least every ten seconds. The client uses nothing else and sends nothing back but the hello and the `/stats`
command a button press stands for. Only information a player could already get:

* **Blood Moon**: the day and time, and the siege's own rule (`SiegeRules.bloodMoon` with the configured interval), asked every time.
* **Invasion**: the player's own mark, the line `/invasion` already prints ("it comes on a night from day N").
* **Disaster**: only `quiet`, `brewing` (due within a Minecraft day, no more precise) or the kind while it runs. The exact time stays
  an administrator's secret (`/<disaster> status`), and the journal has no way to see it.
* **You**: the player's own level and stat sheet summary.
* **Perks**: the player's own effects that are ambient and particle-less.

Bounds: at most 12 effects, texts reduced to plain ASCII and capped, at most 6 incoming messages a second per player (any message
that is not exactly `hello 1` is refused and counted), a 64-byte limit on what is read from the client, and a client that has sent
no valid hello gets nothing.

Logs (counts only, no names, addresses or tokens): `JOURNAL_READY`, `JOURNAL_CLIENT_HELLO clients=N`, `JOURNAL_METRICS` every five
minutes (clients, sends, hellos, refused, failures per source), `JOURNAL_SOURCE_UNAVAILABLE source=...` once per failing source,
`JOURNAL_SEND_FAILED`. Client side, diagnostics events `jaspercraft.journal.state` and `.error` (bounded, same-origin).

### How the other plugins are read

JasprJournal needs none of them at compile time and keeps working, with only that row missing, when one is off or changed:

* **Blood Moon**: reflection into JasprApocalypse `SiegeRules.bloodMoon(long, int)` and its config `siege.blood-moon-every-nights`.
* **Stats**: `RpgApi.summary(UUID, int)` (new, JasprRPG 1.3.2): `{ranks, stats raised, stats, stats affordable now, cheapest next
  price}`. Read-only.
* **Disasters**: `DisasterPlugin.journalState()` (new, JasprDisasters 1.4.1): `"active:<kind>"`, `"brewing"` or `"quiet"`.
* **Invasions**: reads the plugin's own private state with reflection (`active`, `settings.daysAfterSleep`, the progress store's
  `all()` and each record's `sleptAt`), never creating a record. JasprInvasions was not touched because another session has
  uncommitted work in it; when that is committed an official accessor like the other two would be tidier.

## The client stage

`client-mods/journal-teavm.js` + `scripts/build-journal-client.cjs` (fence `JASPR_JOURNAL`, marker `JJ`). Four small hooks, each
placed **right after** the text of the other stages in the same function and never inside it, so the wide-inventory, text-fit,
silent-effects, nbt-skin and armor-bar builders can still rebuild a client that carries this stage (and the stages commute: tested):

* `C6T` GuiContainer.drawScreen: the start of state 2 draws the panel (new state 291 continues);
* `E8R` handleJoinGame: state 10 says hello (new state 292 continues);
* `Cyr` handleCustomPayload: the channel `jaspr:journal` (new state 2995) reads the string like the gear channel does;
* `Gmh` GuiContainer.mouseClicked: a click on the panel is used up right after the super call.

Layout (GUI pixels, window-relative): the inset runs from x 176 to 258 and y 4 to 77, between the crafting result and the frame
and clear of the first inventory row (which starts at y 83). Text is white or tooltip-coloured on a dark screen, measured with the
real font: a row that does not fit side by side wraps (label, then value), a page holds six rows, and more than six rows turn
into pages (`1/2 tap`): **nothing is ever cut off.** The tab strip uses full labels, falls back to `Soon / Me / Fx` and then to single
letters when a font is wider. Nothing is hover-only; a tap on the Open Stats button has a target that runs to the bottom of the
panel, and a tap on the body (not just the 11 pixel tabs) flips tabs, which gives a finger a large target.

## Tests

| What | Where |
| --- | --- |
| Pure rules (phases, Blood Moon countdown checked against a tick-by-tick simulation, invasion and disaster states, effect names, payload) and source contracts (the rule copy is the siege's real text, read-only doors, coarse disaster hint, wire format) | `tests/journal-server.test.cjs` |
| Client stage (builds on the live client, reversible, hooks placed outside other stages' text, state numbers new, every other stage still rebuilds on top of it), module on mocks with the real font's widths (fit, wrap, paging, tabs, taps, stale data, hostile payloads), resumable entry points | `tests/journal-client.test.cjs` |
| **Real Paper** with the real Apocalypse, RPG, Disasters, Invasions and Journal jars (440 assertions): Blood Moon against the real `SiegeRules` at a sweep of times and intervals, invasion mark lifecycle, disaster hint, stat summary against an independent computation, silent effects, hello bounds, a missing plugin hides only its row | `node tests/journal-smoke.cjs --paper` (see its header for the environment) |
| **Real browser** against a real server (wide inventory jar + real Journal/RPG/Disasters/Invasions + a stand-in JasprApocalypse carrying the real `SiegeRules`): hello and data over the channel, the panel's geometry and pixels, every word inside the frame, real mouse clicks on tabs and the button (the server opens the stat sheet), a silent effect with its countdown, no panel in other windows; `--mobile` repeats it as a phone in landscape then portrait with touch | `node tests/journal-browser.cjs [--mobile]` |

## Rebuild order and ownership

Client: LIVE `classes.js` -> `node scripts/build-journal-client.cjs` (outermost, but nothing else needs it unpatched; `--unpatch`
removes it exactly). `node scripts/assemble-journal-client.cjs --game <live checkout> --out candidate/deploy --key <cache key>`
builds `classes.js` and `client.html` from the live files. Plugins: `node scripts/build-journal-plugin.cjs` (JasprJournal, one
processor) and `node scripts/patch-plugin-jars.cjs --game <live checkout> --out candidate/jars JasprRPG JasprDisasters` (the patch
tool now also knows JasprDisasters and compiles on one processor).

New panel rows: add the data to `JournalRules.Snapshot` (and its test), the parsing in `parse()`, and the rows in `rowsFor()`; keep
a tab at six rows or let it page.
