'use strict';
// The gate guide kit (owner, 2026-09-29): Atlas, Drownhollow and the Nether can each be beaten on its own, in any order,
// and it is obvious how. A guide at every gate hands out, as often as asked, a compass that points to the next task, a
// checklist that ticks itself off and a map that marks the way; finishing a realm crowns the player, all three crown them
// Conqueror of the Three Realms. One GuideKit.java is shared word for word by the three plugins; each supplies a Realm.
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const root = path.resolve(__dirname, '..');
const read = file => fs.readFileSync(path.join(root, file), 'utf8');
const P = 'server/custom-plugins/';
const KITS = {nether: P + 'JasprNether/src/chat/jaspr/nether/GuideKit.java', ruins: P + 'JasprRuins/src/chat/jaspr/ruins/GuideKit.java',
  atlas: P + 'JasprAtlas/src/chat/jaspr/atlas/GuideKit.java', backrooms: P + 'JasprBackrooms/src/chat/jaspr/backrooms/GuideKit.java'};

test('one kit, identical copies in all four realm plugins (only the package line differs)', () => {
  const body = f => read(f).replace(/\r\n/g, '\n').split('\n').slice(1).join('\n');
  const nether = body(KITS.nether);
  for (const [k, f] of Object.entries(KITS)) {
    assert.ok(read(f).startsWith('package chat.jaspr.' + k + ';'), k);
    assert.equal(body(f), nether, k + ' copy matches the Nether one');
  }
});

