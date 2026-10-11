# First-person melee animation v5 (combat rework, workstream B) -- 2026-10-11

The owner: "Completely redo the animations for the sword swings, the axe swings, and other weapons ... Every type of
melee weapon should have a different type of animation that looks fluid and epic and have animatic effects ... Dual
wielding should have its own animation for different combinations of weapons, it should be specific to every
combination ... Try to make the combat for melee weapons and gunplay extremely satisfying."

Visual only: damage, reach, cooldowns and packets stay Better Combat's (workstream A). Third person stays Mo' Bends.

## What players see
- **18 weapon classes, 94 moves.** Each class has its own combo chain (3 or 4 strokes, each with anticipation, a fast
  committed stroke whose edge leads, follow-through and a visible recovery), a sprint attack and a jump (falling)
  attack. An axe hoists over the shoulder and pauses before it chops; a katana draws from the hip; a rapier lunges and
  ripostes; a hammer hangs at the top before it slams; a dagger stabs, slashes in reverse grip and flips in the fingers.
- **Combos flow.** A click during a stroke never cuts it off: one move waits and starts where the blade ended (a
  forehand ending on the left is answered by a backhand from the left). Clicking slowly from rest plays the class's
  strokes that start from the right; a pause of more than 26 ticks starts the combo over.
- **Faithful to the cooldown.** Clip lengths follow the held weapon's real cooldown (its attack speed, or the server's
  recovery for Apocalypse and Dungeon arsenal weapons): a slower weapon of a class swings slower and recovers later, and
  the weapon is back at rest about when it is recharged. A spammed (low-energy) strike swings a shorter arc around the
  same contact point, so it looks as weak as it hits.
- **Dual wielding** (both hands melee weapons, Better Combat's off-hand attacks on): the waiting hand answers every
  strike of the other hand with a support track tied to that strike's wind-up, contact and stroke end (it guards, cocks
  back, counter-turns, flows with it, tucks in, braces, readies its point or ducks under); when the second hand clicks
  during the first hand's wind-up or stroke it joins with the pair's joint move (twin swords cross in an X, a dagger
  stabs through the opening a sword cut made, a light weapon jabs just after a heavy blow lands, twin axes chop
  together). Every one of the 306 main x off pairs is defined below. The left hand plays the exact mirror image. When
  the hands take turns, each hand goes on with its own combo (its stroke still counts when a support track takes over
  its recovery): alternating twin swords cut diagonal, level, thrust on each side instead of repeating the opener.
- **Off the camera, always.** Moves are authored to stay clear of the eye; in addition no pose (strike, support track or
  trail sample) ever brings the weapon's grip, middle or tip nearer than 0.32 blocks of view depth: such a pose is
  pushed straight back, continuously (in all 306 pairs at four click rhythms this touches 0.03% of frames, counted as
  `eyeGuard` in the diagnostics).
- **Impacts** (when the swing aimed at an entity, or the server confirms a hit): a short hit-stop at contact (25 ms for a
  dagger to 85 ms for a hammer, +25 ms on a crit; the clip still ends on schedule), a camera kick in the direction of
  the blow (a dagger barely nudges the view, a hammer slam dips it 1.8 degrees), sparks at the struck side of the target
  (blade sparks, blunt dust, ender sparks for the claw, magic sparks for staves; crit sparks on crits), and a brighter
  blade trail. A shield block clangs with sparks only; a miss or a server refusal does nothing.
- **Blade trails:** a ribbon along the edge's path over the stroke only, class or set coloured (realm armouries carry
  their set's colour, Apocalypse weapons their accent), brightest along the edge, fading toward the hilt and the tail in
  under two ticks, wider on sweeps, frozen (not collapsed) during hit-stop.
