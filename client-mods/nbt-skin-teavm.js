/* Item skins chosen by an item's own data (owner, 2026-10-05: "sanitized flesh should have its own texture, and it should
 * look like jerky").
 *
 * Vanilla can only pick a model by an item's damage, and only for items that can be damaged. Stackable supplies (Sanitized
 * Flesh is a cooked beef) have no damage to spare, so this stage lets such an item answer a model predicate of its own:
 *
 *   "overrides": [{"predicate": {"jaspr_skin": 1}, "model": "item/jaspr_sanitized_flesh"}]
 *
 * The item is registered with a property getter named jaspr_skin. The getter reads the id the server (or the creative / recipe
 * catalogues) wrote into the stack -- JasprApocalypse.id, or JasprCreative.id for a catalogue placeholder -- and answers the
 * number SKINS gives that id (0 for anything else, which keeps the item's vanilla model). Nothing about the item changes:
 * not its NBT, its stacking or its behaviour; only the model the client draws. Items made before this stage existed are
 * skinned the moment they are seen.
 *
 * Written against the deployed client's own names: Bb ResourceLocation (Gp9 = new ResourceLocation(String)), DQE
 * Item.addPropertyOverride, GaK ItemStack.getSubCompound(String), F54 NBTTagCompound.getString(String). The stage's builder
 * (scripts/build-nbt-skin-client.cjs) checks they are all still declared. Function declarations only, so the hook may run
 * before this block is reached; the getter and the Java strings are made on first use. ASCII only.
 */
var JasprNbtSkinState = {getter: null, keys: null, installed: 0, hits: 0, errors: 0};
/** id -> value of the jaspr_skin predicate (an override matches when the value is at least its own). */
var JasprNbtSkinTable = {sanitized_flesh: 1};

function JasprNbtSkinKeys() {
  var s = JasprNbtSkinState;
  if (s.keys === null) s.keys = {apocalypse: $rt_str("JasprApocalypse"), creative: $rt_str("JasprCreative"), id: $rt_str("id")};
  return s.keys;
}

/** The value of the jaspr_skin predicate for a stack: 0 for every item that has no skin. Never throws. */
function JasprNbtSkinValue(stack) {
  var s = JasprNbtSkinState;
  try {
    if (stack === null || stack === undefined) return 0;
    var k = JasprNbtSkinKeys();
    var sub = GaK(stack, k.apocalypse);
    if (sub === null) sub = GaK(stack, k.creative);
    if (sub === null) return 0;
    var id = $rt_ustr(F54(sub, k.id));
    if (Object.prototype.hasOwnProperty.call(JasprNbtSkinTable, id)) { s.hits++; return JasprNbtSkinTable[id]; }
    return 0;
  } catch (e) {
    s.errors++;
    return 0;
  }
}

/** Called once for each item that may carry a skin, right after the item registry hands it out (Items static initialiser). */
function JasprNbtSkinInstall(item) {
  var s = JasprNbtSkinState;
  try {
    if (item === null || item === undefined) return;
    if (s.getter === null) s.getter = {TM: function (stack, world, entity) { return JasprNbtSkinValue(stack); }};
    var key = new Bb;
    Gp9(key, $rt_str("jaspr_skin"));
    DQE(item, key, s.getter);
    s.installed++;
  } catch (e) {
    s.errors++;
  }
}