test('the kit: three items as often as asked, self-updating, victory and the Three Realms, logged', () => {
  const kit = read(KITS.nether);
  // items: compass, checklist, map; the guide gives what is missing, a whole new set when sneaking
  for (const k of ['ItemStack compass()', 'ItemStack checklist(Player p)', 'ItemStack map()', 'int offer(Player p, boolean all)', 'offer(p, p.isSneaking())'])
    assert.ok(kit.includes(k), k);
  // the guide: placed at a gate, cannot be hurt or traded with
  for (const k of ['Villager ensureGuide(Location gate)', 'g.setInvulnerable(true)', 'g.setAI(false)', 'public void talk(PlayerInteractEntityEvent e)', 'e.setCancelled(true);'])
    assert.ok(kit.includes(k), k);
  // self-updating: tasks re-read every second, books rewritten, the map redrawn, the needle and the HUD
  for (const k of ['refreshBooks(p, t)', 'write(e.getItem(), p, tasks(p))', 'p.setCompassTarget(target)', 'ChatMessageType.ACTION_BAR', 'final class Chart extends MapRenderer',
    'realm.paintVersion()', 'Bukkit.getScheduler().runTask(plugin, this::view)'])
    assert.ok(kit.includes(k), k);
  // the compass and checklist never get in the way of chests, doors and levers
  assert.ok(kit.includes('if (e.getAction() == Action.RIGHT_CLICK_BLOCK && interactive(e.getClickedBlock())) return;'));
  assert.ok(!/k\.equals\("compass"\)\) return;\s*e\.setCancelled\(true\)/.test(kit), 'the compass does not cancel block use');
  // victory, past victories and all three
  for (const k of ['void victory(Player p)', 'void recordPast(Player p, String deed)', 'private void threeRealms(Player p)', '"jr_beat_all"', 'static final String[] REALMS = {"atlas", "ruins", "nether"};'])
    assert.ok(kit.includes(k), k);
  // diagnostics (privacy-safe: realm keys and counts, no names)
  for (const k of ['GUIDE_ITEMS realm=', 'GUIDE_PLACED realm=', 'GUIDE_TASK_DONE realm=', 'GUIDE_VICTORY realm=', 'GUIDE_PAST_VICTORY realm=', 'GUIDE_THREE_REALMS realm=', 'GUIDE_MAP realm=', 'GUIDE_TASKS_FAILED realm=', 'GUIDE_TICK_FAILED realm='])
    assert.ok(kit.includes(k), k);
  assert.ok(!/getLogger\(\)\.info\("GUIDE_[^;]*getName\(\)/.test(kit), 'kit logs carry no player names');
  // checklist pages stay within the written-book limit
  assert.ok(kit.includes('next.length() > 250 ? next.substring(0, 250)') && kit.includes('tip.length() > 250 ? tip.substring(0, 250)'));
  // /goals lists every realm
  assert.ok(kit.includes('public void goals(PlayerCommandPreprocessEvent e)'));
  for (const plugin of ['JasprNether', 'JasprRuins', 'JasprAtlas'])
    assert.match(read(P + plugin + '/resources/plugin.yml'), /goals:[\s\S]*aliases: \[goal, quests, quest, checklist\]/, plugin);
});

test('each realm supplies its road, its guide at the gates and its victory', () => {
  const nq = read(P + 'JasprNether/src/chat/jaspr/nether/NetherQuest.java');
  const rq = read(P + 'JasprRuins/src/chat/jaspr/ruins/RuinsQuest.java');
  const aq = read(P + 'JasprAtlas/src/chat/jaspr/atlas/AtlasQuest.java');
  for (const [q, key, order] of [[nq, 'nether', 2], [rq, 'ruins', 1], [aq, 'atlas', 0]]) {
    assert.ok(q.includes('implements GuideKit.Realm'), key);
    assert.ok(q.includes('public String key() { return "' + key + '"; }'), key);
    assert.ok(q.includes('public int order() { return ' + order + '; }'), key);
  }
  // the Nether: Cathedral, a Potion of Sorrow (the font fills a bottle on a click of either button or a touch), the urn, the Queen
  for (const t of ['"Reach the Spore Cathedral"', '"Take a Potion of Sorrow"', '"Pour it into the Urn of Sorrow"', '"Slay the Ghast Queen"', 'void touchFonts()',
    'Action.LEFT_CLICK_BLOCK', 'NETHER_FONT_GIVEN at=', 'public void keepFont('])
    assert.ok(nq.includes(t), t);
  assert.ok(read(P + 'JasprNether/src/chat/jaspr/nether/Boss.java').includes('plugin.guide.victory(p)'), 'the Queen conquers the Nether');
  // Drownhollow: three different Seals, the Door, the Herald
  for (const t of ['"Win a Warden\'s Seal"', '"Win a second, different Seal"', '"Win a third, different Seal"', '"Set the Seals into the Great Door"', '"Slay the Dreamer\'s Herald"'])
    assert.ok(rq.includes(t), t);
  const bosses = read(P + 'JasprRuins/src/chat/jaspr/ruins/Bosses.java');
  assert.ok(bosses.includes('plugin.guide().victory(p)'), 'the Herald conquers Drownhollow');
  assert.ok(bosses.includes('doorOpen = doorSeals.size() >= SEALS_NEEDED;'), 'an open Door survives a restart');
  assert.ok(bosses.includes('e.getHand() == org.bukkit.inventory.EquipmentSlot.OFF_HAND'), 'one answer per click at the Door');
  const ruins = read(P + 'JasprRuins/src/chat/jaspr/ruins/RuinsPlugin.java');
  assert.ok(ruins.includes('guide.recordPast(p, "you slew the Dreamer\'s Herald")') && read(P + 'JasprRuins/src/chat/jaspr/ruins/Portals.java').includes('plugin.quest().gate(player, arrive)'));
  // Atlas: the Codex's road, and the Echo of the Pyrarch for those who come after victory
  for (const t of ['"Meet Archon Kleio"', '"Take the Oath from Lysandra"', '"Break Kallias at the Pylon of Teeth"', '"Learn the Hymn from Iaso"', '"Break Melaina, the Stiller"',
    '"Get the Counterpoint from Perdix"', '"Break Daidaros, the Forgemaster"', '"Get the Charter from Hesper"', '"Break Keleos, the Silent Magistrate"',
    '"Take the Light of Theano from Kleio"', '"Defeat the Echo of the Pyrarch"', '"End the Pyrarch in Anthrakion"'])
    assert.ok(aq.includes(t), t);
  const ab = read(P + 'JasprAtlas/src/chat/jaspr/atlas/Bosses.java');
  assert.ok(ab.includes('if (fallen(b)) return b == Boss.PYRARCH && plugin.state().victory;') && ab.includes('private boolean echoWanted(World w, Location at)'), 'the Echo rises after victory');
  assert.ok(ab.includes('private void echoFallen(EntityDeathEvent e, Fight f)') && ab.includes('ATLAS_ECHO_FALLEN by='), 'the Echo conquers Atlas');
  assert.ok(ab.includes('for (Player p : by) plugin.guide().victory(p);'), 'the Pyrarch conquers Atlas');
  assert.ok(read(P + 'JasprAtlas/src/chat/jaspr/atlas/Figures.java').includes('c.s.victory && c.plugin.guide() != null && !c.plugin.guide().beaten(c.p)'), 'Kleio lends the Light for the Echo');
  const atlas = read(P + 'JasprAtlas/src/chat/jaspr/atlas/AtlasPlugin.java');
  assert.ok(atlas.includes('guide.recordPast(p, "you rekindled the Star")') && read(P + 'JasprAtlas/src/chat/jaspr/atlas/Gates.java').includes('plugin.quest().gate(player, arrive)'));
  assert.ok(!atlas.includes('Talk.give(p, Items.book("Welcome, Stranger"'), 'three items on arrival, no more');
});