- Nothing full-screen, no glow or outline on any entity, nothing full-bright (trails take the weapon's own light), no new
  sounds (Dynamic Surroundings already plays class swing sounds; Better Combat plays the hit sounds). The client particle
  setting decides the sparks; "minimal" particles (or the Video Settings particle switch off) also turn trails off. A
  browser that asks for reduced motion gets half-size camera kicks.

## Classes
Weapons are classified from the real stack (item id, damage, NBT), cached per stack. Trinket icons on unbreakable stone
swords/shovels, guns, gadgets, the sentry turret, bows and shields keep the vanilla first-person path. Fists keep the
vanilla arm.

| Class | Category | Nominal cooldown (ticks) | Hit-stop | Chain | Sprint / jump | Weapons |
| --- | --- | --- | --- | --- | --- | --- |
| sword | blade | 12.5 | 45 ms | diag, rise, flat, thrust | lunge / cleave | vanilla and realm swords; Apocalypse thermal_machete, cautery_sabre, ember_falchion, champions_gladius, high_roller_sabre, brinehook_cutlass, shock_baton; Nether infernal_blade, deathwing_talon, khopesh, fire_nation_dao, gladiator_gladius, amedian swords; Backrooms arc_baton, tidebreaker |
| katana | blade | 12.5 | 40 ms | draw, kesa, gyaku, tsuki (with chiburi) | dash / kabuto | Apocalypse mono_katana, voidsteel_katana, verdict_of_rime; Nether cursed_katana |
| rapier | blade | 10 | 35 ms | lunge, flick, riposte, flourish | fleche / plunge | Apocalypse glass_rapier, starmetal_rapier; Dungeon VELVET_RAPIER |
| greatsword | heavy | 20 | 70 ms | sweep, return, overhead, thrust | charge / fall | Apocalypse rebar_sword, execution_sword, skybreaker_blade; Nether wyrmbone_blade |
| axe | heavy | 20 | 60 ms | chop, hook, cleave, heave | rush / drop | vanilla and realm axes; Apocalypse breacher_axe, wardcleaver, bone_reaver, railpick; Dungeon BASILICA_CLEAVER; Nether amedian and cincinnasite axes, bone_reaver; Backrooms crowbar |
| greataxe | heavy | 30 | 80 ms | overhead, sweep, return, execute | charge / fall | Apocalypse magmaforged_greataxe; Nether pit_lord_cleaver, gatekeeper_cleaver |
| hammer | heavy | 24 | 85 ms | slam, swing, upper, pound | ram / meteor | Mutants Hulk Hammer (4004); Apocalypse gravity_maul, tollhammer, altar_mallet, censer_mace, obsidian_warhammer; Dungeon INQUISITOR_MAUL, SILENT_MALLET, trophy DEEPBREAKER; Nether magma_maul, colossus_maul, amedian hammers; Backrooms rebar, pipe_wrench, stop_sign |
| dagger | light | 8 | 25 ms | stab, slash (reverse grip), flick, twirl | lunge / plunge | Apocalypse trench_blade, gravespike, vesper_dagger, silkfang_dagger, hunters_kukri; Dungeon WAX_SCALPEL, HOSPICE_LANCET; Nether bloodfang_dagger, serpent_fang; Backrooms utility_knife, letter_opener, wire_cutters; Mutants Creeper Shard (4002) |
| spear | pole | 18 | 50 ms | thrust, jab, sweep (butt-strike), impale | charge / skewer | Apocalypse sentinel_spear, pilgrim_lance, tidecaller_spear |
| glaive | pole | 22 | 60 ms | arc, return, thrust, whirl | charge / fall | Apocalypse hollow_halberd, mourning_glaive; Dungeon AMBER_GLAIVE |
| scythe | pole | 22 | 55 ms | reap, return, high, harvest | sweep / fall | Apocalypse reaper_scythe, suture_sickle, nightfall_scythe; Nether soulreaper_scythe; Muse "Scythe-Hoe" relics |
| flail | heavy | 22 | 55 ms | whirl, lash, crack | lash / crack | Apocalypse ossuary_flail, wire_whip; Dungeon CLOISTER_FLAIL; Nether titan_chain |
| staff | pole | 15 | 45 ms | swing, rise, poke, twirl | poke / fall | Nether pharaoh_crook, rime_scepter, dread_staff, sovereign_flame, archon_wand; Dungeon trophy ABYSSAL_SCEPTRE and the arsenal's CARRION_CENSER, OPAL_PRISM, SUNLESS_BRAND, PALE_LANTERN |
| claw | light | 12.5 | 35 ms | rake, backrake, swipe, uppercut | lunge / drop | Mutants Endersoul Hand (4003) |
| odd | tool | 14 | 40 ms | club, backclub, jab | jab / bonk | Dungeon trophy MERCYS_LAST_KEY; Backrooms valve_key |
| pickaxe | tool | 16.7 | 45 ms | hack, back, swipe | swipe / hack | vanilla, realm, Nether cincinnasite pickaxes; Backrooms pallet_hook, jackhammer |
| shovel | tool | 20 | 45 ms | jab, bash, scoop | jab / slam | vanilla and realm shovels |
| hoe | tool | 12 | 40 ms | hook, back, chop | hook / chop | vanilla and realm hoes |

The 40 Apocalypse weapons are 3D cuboid models whose blade runs along model +Y. `scripts/melee-model-table.cjs`
reads each model from `apocalypse-pack` (display transform, grip, trail end points, cutting side) into
`client-mods/melee/melee-models.js`; at runtime the canonical poses are mapped onto the model's own frame (blended in
as the weapon leaves rest, so the rest look stays the model's). Claw and odd moves turn the resting item about view axes
(swipes), so they read right however the item is drawn.

## How a move is built
`client-mods/melee/melee-moves.js`. A move is authored for the right hand by what you see: at each stroke key the grip
sits at screen (x, y) (-1..1 across the 16:9 view of the hand pass's fixed 70 degree field of view) and `depth` blocks
from the eye, and the blade tip shows at screen (tx, ty) (`sc([[gx, gy, depth, tx, ty], ...])`). The blade's directions
are fitted with one plane, so the edge leads through the stroke, and the face is chosen so the cutting side (an axe's
bit) leads. Thrusts drive the grip along keys while the blade points at an aim point; swipes carry the resting item.
Times are ticks at the class's nominal cooldown: `w` wind-up, `h` loaded pause at the top (the weapon arrives a little
into the stroke and draws back: tension), `s` stroke (speed profile peaking near contact), `r` recovery (via keys back
to rest, optional `twirl`). `windTwirl` spins the weapon during the wind-up while it holds still with its flat to the
eye. Each move has its own camera kick direction (`kick`, here positive pitch dips the view), spark direction (`dir`)
and spark kind.

## Dual-wield pair matrix
`client-mods/melee/melee-dual.js`. Categories: blade (sword, katana, rapier), heavy (greatsword, axe, greataxe, hammer,
flail), light (dagger, claw), pole (spear, glaive, scythe, staff), tool (pickaxe, shovel, hoe, odd). Each cell takes its
support styles and joint move from a 5 x 5 category table (`STYLE`, `JOINT`: which role of the joining class plays, and
how long it holds cocked so it lands after the first hand), refined by 27 named signature pairs (bold). The claw (the
Endersoul Hand) is not an off-hand weapon (Better Combat attacks with the off hand only with tool and weapon items), so
it appears only as a main hand. Support poses: `STYLES` (three keys each: at the striker's wind-up end, contact and just
after its stroke), scaled by the striker's weight (heavier blows turn the body more) and the supporter's (heavier weapons
move less). Regenerate with `node scripts/melee-dual-matrix.cjs`; `--check MELEE_ANIMATION_V5.md` verifies this table.

<!-- MATRIX:BEGIN -->
| Main | Off | Choreography | Off hand while main strikes | Main hand while off strikes | Off joins with | Main joins with |
| --- | --- | --- | --- | --- | --- | --- |
| sword | sword | **twin swords: alternating X-cuts** | guards | guards | sword-diag (held 0.2 t) | sword-diag |
| sword | katana | **sword and katana: crossing cuts** | guards | guards | katana-kesa (held 0.2 t) | sword-diag |
| sword | rapier | sword and rapier: crossing cuts | guards | guards | rapier-flick | sword-diag |
| sword | greatsword | sword and greatsword: a cut, then the heavy blow | braces | counter-turns | great-sweep | sword-flat (held 1 t) |
| sword | axe | **sword and axe: cut then chop** | braces | guards | axe-chop (held 0.2 t) | sword-flat (held 1 t) |
| sword | greataxe | sword and greataxe: a cut, then the heavy blow | braces | counter-turns | greataxe-overhead | sword-flat (held 1 t) |
| sword | hammer | sword and hammer: a cut, then the heavy blow | braces | counter-turns | hammer-slam | sword-flat (held 1 t) |
| sword | dagger | **sword and dagger: cut-and-stab** | cocks back | guards | dagger-stab (held 0.5 t) | sword-flat |
| sword | spear | sword and spear: a cut, then the pole strikes | readies its point | guards | spear-jab (held 0.4 t) | sword-diag (held 0.6 t) |
| sword | glaive | sword and glaive: a cut, then the pole strikes | readies its point | guards | glaive-thrust (held 0.4 t) | sword-diag (held 0.6 t) |
| sword | scythe | sword and scythe: a cut, then the pole strikes | readies its point | guards | scythe-high (held 0.4 t) | sword-diag (held 0.6 t) |
| sword | flail | sword and flail: a cut, then the heavy blow | braces | counter-turns | flail-whirl | sword-flat (held 1 t) |
| sword | staff | sword and staff: a cut, then the pole strikes | readies its point | guards | staff-poke (held 0.4 t) | sword-diag (held 0.6 t) |
| sword | odd | sword and odd: cut and hack | guards | guards | odd-club | sword-diag |
| sword | pickaxe | sword and pickaxe: cut and hack | guards | guards | pick-swipe | sword-diag |
| sword | shovel | sword and shovel: cut and hack | guards | guards | shovel-bash | sword-diag |
| sword | hoe | sword and hoe: cut and hack | guards | guards | hoe-hook | sword-diag |
| katana | sword | **katana and sword: crossing cuts** | guards | guards | sword-diag (held 0.2 t) | katana-kesa |
| katana | katana | **twin katanas: crossing kesa cuts** | guards | guards | katana-kesa (held 0.2 t) | katana-kesa |
| katana | rapier | katana and rapier: crossing cuts | guards | guards | rapier-flick | katana-kesa |
| katana | greatsword | katana and greatsword: a cut, then the heavy blow | braces | counter-turns | great-sweep | katana-dash (held 1 t) |
| katana | axe | katana and axe: a cut, then the heavy blow | braces | counter-turns | axe-chop | katana-dash (held 1 t) |
| katana | greataxe | katana and greataxe: a cut, then the heavy blow | braces | counter-turns | greataxe-overhead | katana-dash (held 1 t) |
| katana | hammer | katana and hammer: a cut, then the heavy blow | braces | counter-turns | hammer-slam | katana-dash (held 1 t) |
| katana | dagger | **daisho: long cut, short stab** | cocks back | guards | dagger-stab (held 0.5 t) | katana-dash |
| katana | spear | katana and spear: a cut, then the pole strikes | readies its point | guards | spear-jab (held 0.4 t) | katana-kesa (held 0.6 t) |
| katana | glaive | katana and glaive: a cut, then the pole strikes | readies its point | guards | glaive-thrust (held 0.4 t) | katana-kesa (held 0.6 t) |
| katana | scythe | katana and scythe: a cut, then the pole strikes | readies its point | guards | scythe-high (held 0.4 t) | katana-kesa (held 0.6 t) |
| katana | flail | katana and flail: a cut, then the heavy blow | braces | counter-turns | flail-whirl | katana-dash (held 1 t) |
| katana | staff | katana and staff: a cut, then the pole strikes | readies its point | guards | staff-poke (held 0.4 t) | katana-kesa (held 0.6 t) |
| katana | odd | katana and odd: cut and hack | guards | guards | odd-club | katana-kesa |
| katana | pickaxe | katana and pickaxe: cut and hack | guards | guards | pick-swipe | katana-kesa |
| katana | shovel | katana and shovel: cut and hack | guards | guards | shovel-bash | katana-kesa |
| katana | hoe | katana and hoe: cut and hack | guards | guards | hoe-hook | katana-kesa |
| rapier | sword | rapier and sword: crossing cuts | guards | guards | sword-diag | rapier-flick |
| rapier | katana | rapier and katana: crossing cuts | guards | guards | katana-kesa | rapier-flick |
| rapier | rapier | **twin rapiers: alternating lunges** | cocks back | cocks back | rapier-lunge (held 0.8 t) | rapier-flick |
| rapier | greatsword | rapier and greatsword: a cut, then the heavy blow | braces | counter-turns | great-sweep | rapier-flick (held 1 t) |
| rapier | axe | rapier and axe: a cut, then the heavy blow | braces | counter-turns | axe-chop | rapier-flick (held 1 t) |
| rapier | greataxe | rapier and greataxe: a cut, then the heavy blow | braces | counter-turns | greataxe-overhead | rapier-flick (held 1 t) |
| rapier | hammer | rapier and hammer: a cut, then the heavy blow | braces | counter-turns | hammer-slam | rapier-flick (held 1 t) |
| rapier | dagger | **main-gauche: the dagger parries while the rapier thrusts** | guards | cocks back | dagger-slash (held 0.2 t) | rapier-flick |
| rapier | spear | rapier and spear: a cut, then the pole strikes | readies its point | guards | spear-jab (held 0.4 t) | rapier-flick (held 0.6 t) |
| rapier | glaive | rapier and glaive: a cut, then the pole strikes | readies its point | guards | glaive-thrust (held 0.4 t) | rapier-flick (held 0.6 t) |
| rapier | scythe | rapier and scythe: a cut, then the pole strikes | readies its point | guards | scythe-high (held 0.4 t) | rapier-flick (held 0.6 t) |
| rapier | flail | rapier and flail: a cut, then the heavy blow | braces | counter-turns | flail-whirl | rapier-flick (held 1 t) |
| rapier | staff | rapier and staff: a cut, then the pole strikes | readies its point | guards | staff-poke (held 0.4 t) | rapier-flick (held 0.6 t) |
| rapier | odd | rapier and odd: cut and hack | guards | guards | odd-club | rapier-flick |
| rapier | pickaxe | rapier and pickaxe: cut and hack | guards | guards | pick-swipe | rapier-flick |
| rapier | shovel | rapier and shovel: cut and hack | guards | guards | shovel-bash | rapier-flick |
| rapier | hoe | rapier and hoe: cut and hack | guards | guards | hoe-hook | rapier-flick |
| greatsword | sword | greatsword and sword: the heavy blow, then a cut | counter-turns | braces | sword-flat (held 1 t) | great-sweep |
| greatsword | katana | greatsword and katana: the heavy blow, then a cut | counter-turns | braces | katana-dash (held 1 t) | great-sweep |
| greatsword | rapier | greatsword and rapier: the heavy blow, then a cut | counter-turns | braces | rapier-flick (held 1 t) | great-sweep |
| greatsword | greatsword | **twin greatswords: crossing sweeps** | braces | braces | great-sweep (held 0.8 t) | great-overhead (held 0.4 t) |
| greatsword | axe | greatsword and axe: two heavy blows | braces | braces | axe-chop (held 0.4 t) | great-overhead (held 0.4 t) |
| greatsword | greataxe | greatsword and greataxe: two heavy blows | braces | braces | greataxe-overhead (held 0.4 t) | great-overhead (held 0.4 t) |
| greatsword | hammer | greatsword and hammer: two heavy blows | braces | braces | hammer-slam (held 0.4 t) | great-overhead (held 0.4 t) |
| greatsword | dagger | **greatsword and knife: sweep, then a quick jab** | tucks in | braces | dagger-stab (held 1.4 t) | great-sweep |
| greatsword | spear | greatsword and spear: the blow, then the pole strikes | ducks under | braces | spear-jab (held 1.2 t) | great-sweep (held 0.6 t) |
| greatsword | glaive | greatsword and glaive: the blow, then the pole strikes | ducks under | braces | glaive-thrust (held 1.2 t) | great-sweep (held 0.6 t) |
| greatsword | scythe | greatsword and scythe: the blow, then the pole strikes | ducks under | braces | scythe-high (held 1.2 t) | great-sweep (held 0.6 t) |
| greatsword | flail | greatsword and flail: two heavy blows | braces | braces | flail-crack (held 0.4 t) | great-overhead (held 0.4 t) |
| greatsword | staff | greatsword and staff: the blow, then the pole strikes | ducks under | braces | staff-poke (held 1.2 t) | great-sweep (held 0.6 t) |
| greatsword | odd | greatsword and odd: the blow, then a hack | counter-turns | braces | odd-club (held 0.8 t) | great-sweep (held 0.4 t) |
| greatsword | pickaxe | greatsword and pickaxe: the blow, then a hack | counter-turns | braces | pick-hack (held 0.8 t) | great-sweep (held 0.4 t) |
| greatsword | shovel | greatsword and shovel: the blow, then a hack | counter-turns | braces | shovel-jab (held 0.8 t) | great-sweep (held 0.4 t) |
| greatsword | hoe | greatsword and hoe: the blow, then a hack | counter-turns | braces | hoe-hook (held 0.8 t) | great-sweep (held 0.4 t) |
| axe | sword | **axe and sword: chop then cut** | guards | counter-turns | sword-flat (held 0.8 t) | axe-chop |
| axe | katana | axe and katana: the heavy blow, then a cut | counter-turns | braces | katana-dash (held 1 t) | axe-chop |
| axe | rapier | axe and rapier: the heavy blow, then a cut | counter-turns | braces | rapier-flick (held 1 t) | axe-chop |
| axe | greatsword | axe and greatsword: two heavy blows | braces | braces | great-overhead (held 0.4 t) | axe-chop (held 0.4 t) |
| axe | axe | **twin axes: alternating chops** | cocks back | cocks back | axe-chop (held 0.6 t) | axe-chop (held 0.4 t) |
| axe | greataxe | axe and greataxe: two heavy blows | braces | braces | greataxe-overhead (held 0.4 t) | axe-chop (held 0.4 t) |
| axe | hammer | axe and hammer: two heavy blows | braces | braces | hammer-slam (held 0.4 t) | axe-chop (held 0.4 t) |
| axe | dagger | **axe and knife: chop and stab** | tucks in | cocks back | dagger-stab (held 1 t) | axe-chop |
| axe | spear | axe and spear: the blow, then the pole strikes | ducks under | braces | spear-jab (held 1.2 t) | axe-chop (held 0.6 t) |
| axe | glaive | axe and glaive: the blow, then the pole strikes | ducks under | braces | glaive-thrust (held 1.2 t) | axe-chop (held 0.6 t) |
| axe | scythe | axe and scythe: the blow, then the pole strikes | ducks under | braces | scythe-high (held 1.2 t) | axe-chop (held 0.6 t) |
| axe | flail | axe and flail: two heavy blows | braces | braces | flail-crack (held 0.4 t) | axe-chop (held 0.4 t) |
| axe | staff | axe and staff: the blow, then the pole strikes | ducks under | braces | staff-poke (held 1.2 t) | axe-chop (held 0.6 t) |
| axe | odd | axe and odd: the blow, then a hack | counter-turns | braces | odd-club (held 0.8 t) | axe-chop (held 0.4 t) |
| axe | pickaxe | axe and pickaxe: the blow, then a hack | counter-turns | braces | pick-hack (held 0.8 t) | axe-chop (held 0.4 t) |
| axe | shovel | axe and shovel: the blow, then a hack | counter-turns | braces | shovel-jab (held 0.8 t) | axe-chop (held 0.4 t) |
| axe | hoe | axe and hoe: the blow, then a hack | counter-turns | braces | hoe-hook (held 0.8 t) | axe-chop (held 0.4 t) |
| greataxe | sword | greataxe and sword: the heavy blow, then a cut | counter-turns | braces | sword-flat (held 1 t) | greataxe-overhead |
| greataxe | katana | greataxe and katana: the heavy blow, then a cut | counter-turns | braces | katana-dash (held 1 t) | greataxe-overhead |
| greataxe | rapier | greataxe and rapier: the heavy blow, then a cut | counter-turns | braces | rapier-flick (held 1 t) | greataxe-overhead |
| greataxe | greatsword | greataxe and greatsword: two heavy blows | braces | braces | great-overhead (held 0.4 t) | greataxe-overhead (held 0.4 t) |
| greataxe | axe | greataxe and axe: two heavy blows | braces | braces | axe-chop (held 0.4 t) | greataxe-overhead (held 0.4 t) |
| greataxe | greataxe | **twin greataxes: heavy alternating blows** | braces | braces | greataxe-overhead (held 1 t) | greataxe-overhead (held 0.4 t) |
| greataxe | hammer | greataxe and hammer: two heavy blows | braces | braces | hammer-slam (held 0.4 t) | greataxe-overhead (held 0.4 t) |
| greataxe | dagger | **greataxe and knife: cleave, then a quick jab** | tucks in | braces | dagger-stab (held 1.8 t) | greataxe-overhead (off chain: dagger-stab, dagger-flick, dagger-stab, dagger-twirl) |
| greataxe | spear | greataxe and spear: the blow, then the pole strikes | ducks under | braces | spear-jab (held 1.2 t) | greataxe-overhead (held 0.6 t) |
| greataxe | glaive | greataxe and glaive: the blow, then the pole strikes | ducks under | braces | glaive-thrust (held 1.2 t) | greataxe-overhead (held 0.6 t) |
| greataxe | scythe | greataxe and scythe: the blow, then the pole strikes | ducks under | braces | scythe-high (held 1.2 t) | greataxe-overhead (held 0.6 t) |
| greataxe | flail | greataxe and flail: two heavy blows | braces | braces | flail-crack (held 0.4 t) | greataxe-overhead (held 0.4 t) |
| greataxe | staff | greataxe and staff: the blow, then the pole strikes | ducks under | braces | staff-poke (held 1.2 t) | greataxe-overhead (held 0.6 t) |
| greataxe | odd | greataxe and odd: the blow, then a hack | counter-turns | braces | odd-club (held 0.8 t) | greataxe-overhead (held 0.4 t) |
| greataxe | pickaxe | greataxe and pickaxe: the blow, then a hack | counter-turns | braces | pick-hack (held 0.8 t) | greataxe-overhead (held 0.4 t) |
| greataxe | shovel | greataxe and shovel: the blow, then a hack | counter-turns | braces | shovel-jab (held 0.8 t) | greataxe-overhead (held 0.4 t) |
| greataxe | hoe | greataxe and hoe: the blow, then a hack | counter-turns | braces | hoe-hook (held 0.8 t) | greataxe-overhead (held 0.4 t) |
| hammer | sword | hammer and sword: the heavy blow, then a cut | counter-turns | braces | sword-flat (held 1 t) | hammer-slam |
| hammer | katana | hammer and katana: the heavy blow, then a cut | counter-turns | braces | katana-dash (held 1 t) | hammer-slam |
| hammer | rapier | hammer and rapier: the heavy blow, then a cut | counter-turns | braces | rapier-flick (held 1 t) | hammer-slam |
| hammer | greatsword | hammer and greatsword: two heavy blows | braces | braces | great-overhead (held 0.4 t) | hammer-slam (held 0.4 t) |
| hammer | axe | hammer and axe: two heavy blows | braces | braces | axe-chop (held 0.4 t) | hammer-slam (held 0.4 t) |
| hammer | greataxe | hammer and greataxe: two heavy blows | braces | braces | greataxe-overhead (held 0.4 t) | hammer-slam (held 0.4 t) |
| hammer | hammer | **twin hammers: alternating slams** | braces | braces | hammer-slam (held 1 t) | hammer-slam (held 0.4 t) |
| hammer | dagger | **hammer and knife: slam, then a quick jab** | tucks in | braces | dagger-stab (held 1.6 t) | hammer-slam (off chain: dagger-stab, dagger-flick, dagger-stab, dagger-twirl) |
| hammer | spear | hammer and spear: the blow, then the pole strikes | ducks under | braces | spear-jab (held 1.2 t) | hammer-slam (held 0.6 t) |
| hammer | glaive | hammer and glaive: the blow, then the pole strikes | ducks under | braces | glaive-thrust (held 1.2 t) | hammer-slam (held 0.6 t) |
| hammer | scythe | hammer and scythe: the blow, then the pole strikes | ducks under | braces | scythe-high (held 1.2 t) | hammer-slam (held 0.6 t) |
| hammer | flail | hammer and flail: two heavy blows | braces | braces | flail-crack (held 0.4 t) | hammer-slam (held 0.4 t) |
| hammer | staff | hammer and staff: the blow, then the pole strikes | ducks under | braces | staff-poke (held 1.2 t) | hammer-slam (held 0.6 t) |
| hammer | odd | hammer and odd: the blow, then a hack | counter-turns | braces | odd-club (held 0.8 t) | hammer-slam (held 0.4 t) |
| hammer | pickaxe | hammer and pickaxe: the blow, then a hack | counter-turns | braces | pick-hack (held 0.8 t) | hammer-slam (held 0.4 t) |
| hammer | shovel | hammer and shovel: the blow, then a hack | counter-turns | braces | shovel-jab (held 0.8 t) | hammer-slam (held 0.4 t) |
| hammer | hoe | hammer and hoe: the blow, then a hack | counter-turns | braces | hoe-hook (held 0.8 t) | hammer-slam (held 0.4 t) |
| dagger | sword | **dagger and sword: stab then cut** | flows with it | cocks back | sword-flat | dagger-stab (held 0.6 t) |
| dagger | katana | dagger and katana: a jab, then a cut | flows with it | cocks back | katana-dash | dagger-stab (held 0.6 t) |
| dagger | rapier | dagger and rapier: a jab, then a cut | flows with it | cocks back | rapier-flick | dagger-stab (held 0.6 t) |
| dagger | greatsword | dagger and greatsword: a jab, then the heavy blow | braces | tucks in | great-sweep | dagger-stab (held 1.6 t) |
| dagger | axe | dagger and axe: a jab, then the heavy blow | braces | tucks in | axe-chop | dagger-stab (held 1.6 t) |
| dagger | greataxe | dagger and greataxe: a jab, then the heavy blow | braces | tucks in | greataxe-overhead | dagger-stab (held 1.6 t) |
| dagger | hammer | dagger and hammer: a jab, then the heavy blow | braces | tucks in | hammer-slam | dagger-stab (held 1.6 t) |
| dagger | dagger | **twin daggers: flurry** | counter-turns | counter-turns | dagger-slash | dagger-slash (off chain: dagger-stab, dagger-flick, dagger-slash, dagger-twirl) |
| dagger | spear | dagger and spear: a jab, then the pole strikes | readies its point | tucks in | spear-jab | dagger-stab (held 0.8 t) |
| dagger | glaive | dagger and glaive: a jab, then the pole strikes | readies its point | tucks in | glaive-thrust | dagger-stab (held 0.8 t) |
| dagger | scythe | dagger and scythe: a jab, then the pole strikes | readies its point | tucks in | scythe-high | dagger-stab (held 0.8 t) |
| dagger | flail | dagger and flail: a jab, then the heavy blow | braces | tucks in | flail-whirl | dagger-stab (held 1.6 t) |
| dagger | staff | dagger and staff: a jab, then the pole strikes | readies its point | tucks in | staff-poke | dagger-stab (held 0.8 t) |
| dagger | odd | dagger and odd: a jab, then a hack | flows with it | cocks back | odd-club | dagger-stab (held 0.6 t) |
| dagger | pickaxe | dagger and pickaxe: a jab, then a hack | flows with it | cocks back | pick-hack | dagger-stab (held 0.6 t) |
| dagger | shovel | dagger and shovel: a jab, then a hack | flows with it | cocks back | shovel-jab | dagger-stab (held 0.6 t) |
| dagger | hoe | dagger and hoe: a jab, then a hack | flows with it | cocks back | hoe-hook | dagger-stab (held 0.6 t) |
| spear | sword | spear and sword: the pole strikes, then a cut | guards | readies its point | sword-diag (held 0.6 t) | spear-jab (held 0.4 t) |
| spear | katana | spear and katana: the pole strikes, then a cut | guards | readies its point | katana-kesa (held 0.6 t) | spear-jab (held 0.4 t) |
| spear | rapier | spear and rapier: the pole strikes, then a cut | guards | readies its point | rapier-flick (held 0.6 t) | spear-jab (held 0.4 t) |
| spear | greatsword | spear and greatsword: the pole strikes, then the heavy blow | braces | ducks under | great-sweep (held 0.6 t) | spear-jab (held 1.2 t) |
| spear | axe | spear and axe: the pole strikes, then the heavy blow | braces | ducks under | axe-chop (held 0.6 t) | spear-jab (held 1.2 t) |
| spear | greataxe | spear and greataxe: the pole strikes, then the heavy blow | braces | ducks under | greataxe-overhead (held 0.6 t) | spear-jab (held 1.2 t) |
| spear | hammer | spear and hammer: the pole strikes, then the heavy blow | braces | ducks under | hammer-slam (held 0.6 t) | spear-jab (held 1.2 t) |
| spear | dagger | **spear and knife: thrust and close in** | tucks in | readies its point | dagger-stab (held 0.8 t) | spear-jab |
| spear | spear | **twin spears: piston thrusts** | cocks back | cocks back | spear-jab (held 0.8 t) | spear-jab (held 0.8 t) |
| spear | glaive | spear and glaive: both poles strike | readies its point | readies its point | glaive-thrust (held 0.8 t) | spear-jab (held 0.8 t) |
| spear | scythe | spear and scythe: both poles strike | readies its point | readies its point | scythe-high (held 0.8 t) | spear-jab (held 0.8 t) |
| spear | flail | spear and flail: the pole strikes, then the heavy blow | braces | ducks under | flail-whirl (held 0.6 t) | spear-jab (held 1.2 t) |
| spear | staff | spear and staff: both poles strike | readies its point | readies its point | staff-poke (held 0.8 t) | spear-jab (held 0.8 t) |
| spear | odd | spear and odd: the pole strikes, then a hack | guards | readies its point | odd-club (held 0.6 t) | spear-jab (held 0.4 t) |
| spear | pickaxe | spear and pickaxe: the pole strikes, then a hack | guards | readies its point | pick-hack (held 0.6 t) | spear-jab (held 0.4 t) |
| spear | shovel | spear and shovel: the pole strikes, then a hack | guards | readies its point | shovel-jab (held 0.6 t) | spear-jab (held 0.4 t) |
| spear | hoe | spear and hoe: the pole strikes, then a hack | guards | readies its point | hoe-hook (held 0.6 t) | spear-jab (held 0.4 t) |
| glaive | sword | glaive and sword: the pole strikes, then a cut | guards | readies its point | sword-diag (held 0.6 t) | glaive-thrust (held 0.4 t) |
| glaive | katana | glaive and katana: the pole strikes, then a cut | guards | readies its point | katana-kesa (held 0.6 t) | glaive-thrust (held 0.4 t) |
| glaive | rapier | glaive and rapier: the pole strikes, then a cut | guards | readies its point | rapier-flick (held 0.6 t) | glaive-thrust (held 0.4 t) |
| glaive | greatsword | glaive and greatsword: the pole strikes, then the heavy blow | braces | ducks under | great-sweep (held 0.6 t) | glaive-thrust (held 1.2 t) |
| glaive | axe | glaive and axe: the pole strikes, then the heavy blow | braces | ducks under | axe-chop (held 0.6 t) | glaive-thrust (held 1.2 t) |
| glaive | greataxe | glaive and greataxe: the pole strikes, then the heavy blow | braces | ducks under | greataxe-overhead (held 0.6 t) | glaive-thrust (held 1.2 t) |
| glaive | hammer | glaive and hammer: the pole strikes, then the heavy blow | braces | ducks under | hammer-slam (held 0.6 t) | glaive-thrust (held 1.2 t) |
| glaive | dagger | glaive and dagger: the pole strikes, then a jab | tucks in | readies its point | dagger-stab (held 0.8 t) | glaive-thrust |
| glaive | spear | glaive and spear: both poles strike | readies its point | readies its point | spear-jab (held 0.8 t) | glaive-thrust (held 0.8 t) |
| glaive | glaive | glaive and glaive: both poles strike | readies its point | readies its point | glaive-thrust (held 0.8 t) | glaive-thrust (held 0.8 t) |
| glaive | scythe | glaive and scythe: both poles strike | readies its point | readies its point | scythe-high (held 0.8 t) | glaive-thrust (held 0.8 t) |
| glaive | flail | glaive and flail: the pole strikes, then the heavy blow | braces | ducks under | flail-whirl (held 0.6 t) | glaive-thrust (held 1.2 t) |
| glaive | staff | glaive and staff: both poles strike | readies its point | readies its point | staff-poke (held 0.8 t) | glaive-thrust (held 0.8 t) |
| glaive | odd | glaive and odd: the pole strikes, then a hack | guards | readies its point | odd-club (held 0.6 t) | glaive-thrust (held 0.4 t) |
| glaive | pickaxe | glaive and pickaxe: the pole strikes, then a hack | guards | readies its point | pick-hack (held 0.6 t) | glaive-thrust (held 0.4 t) |
| glaive | shovel | glaive and shovel: the pole strikes, then a hack | guards | readies its point | shovel-jab (held 0.6 t) | glaive-thrust (held 0.4 t) |
| glaive | hoe | glaive and hoe: the pole strikes, then a hack | guards | readies its point | hoe-hook (held 0.6 t) | glaive-thrust (held 0.4 t) |
| scythe | sword | scythe and sword: the pole strikes, then a cut | guards | readies its point | sword-diag (held 0.6 t) | scythe-high (held 0.4 t) |
| scythe | katana | scythe and katana: the pole strikes, then a cut | guards | readies its point | katana-kesa (held 0.6 t) | scythe-high (held 0.4 t) |
| scythe | rapier | scythe and rapier: the pole strikes, then a cut | guards | readies its point | rapier-flick (held 0.6 t) | scythe-high (held 0.4 t) |
| scythe | greatsword | scythe and greatsword: the pole strikes, then the heavy blow | braces | ducks under | great-sweep (held 0.6 t) | scythe-high (held 1.2 t) |
| scythe | axe | scythe and axe: the pole strikes, then the heavy blow | braces | ducks under | axe-chop (held 0.6 t) | scythe-high (held 1.2 t) |
| scythe | greataxe | scythe and greataxe: the pole strikes, then the heavy blow | braces | ducks under | greataxe-overhead (held 0.6 t) | scythe-high (held 1.2 t) |
| scythe | hammer | scythe and hammer: the pole strikes, then the heavy blow | braces | ducks under | hammer-slam (held 0.6 t) | scythe-high (held 1.2 t) |
| scythe | dagger | scythe and dagger: the pole strikes, then a jab | tucks in | readies its point | dagger-stab (held 0.8 t) | scythe-high |
| scythe | spear | scythe and spear: both poles strike | readies its point | readies its point | spear-jab (held 0.8 t) | scythe-high (held 0.8 t) |
| scythe | glaive | scythe and glaive: both poles strike | readies its point | readies its point | glaive-thrust (held 0.8 t) | scythe-high (held 0.8 t) |
| scythe | scythe | **twin scythes: mirrored reaping** | flows with it | flows with it | scythe-reap (held 0.4 t) | scythe-high (held 0.8 t) |
| scythe | flail | scythe and flail: the pole strikes, then the heavy blow | braces | ducks under | flail-whirl (held 0.6 t) | scythe-high (held 1.2 t) |
| scythe | staff | scythe and staff: both poles strike | readies its point | readies its point | staff-poke (held 0.8 t) | scythe-high (held 0.8 t) |
| scythe | odd | scythe and odd: the pole strikes, then a hack | guards | readies its point | odd-club (held 0.6 t) | scythe-high (held 0.4 t) |
| scythe | pickaxe | scythe and pickaxe: the pole strikes, then a hack | guards | readies its point | pick-hack (held 0.6 t) | scythe-high (held 0.4 t) |
| scythe | shovel | scythe and shovel: the pole strikes, then a hack | guards | readies its point | shovel-jab (held 0.6 t) | scythe-high (held 0.4 t) |
| scythe | hoe | scythe and hoe: the pole strikes, then a hack | guards | readies its point | hoe-hook (held 0.6 t) | scythe-high (held 0.4 t) |
| flail | sword | flail and sword: the heavy blow, then a cut | counter-turns | braces | sword-flat (held 1 t) | flail-whirl |
| flail | katana | flail and katana: the heavy blow, then a cut | counter-turns | braces | katana-dash (held 1 t) | flail-whirl |
| flail | rapier | flail and rapier: the heavy blow, then a cut | counter-turns | braces | rapier-flick (held 1 t) | flail-whirl |
| flail | greatsword | flail and greatsword: two heavy blows | braces | braces | great-overhead (held 0.4 t) | flail-crack (held 0.4 t) |
| flail | axe | flail and axe: two heavy blows | braces | braces | axe-chop (held 0.4 t) | flail-crack (held 0.4 t) |
| flail | greataxe | flail and greataxe: two heavy blows | braces | braces | greataxe-overhead (held 0.4 t) | flail-crack (held 0.4 t) |
| flail | hammer | flail and hammer: two heavy blows | braces | braces | hammer-slam (held 0.4 t) | flail-crack (held 0.4 t) |
| flail | dagger | flail and dagger: the blow, then a quick jab | tucks in | braces | dagger-stab (held 1.6 t) | flail-whirl |
| flail | spear | flail and spear: the blow, then the pole strikes | ducks under | braces | spear-jab (held 1.2 t) | flail-whirl (held 0.6 t) |
| flail | glaive | flail and glaive: the blow, then the pole strikes | ducks under | braces | glaive-thrust (held 1.2 t) | flail-whirl (held 0.6 t) |
| flail | scythe | flail and scythe: the blow, then the pole strikes | ducks under | braces | scythe-high (held 1.2 t) | flail-whirl (held 0.6 t) |
| flail | flail | **twin flails: alternating whirls** | counter-turns | counter-turns | flail-lash (held 0.6 t) | flail-crack (held 0.4 t) |
| flail | staff | flail and staff: the blow, then the pole strikes | ducks under | braces | staff-poke (held 1.2 t) | flail-whirl (held 0.6 t) |
| flail | odd | flail and odd: the blow, then a hack | counter-turns | braces | odd-club (held 0.8 t) | flail-whirl (held 0.4 t) |
| flail | pickaxe | flail and pickaxe: the blow, then a hack | counter-turns | braces | pick-hack (held 0.8 t) | flail-whirl (held 0.4 t) |
| flail | shovel | flail and shovel: the blow, then a hack | counter-turns | braces | shovel-jab (held 0.8 t) | flail-whirl (held 0.4 t) |
| flail | hoe | flail and hoe: the blow, then a hack | counter-turns | braces | hoe-hook (held 0.8 t) | flail-whirl (held 0.4 t) |
| staff | sword | staff and sword: the pole strikes, then a cut | guards | readies its point | sword-diag (held 0.6 t) | staff-poke (held 0.4 t) |
| staff | katana | staff and katana: the pole strikes, then a cut | guards | readies its point | katana-kesa (held 0.6 t) | staff-poke (held 0.4 t) |
| staff | rapier | staff and rapier: the pole strikes, then a cut | guards | readies its point | rapier-flick (held 0.6 t) | staff-poke (held 0.4 t) |
| staff | greatsword | staff and greatsword: the pole strikes, then the heavy blow | braces | ducks under | great-sweep (held 0.6 t) | staff-poke (held 1.2 t) |
| staff | axe | staff and axe: the pole strikes, then the heavy blow | braces | ducks under | axe-chop (held 0.6 t) | staff-poke (held 1.2 t) |
| staff | greataxe | staff and greataxe: the pole strikes, then the heavy blow | braces | ducks under | greataxe-overhead (held 0.6 t) | staff-poke (held 1.2 t) |
| staff | hammer | staff and hammer: the pole strikes, then the heavy blow | braces | ducks under | hammer-slam (held 0.6 t) | staff-poke (held 1.2 t) |
| staff | dagger | staff and dagger: the pole strikes, then a jab | tucks in | readies its point | dagger-stab (held 0.8 t) | staff-poke |
| staff | spear | staff and spear: both poles strike | readies its point | readies its point | spear-jab (held 0.8 t) | staff-poke (held 0.8 t) |
| staff | glaive | staff and glaive: both poles strike | readies its point | readies its point | glaive-thrust (held 0.8 t) | staff-poke (held 0.8 t) |
| staff | scythe | staff and scythe: both poles strike | readies its point | readies its point | scythe-high (held 0.8 t) | staff-poke (held 0.8 t) |
| staff | flail | staff and flail: the pole strikes, then the heavy blow | braces | ducks under | flail-whirl (held 0.6 t) | staff-poke (held 1.2 t) |
| staff | staff | **twin staves: alternating strikes** | counter-turns | counter-turns | staff-poke (held 0.4 t) | staff-poke (held 0.8 t) |
| staff | odd | staff and odd: the pole strikes, then a hack | guards | readies its point | odd-club (held 0.6 t) | staff-poke (held 0.4 t) |
| staff | pickaxe | staff and pickaxe: the pole strikes, then a hack | guards | readies its point | pick-hack (held 0.6 t) | staff-poke (held 0.4 t) |
| staff | shovel | staff and shovel: the pole strikes, then a hack | guards | readies its point | shovel-jab (held 0.6 t) | staff-poke (held 0.4 t) |
| staff | hoe | staff and hoe: the pole strikes, then a hack | guards | readies its point | hoe-hook (held 0.6 t) | staff-poke (held 0.4 t) |
| claw | sword | **claw and sword: rake then cut** | guards | flows with it | sword-diag (held 0.3 t) | claw-lunge (held 0.6 t) |
| claw | katana | claw and katana: a jab, then a cut | flows with it | cocks back | katana-dash | claw-lunge (held 0.6 t) |
| claw | rapier | claw and rapier: a jab, then a cut | flows with it | cocks back | rapier-flick | claw-lunge (held 0.6 t) |
| claw | greatsword | claw and greatsword: a jab, then the heavy blow | braces | tucks in | great-sweep | claw-lunge (held 1.6 t) |
| claw | axe | claw and axe: a jab, then the heavy blow | braces | tucks in | axe-chop | claw-lunge (held 1.6 t) |
| claw | greataxe | claw and greataxe: a jab, then the heavy blow | braces | tucks in | greataxe-overhead | claw-lunge (held 1.6 t) |
| claw | hammer | claw and hammer: a jab, then the heavy blow | braces | tucks in | hammer-slam | claw-lunge (held 1.6 t) |
| claw | dagger | **claw and knife: rake and stab** | counter-turns | flows with it | dagger-stab (held 0.2 t) | claw-swipe |
| claw | spear | claw and spear: a jab, then the pole strikes | readies its point | tucks in | spear-jab | claw-lunge (held 0.8 t) |
| claw | glaive | claw and glaive: a jab, then the pole strikes | readies its point | tucks in | glaive-thrust | claw-lunge (held 0.8 t) |
| claw | scythe | claw and scythe: a jab, then the pole strikes | readies its point | tucks in | scythe-high | claw-lunge (held 0.8 t) |
| claw | flail | claw and flail: a jab, then the heavy blow | braces | tucks in | flail-whirl | claw-lunge (held 1.6 t) |
| claw | staff | claw and staff: a jab, then the pole strikes | readies its point | tucks in | staff-poke | claw-lunge (held 0.8 t) |
| claw | odd | claw and odd: a jab, then a hack | flows with it | cocks back | odd-club | claw-lunge (held 0.6 t) |
| claw | pickaxe | claw and pickaxe: a jab, then a hack | flows with it | cocks back | pick-hack | claw-lunge (held 0.6 t) |
| claw | shovel | claw and shovel: a jab, then a hack | flows with it | cocks back | shovel-jab | claw-lunge (held 0.6 t) |
| claw | hoe | claw and hoe: a jab, then a hack | flows with it | cocks back | hoe-hook | claw-lunge (held 0.6 t) |
| odd | sword | odd and sword: a hack, then a cut | guards | guards | sword-diag | odd-club |
| odd | katana | odd and katana: a hack, then a cut | guards | guards | katana-kesa | odd-club |
| odd | rapier | odd and rapier: a hack, then a cut | guards | guards | rapier-flick | odd-club |
| odd | greatsword | odd and greatsword: a hack, then the heavy blow | braces | counter-turns | great-sweep (held 0.4 t) | odd-club (held 0.8 t) |
| odd | axe | odd and axe: a hack, then the heavy blow | braces | counter-turns | axe-chop (held 0.4 t) | odd-club (held 0.8 t) |
| odd | greataxe | odd and greataxe: a hack, then the heavy blow | braces | counter-turns | greataxe-overhead (held 0.4 t) | odd-club (held 0.8 t) |
| odd | hammer | odd and hammer: a hack, then the heavy blow | braces | counter-turns | hammer-slam (held 0.4 t) | odd-club (held 0.8 t) |
| odd | dagger | odd and dagger: hack and stab | cocks back | flows with it | dagger-stab (held 0.6 t) | odd-club |
| odd | spear | odd and spear: a hack, then the pole strikes | readies its point | guards | spear-jab (held 0.4 t) | odd-club (held 0.6 t) |
| odd | glaive | odd and glaive: a hack, then the pole strikes | readies its point | guards | glaive-thrust (held 0.4 t) | odd-club (held 0.6 t) |
| odd | scythe | odd and scythe: a hack, then the pole strikes | readies its point | guards | scythe-high (held 0.4 t) | odd-club (held 0.6 t) |
| odd | flail | odd and flail: a hack, then the heavy blow | braces | counter-turns | flail-whirl (held 0.4 t) | odd-club (held 0.8 t) |
| odd | staff | odd and staff: a hack, then the pole strikes | readies its point | guards | staff-poke (held 0.4 t) | odd-club (held 0.6 t) |
| odd | odd | odd and odd: two hacks | counter-turns | counter-turns | odd-club (held 0.3 t) | odd-club (held 0.3 t) |
| odd | pickaxe | odd and pickaxe: two hacks | counter-turns | counter-turns | pick-hack (held 0.3 t) | odd-club (held 0.3 t) |
| odd | shovel | odd and shovel: two hacks | counter-turns | counter-turns | shovel-jab (held 0.3 t) | odd-club (held 0.3 t) |
| odd | hoe | odd and hoe: two hacks | counter-turns | counter-turns | hoe-hook (held 0.3 t) | odd-club (held 0.3 t) |
| pickaxe | sword | pickaxe and sword: a hack, then a cut | guards | guards | sword-diag | pick-swipe |
| pickaxe | katana | pickaxe and katana: a hack, then a cut | guards | guards | katana-kesa | pick-swipe |
| pickaxe | rapier | pickaxe and rapier: a hack, then a cut | guards | guards | rapier-flick | pick-swipe |
| pickaxe | greatsword | pickaxe and greatsword: a hack, then the heavy blow | braces | counter-turns | great-sweep (held 0.4 t) | pick-hack (held 0.8 t) |
| pickaxe | axe | pickaxe and axe: a hack, then the heavy blow | braces | counter-turns | axe-chop (held 0.4 t) | pick-hack (held 0.8 t) |
| pickaxe | greataxe | pickaxe and greataxe: a hack, then the heavy blow | braces | counter-turns | greataxe-overhead (held 0.4 t) | pick-hack (held 0.8 t) |
| pickaxe | hammer | pickaxe and hammer: a hack, then the heavy blow | braces | counter-turns | hammer-slam (held 0.4 t) | pick-hack (held 0.8 t) |
| pickaxe | dagger | pickaxe and dagger: hack and stab | cocks back | flows with it | dagger-stab (held 0.6 t) | pick-hack |
| pickaxe | spear | pickaxe and spear: a hack, then the pole strikes | readies its point | guards | spear-jab (held 0.4 t) | pick-hack (held 0.6 t) |
| pickaxe | glaive | pickaxe and glaive: a hack, then the pole strikes | readies its point | guards | glaive-thrust (held 0.4 t) | pick-hack (held 0.6 t) |
| pickaxe | scythe | pickaxe and scythe: a hack, then the pole strikes | readies its point | guards | scythe-high (held 0.4 t) | pick-hack (held 0.6 t) |
| pickaxe | flail | pickaxe and flail: a hack, then the heavy blow | braces | counter-turns | flail-whirl (held 0.4 t) | pick-hack (held 0.8 t) |
| pickaxe | staff | pickaxe and staff: a hack, then the pole strikes | readies its point | guards | staff-poke (held 0.4 t) | pick-hack (held 0.6 t) |
| pickaxe | odd | pickaxe and odd: two hacks | counter-turns | counter-turns | odd-club (held 0.3 t) | pick-hack (held 0.3 t) |
| pickaxe | pickaxe | pickaxe and pickaxe: two hacks | counter-turns | counter-turns | pick-hack (held 0.3 t) | pick-hack (held 0.3 t) |
| pickaxe | shovel | pickaxe and shovel: two hacks | counter-turns | counter-turns | shovel-jab (held 0.3 t) | pick-hack (held 0.3 t) |
| pickaxe | hoe | pickaxe and hoe: two hacks | counter-turns | counter-turns | hoe-hook (held 0.3 t) | pick-hack (held 0.3 t) |
| shovel | sword | shovel and sword: a hack, then a cut | guards | guards | sword-diag | shovel-bash |
| shovel | katana | shovel and katana: a hack, then a cut | guards | guards | katana-kesa | shovel-bash |
| shovel | rapier | shovel and rapier: a hack, then a cut | guards | guards | rapier-flick | shovel-bash |
| shovel | greatsword | shovel and greatsword: a hack, then the heavy blow | braces | counter-turns | great-sweep (held 0.4 t) | shovel-jab (held 0.8 t) |
| shovel | axe | shovel and axe: a hack, then the heavy blow | braces | counter-turns | axe-chop (held 0.4 t) | shovel-jab (held 0.8 t) |
| shovel | greataxe | shovel and greataxe: a hack, then the heavy blow | braces | counter-turns | greataxe-overhead (held 0.4 t) | shovel-jab (held 0.8 t) |
| shovel | hammer | shovel and hammer: a hack, then the heavy blow | braces | counter-turns | hammer-slam (held 0.4 t) | shovel-jab (held 0.8 t) |
| shovel | dagger | shovel and dagger: hack and stab | cocks back | flows with it | dagger-stab (held 0.6 t) | shovel-jab |
| shovel | spear | shovel and spear: a hack, then the pole strikes | readies its point | guards | spear-jab (held 0.4 t) | shovel-jab (held 0.6 t) |
| shovel | glaive | shovel and glaive: a hack, then the pole strikes | readies its point | guards | glaive-thrust (held 0.4 t) | shovel-jab (held 0.6 t) |
| shovel | scythe | shovel and scythe: a hack, then the pole strikes | readies its point | guards | scythe-high (held 0.4 t) | shovel-jab (held 0.6 t) |
| shovel | flail | shovel and flail: a hack, then the heavy blow | braces | counter-turns | flail-whirl (held 0.4 t) | shovel-jab (held 0.8 t) |
| shovel | staff | shovel and staff: a hack, then the pole strikes | readies its point | guards | staff-poke (held 0.4 t) | shovel-jab (held 0.6 t) |
| shovel | odd | shovel and odd: two hacks | counter-turns | counter-turns | odd-club (held 0.3 t) | shovel-jab (held 0.3 t) |
| shovel | pickaxe | shovel and pickaxe: two hacks | counter-turns | counter-turns | pick-hack (held 0.3 t) | shovel-jab (held 0.3 t) |
| shovel | shovel | shovel and shovel: two hacks | counter-turns | counter-turns | shovel-jab (held 0.3 t) | shovel-jab (held 0.3 t) |
| shovel | hoe | shovel and hoe: two hacks | counter-turns | counter-turns | hoe-hook (held 0.3 t) | shovel-jab (held 0.3 t) |
| hoe | sword | hoe and sword: a hack, then a cut | guards | guards | sword-diag | hoe-hook |
| hoe | katana | hoe and katana: a hack, then a cut | guards | guards | katana-kesa | hoe-hook |
| hoe | rapier | hoe and rapier: a hack, then a cut | guards | guards | rapier-flick | hoe-hook |
| hoe | greatsword | hoe and greatsword: a hack, then the heavy blow | braces | counter-turns | great-sweep (held 0.4 t) | hoe-hook (held 0.8 t) |
| hoe | axe | hoe and axe: a hack, then the heavy blow | braces | counter-turns | axe-chop (held 0.4 t) | hoe-hook (held 0.8 t) |
| hoe | greataxe | hoe and greataxe: a hack, then the heavy blow | braces | counter-turns | greataxe-overhead (held 0.4 t) | hoe-hook (held 0.8 t) |
| hoe | hammer | hoe and hammer: a hack, then the heavy blow | braces | counter-turns | hammer-slam (held 0.4 t) | hoe-hook (held 0.8 t) |
| hoe | dagger | hoe and dagger: hack and stab | cocks back | flows with it | dagger-stab (held 0.6 t) | hoe-hook |
| hoe | spear | hoe and spear: a hack, then the pole strikes | readies its point | guards | spear-jab (held 0.4 t) | hoe-hook (held 0.6 t) |
| hoe | glaive | hoe and glaive: a hack, then the pole strikes | readies its point | guards | glaive-thrust (held 0.4 t) | hoe-hook (held 0.6 t) |
| hoe | scythe | hoe and scythe: a hack, then the pole strikes | readies its point | guards | scythe-high (held 0.4 t) | hoe-hook (held 0.6 t) |
| hoe | flail | hoe and flail: a hack, then the heavy blow | braces | counter-turns | flail-whirl (held 0.4 t) | hoe-hook (held 0.8 t) |
| hoe | staff | hoe and staff: a hack, then the pole strikes | readies its point | guards | staff-poke (held 0.4 t) | hoe-hook (held 0.6 t) |
| hoe | odd | hoe and odd: two hacks | counter-turns | counter-turns | odd-club (held 0.3 t) | hoe-hook (held 0.3 t) |
| hoe | pickaxe | hoe and pickaxe: two hacks | counter-turns | counter-turns | pick-hack (held 0.3 t) | hoe-hook (held 0.3 t) |
| hoe | shovel | hoe and shovel: two hacks | counter-turns | counter-turns | shovel-jab (held 0.3 t) | hoe-hook (held 0.3 t) |
| hoe | hoe | hoe and hoe: two hacks | counter-turns | counter-turns | hoe-hook (held 0.3 t) | hoe-hook (held 0.3 t) |
<!-- MATRIX:END -->

## Effects (JASPR_COMBATFX stage)
`client-mods/combatfx/` (runtime + engine glue), builder `scripts/build-combatfx-client.cjs` (fenced, `--unpatch`).
Hooks, all on original engine text no other stage replaced (the gun stage's C45, Ch0 and Chn are left alone):
- `EntityRenderer.hurtCameraEffect` (FN4), at its start: `JasprCameraFXApply` applies the camera kicks for the world
  camera and the first-person hand pass, and once per frame turns the melee impact intents into kicks and sparks.
- `EntityRenderer.renderHand` (Ddz): `JasprCameraFX.pass` around its hurtCameraEffect call (a push back moves only the
  world), and after both first-person hands are drawn `JasprCombatFXHand` draws the trails (one tessellator draw of
  POSITION_COLOR quads, alpha-blended, depth-tested, no depth writes; every touched GL state restored).

`JasprCameraFX.kick({ pitch, yaw, roll, back, ms, curve })` (also used by the gun stage):
- pitch degrees, positive = the view kicks UP; yaw positive = turns right; roll positive = clockwise; back blocks,
  positive = the camera is pushed back (world only); ms 16..1000 (default 120);
- curve: `punch` (default: quick rise, smooth fall), `recoil` (very quick rise, slower return), `snap` (instant, then
  back), `smooth` (sine), `spring` (damped wobble);
- each kick clamped (6 degrees, back -0.15..0.25), at most 12 at once (oldest dropped), the sum clamped (8 degrees,
  back -0.2..0.3); clocked by the player's ticks + partial ticks; third person gets half; never touches the player's
  real rotation or what the server sees.

Sparks: `World.spawnParticle` (so the particle setting and range apply), at the struck side of the target's box, moving
along the blow; at most 9 per impact and 48 per second. Particle kinds: crit (blade, pierce), crit + cloud (blunt),
portal + crit (claw), witch magic + crit (staff), + magic crit on crits, + cloud on a kill, crit only on a block.

Failure: any thrown error disables only its own part (camera, kicks, sparks or trails) and is reported once.
Diagnostics: `window.JasprCombatFXDiagnostics.status()` and `window.JasprMeleeDiagnostics.status()` (counters, current
classes and moves, no positions or names); events `jaspercraft.combatfx.state` (a part first active) and
`jaspercraft.combatfx.error` (at most 12 per page, same-origin, bounded).

## The combat feed (workstream A) and fallbacks
Swing sources are merged per hand: `JasprMeleeMotion.mark` (Better Combat's `markSwing`), the feed's `swing` events
(`tick` = the player's tick count at the click: the motion starts at the next tick boundary) and, if neither exists,
polling `JasprBetterCombat.motion()`. A swing aimed at an entity (the feed's `target`) gets its impact at contact; the
verdict (`hit`, `blocked`, `miss`, or `denied` with a `ref`, found by `ref`) upgrades it (crit, sweep, kill, block),
cancels it if it arrives first, or fires a late impact without hit-stop for a hit the client did not aim at. Without a
feed the swing animates and nothing else happens.

## Knobs
- `JasprMeleeMotion.knobs`: `motion` (all off), `hitstop` (scale, 0 = none), `amplitude` (energy-scaled arcs), `support`
  and `dual` (dual choreography), `impacts`, `trails`, `trailTicks` (1.8), `trailSamples` (10).
- `JasprCameraFX.scale` (0..1), `JasprCameraFX.enabled`; `JasprCombatFX.parts` (`kicks`, `sparks`, `trails`).
- In `melee-motion.js`: `COMBO_WINDOW` 26 ticks, `BOTH_WINDOW` 4.5 ticks (a joint move), `MERGE_MS` 320.
- Per class (`melee-moves.js`): `period`, `weight`, `hitstop` (ms), `kick` and `kickMs`, `sparks`, `color`, `trail`
  (`from`, `to` in sprite space, `width`, `alpha`, `len`), `chain`, `sprint`, `jump`.
- The personal Better Combat option "JasperCraft melee animations" switches the whole first-person motion off.

## Tuning workflow (offline, no game, no browser)
```
node scripts/melee-preview-metrics.cjs [move|class:<name> ...]   # closeness to the eye, time on screen, contact spot, speeds
node scripts/melee-preview-overview.cjs out.png class:axe        # a row of frames per move (trails, hit-stop included)
node scripts/melee-preview.cjs <outDir> axe-chop [--item wardcleaver] [--miss]   # 16 frames + a blade-tip path plot
node scripts/melee-preview-chain.cjs out.png sword 0,6,12,18 [ticks] [--off dagger 3,9]   # a real combo, one or two hands
node scripts/melee-preview-dual.cjs out.png sword dagger both    # alternating and joint strikes of one pair
node scripts/melee-preview-dual.cjs --sheet <outDir> [main ...]  # every off-hand class for a main class
node scripts/melee-model-table.cjs [--check]                     # after changing an Apocalypse melee model
```
The renderer reproduces the first-person transform chain on the real textures (sprites) and the pack's cuboid models.

## Build and tests
```
node scripts/build-melee-client.cjs [classes.js]       # swaps only the JASPR_MELEE_FP region -> candidate/melee-client/
node scripts/build-combatfx-client.cjs [classes.js]    # adds JASPR_COMBATFX -> candidate/combatfx/ (--unpatch to remove)
node --test --test-concurrency=1 tests/melee-motion.test.cjs        # one file at a time
node --test --test-concurrency=1 tests/combatfx-runtime.test.cjs
node --test --test-concurrency=1 tests/melee-build.test.cjs
node --test --test-concurrency=1 tests/melee-native.test.cjs        # loads the built client offline
node --test --test-concurrency=1 tests/melee-native-dual.test.cjs   # LIVE -> A's head -> melee -> COMBATFX
```
Assembly (COMBAT_REWORK.md): LIVE -> `build-bettercombat-client.cjs` -> `build-melee-client.cjs` ->
`build-mobends-client.cjs --module` -> `build-combatfx-client.cjs` -> `build-gunfx-client.cjs`;
`node scripts/assemble-combat-client.cjs` runs that chain one builder at a time into `candidate/combat-deploy/` (with a
manifest of every step's sha256). The melee builder needs no Mo' Bends unpatching (it only replaces its region and
proves restore(build(x)) === x); the COMBATFX builder refuses to build under JASPR_GUNFX.

Real client: `tests/combat-browser.cjs` (one lean session against the loopback fixture; see its header) attacks a mob
with each hand, alternates two dual pairs, trades with a villager, swings five classes and fires two guns, and reads
the diagnostics (`JasprMeleeDiagnostics`, `JasprCombatFXDiagnostics`, `JasprGunFXDiagnostics`, the combat feed).

## Third person
Mo' Bends plays third-person attacks. Before this change it played every attack with the primary arm and the
main-hand item's animation (Mo' Bends 1.2.2 reads only isSwingInProgress), so an off-hand attack showed as a main-arm
swing. Mo' Bends now swings the arm of the hand that attacked, with that hand's item, and alternating attacks alternate
the arms (MOBENDS_UPDATE.md, "Off-hand attacks"; install with `node scripts/build-mobends-client.cjs --module`, a
module-only swap that needs no other stage taken off).
