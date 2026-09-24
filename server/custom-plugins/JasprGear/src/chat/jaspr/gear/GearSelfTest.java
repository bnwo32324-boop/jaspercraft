package chat.jaspr.gear;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.attribute.Attribute;
import org.bukkit.command.CommandSender;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/**
 * Player-free runtime checks against real Paper classes: identity/NBT, slot rules, recipes,
 * persistence round trip including a corrupt file, effect totals, menu rendering and the
 * panel wire format; Phase 2 consumables, loot parity with Phase 1, adrenaline math, vitals
 * persistence and the HUD packet. Run with -Djaspr.gear.selftest=true or "gear selftest" on the console.
 */
final class GearSelfTest {
    private final GearPlugin plugin;
    private final List<String> failures = new ArrayList<String>();
    private int checks;

    GearSelfTest(GearPlugin plugin) { this.plugin = plugin; }

    private void check(boolean ok, String what) {
        checks++;
        if (!ok) failures.add(what);
    }

    void run(CommandSender out) {
        try {
            items();
            slots();
            recipes();
            store();
            effects();
            menu();
            wire();
            loot();
            bank();
            supplies();
            lootParity();
            vitals();
            vitalsStore();
            hud();
            mutations();
            bosses();
            backpacks();
        } catch (Throwable t) {
            failures.add("exception " + t);
        }
        String line = (failures.isEmpty() ? "GEAR_SELFTEST PASS" : "GEAR_SELFTEST FAIL") + " checks=" + checks
            + " failures=" + failures.size() + (failures.isEmpty() ? "" : " first=" + failures.subList(0, Math.min(5, failures.size())));
        plugin.getLogger().info(line);
        if (out != null && out != Bukkit.getConsoleSender()) out.sendMessage(line);
    }

    private void items() throws Exception {
        java.util.Set<Integer> models = new java.util.HashSet<Integer>();
        for (GearItem item : GearItem.values()) {
            ItemStack stack = GearItems.create(item);
            check(GearItems.identify(stack) == item, "identify " + item.id);
            check(stack.getType() == org.bukkit.Material.STONE_HOE && stack.getDurability() == item.model, "carrier/model " + item.id);
            check(stack.getItemMeta().isUnbreakable(), "unbreakable " + item.id);
            check(stack.getMaxStackSize() == 1, "unstackable " + item.id);
            ItemStack back = GearItems.fromSnbt(GearItems.toSnbt(stack));
            check(back != null && GearItems.identify(back) == item && back.isSimilar(stack), "snbt round trip " + item.id);
            check(models.add(item.model) && item.model > 0 && item.model < GearItems.ICON_BASE_MODEL, "unique model " + item.id);
            check(item.effects.length >= 1 && item.effects.length <= 3, "lore size " + item.id);
            ItemStack plain = new ItemStack(org.bukkit.Material.STONE_HOE, 1, (short) item.model);
            check(GearItems.identify(plain) == null, "untagged hoe is not gear " + item.id);
            ItemStack renamed = stack.clone();
            org.bukkit.inventory.meta.ItemMeta meta = renamed.getItemMeta();
            meta.setDisplayName("Forged");
            renamed.setItemMeta(meta);
            check(GearItems.identify(renamed) == item, "rename keeps identity " + item.id);
        }
        for (int i = 0; i < GearType.SLOT_COUNT; i++) {
            ItemStack icon = GearItems.icon(i);
            check(GearItems.isIcon(icon) && GearItems.identify(icon) == null, "icon " + i);
            check(icon.getDurability() == GearItems.iconModel(i) && icon.getDurability() >= GearItems.ICON_BASE_MODEL, "icon model " + i);
        }
        check(GearItems.identify(null) == null && GearItems.identify(new ItemStack(org.bukkit.Material.DIRT)) == null, "non-gear");
    }

    private void slots() {
        for (GearItem item : GearItem.values()) {
            int fits = 0;
            for (int s = 0; s < GearType.SLOT_COUNT; s++) {
                boolean expect = item.type == GearType.ANY || GearType.SLOTS[s] == item.type;
                check(item.type.fits(s) == expect, "fits " + item.id + "@" + s);
                if (expect) fits++;
            }
            check(fits == (item.type == GearType.ANY ? 7 : item.type == GearType.RING ? 2 : 1), "slot count " + item.id);
            check(!item.type.fits(-1) && !item.type.fits(7), "bounds " + item.id);
        }
        GearProfile prof = new GearProfile(UUID.randomUUID());
        check(prof.freeSlotFor(GearItem.TRITIUM_RING) == 1, "first ring slot");
        prof.slots[1] = GearItems.create(GearItem.TRITIUM_RING);
        check(prof.freeSlotFor(GearItem.NECROTIC_RING) == 2, "second ring slot");
        prof.slots[2] = GearItems.create(GearItem.NECROTIC_RING);
        check(prof.freeSlotFor(GearItem.TRITIUM_RING) == -1, "rings full");
        check(prof.freeSlotFor(GearItem.FIELD_JOURNAL) == 6, "any-slot prefers the charm slot");
        prof.slots[6] = GearItems.create(GearItem.TEDDY_BEAR);
        check(prof.freeSlotFor(GearItem.FIELD_JOURNAL) == 0, "any-slot then takes the first free slot");
        prof.slots[6] = null;
        check(prof.freeSlotFor(GearItem.RIOT_VEST) == 5, "body slot");
        check(prof.worn().equals(EnumSet.of(GearItem.TRITIUM_RING, GearItem.NECROTIC_RING)), "worn set");
    }

    private void recipes() {
        check(plugin.recipeCount() == GearItem.values().length, "recipes registered " + plugin.recipeCount());
        // End-game prices: a Nether Star and 2+ diamond blocks each; rank 3+ an emerald block; rank 4+ three
        // diamond blocks; rank 5 two emerald blocks.
        for (GearItem item : GearItem.values()) {
            int star = 0, diamond = 0, emerald = 0;
            java.util.Map<Character, String> key = item.ingredientMap();
            for (String row : item.shape) for (char ch : row.toCharArray()) {
                String m = key.get(ch);
                if (m == null) continue;
                if (m.equals("NETHER_STAR")) star++;
                if (m.equals("DIAMOND_BLOCK")) diamond++;
                if (m.equals("EMERALD_BLOCK")) emerald++;
            }
            check(star == 1 && diamond >= 2, "expensive: star + 2 diamond blocks " + item.id);
            check(item.rank < 3 || emerald >= 1, "expensive: emerald block from rank 3 " + item.id);
            check(item.rank < 4 || diamond >= 3, "expensive: 3 diamond blocks from rank 4 " + item.id);
            check(item.rank < 5 || emerald >= 2, "expensive: 2 emerald blocks at rank 5 " + item.id);
        }
        // No other recipe on this server (vanilla or any plugin) may share a trinket's pattern.
        java.util.Map<String, String> mine = new java.util.HashMap<String, String>();
        java.util.Iterator<org.bukkit.inventory.Recipe> all = Bukkit.recipeIterator();
        java.util.List<org.bukkit.inventory.ShapedRecipe> others = new ArrayList<org.bukkit.inventory.ShapedRecipe>();
        while (all.hasNext()) {
            org.bukkit.inventory.Recipe r = all.next();
            if (!(r instanceof org.bukkit.inventory.ShapedRecipe)) continue;
            GearItem g = GearItems.identify(r.getResult());
            if (g != null) mine.put(g.id, pattern((org.bukkit.inventory.ShapedRecipe) r, false));
            else others.add((org.bukkit.inventory.ShapedRecipe) r);
        }
        check(mine.size() == GearItem.values().length, "all trinket patterns found");
        java.util.Set<String> seen = new java.util.HashSet<String>();
        for (String p : mine.values()) check(seen.add(p), "trinket patterns distinct");
        int clashes = 0;
        for (org.bukkit.inventory.ShapedRecipe o : others) {
            String a = pattern(o, false), b = pattern(o, true);
            for (java.util.Map.Entry<String, String> m : mine.entrySet())
                if (samePattern(m.getValue(), a) || samePattern(m.getValue(), b)) { clashes++; failures.add("recipe clash " + m.getKey() + " vs " + o.getResult().getType()); }
        }
        check(clashes == 0, "no other recipe shares a trinket pattern (" + others.size() + " shaped recipes compared)");
        for (GearItem item : GearItem.values()) {
            boolean found = false;
            for (org.bukkit.inventory.Recipe r : Bukkit.getRecipesFor(GearItems.create(item)))
                if (GearItems.identify(r.getResult()) == item) found = true;
            check(found, "recipe lookup " + item.id);
            for (String ingredient : item.ingredientMap().values())
                check(!ingredient.startsWith("STONE_HOE"), "no gear/carrier ingredient " + item.id);
        }
    }

    /** Trimmed, optionally mirrored grid of "MATERIAL:data" cells ("*" data = any), rows joined by "/". */
    private static String pattern(org.bukkit.inventory.ShapedRecipe r, boolean mirror) {
        String[] shape = r.getShape();
        java.util.Map<Character, ItemStack> key = r.getIngredientMap();
        StringBuilder out = new StringBuilder();
        for (String row : shape) {
            StringBuilder line = new StringBuilder();
            for (int i = 0; i < row.length(); i++) {
                char ch = row.charAt(mirror ? row.length() - 1 - i : i);
                ItemStack in = key.get(ch);
                if (i > 0) line.append(',');
                if (in == null || in.getType() == org.bukkit.Material.AIR) line.append('.');
                else line.append(in.getType().name()).append(':').append(in.getDurability() < 0 || in.getDurability() == Short.MAX_VALUE ? "*" : String.valueOf(in.getDurability()));
            }
            if (out.length() > 0) out.append('/');
            out.append(line);
        }
        return out.toString();
    }

    private static boolean samePattern(String a, String b) {
        String[] ra = a.split("/", -1), rb = b.split("/", -1);
        if (ra.length != rb.length) return false;
        for (int i = 0; i < ra.length; i++) {
            String[] ca = ra[i].split(",", -1), cb = rb[i].split(",", -1);
            if (ca.length != cb.length) return false;
            for (int j = 0; j < ca.length; j++) {
                if (ca[j].equals(cb[j])) continue;
                String[] x = ca[j].split(":"), y = cb[j].split(":");
                if (x.length < 2 || y.length < 2 || !x[0].equals(y[0]) || !(x[1].equals("*") || y[1].equals("*"))) return false;
            }
        }
        return true;
    }

    private void bosses() {
        check(GearPlugin.bossType(org.bukkit.entity.EntityType.WITHER) && GearPlugin.bossType(org.bukkit.entity.EntityType.ENDER_DRAGON)
            && GearPlugin.bossType(org.bukkit.entity.EntityType.ELDER_GUARDIAN) && !GearPlugin.bossType(org.bukkit.entity.EntityType.ZOMBIE), "boss types");
        org.bukkit.World w = Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0);
        if (w != null) {
            org.bukkit.Location at = w.getSpawnLocation().clone().add(0, 2, 0);
            org.bukkit.entity.LivingEntity plain = (org.bukkit.entity.LivingEntity) w.spawnEntity(at, org.bukkit.entity.EntityType.ZOMBIE);
            org.bukkit.entity.LivingEntity tagged = (org.bukkit.entity.LivingEntity) w.spawnEntity(at, org.bukkit.entity.EntityType.ZOMBIE);
            tagged.addScoreboardTag(GearPlugin.BOSS_TAG);
            org.bukkit.entity.LivingEntity legacy = (org.bukkit.entity.LivingEntity) w.spawnEntity(at, org.bukkit.entity.EntityType.HUSK);
            legacy.setMetadata(GearPlugin.BOSS_TAG, new org.bukkit.metadata.FixedMetadataValue(plugin, "test"));
            check(!GearPlugin.boss(plain) && GearPlugin.boss(tagged) && GearPlugin.boss(legacy), "boss markers (tag, metadata)");
            plain.remove(); tagged.remove(); legacy.remove();
        }
        int[] counts = new int[GearItem.values().length];
        java.util.Random r = new java.util.Random(99L);
        int n = 150000;
        for (int i = 0; i < n; i++) counts[GearItems.identify(GearApi.bossLoot(r)).ordinal()]++;
        for (GearItem g : GearItem.values())
            check(Math.abs(counts[g.ordinal()] - n / (double) counts.length) < 5 * Math.sqrt(n / (double) counts.length), "boss loot uniform " + g.id);
        check(GearApi.bossLoot(null) == null, "boss loot null-safe");
    }

    private void store() throws Exception {
        File dir = new File(plugin.getDataFolder(), "selftest-" + System.nanoTime());
        GearStore store = new GearStore(dir, plugin.getLogger());
        GearProfile a = new GearProfile(UUID.randomUUID());
        a.slots[0] = GearItems.create(GearItem.REBREATHER);
        a.slots[3] = GearItems.create(GearItem.CAPACITOR_BELT);
        ItemStack named = GearItems.create(GearItem.TEDDY_BEAR);
        org.bukkit.inventory.meta.ItemMeta meta = named.getItemMeta();
        meta.setDisplayName("Mr. Buttons \"the brave\" \\ ok");
        named.setItemMeta(meta);
        a.slots[6] = named;
        a.lastStandReady = 123456789L;
        store.saveNow(a);
        check(store.file(a.uuid).isFile() && !new File(dir, a.uuid + ".gear.tmp").exists(), "atomic write left no temp");
        GearProfile b = new GearProfile(a.uuid);
        check(store.load(b), "load ok");
        for (int i = 0; i < GearType.SLOT_COUNT; i++)
            check(GearItems.toSnbt(a.slots[i]).equals(GearItems.toSnbt(b.slots[i])), "slot " + i + " round trip");
        check(b.lastStandReady == 123456789L, "last stand persisted");
        check(b.slots[6] != null && "Mr. Buttons \"the brave\" \\ ok".equals(b.slots[6].getItemMeta().getDisplayName()), "custom name survives");
        GearProfile empty = new GearProfile(UUID.randomUUID());
        check(store.load(empty) && empty.empty(), "missing file is empty");
        Files.write(store.file(empty.uuid).toPath(), "garbage\n0=@@@".getBytes(StandardCharsets.UTF_8));
        GearProfile broken = new GearProfile(empty.uuid);
        boolean safe = store.load(broken);
        File[] aside = dir.listFiles((d, n) -> n.startsWith(empty.uuid + ".gear.corrupt-"));
        check(safe && broken.empty() && aside != null && aside.length == 1 && !store.file(empty.uuid).exists(), "corrupt file preserved aside");
        store.flush();
        for (File f : dir.listFiles()) Files.deleteIfExists(f.toPath());
        Files.deleteIfExists(dir.toPath());
    }

    private void effects() {
        Map<Attribute, Double> t = plugin.abilities.expectedTotals(EnumSet.of(GearItem.CAPACITOR_BELT, GearItem.SPRINT_BRACE, GearItem.RAZOR_CLAWS));
        check(close(t.get(Attribute.GENERIC_MOVEMENT_SPEED), 0.25), "speed total");
        check(close(t.get(Attribute.GENERIC_KNOCKBACK_RESISTANCE), 0.30), "knockback total");
        check(close(t.get(Attribute.GENERIC_ATTACK_DAMAGE), 1.0), "attack total");
        check(plugin.abilities.expectedTotals(EnumSet.noneOf(GearItem.class)).isEmpty(), "no gear, no modifiers");
        check(plugin.abilities.expectedTotals(EnumSet.of(GearItem.GYRO_STABILIZER)).get(Attribute.GENERIC_KNOCKBACK_RESISTANCE) == 0.8, "gyro");
        check(GearAbilities.oreName(org.bukkit.Material.DIAMOND_ORE) != null && GearAbilities.oreName(org.bukkit.Material.STONE) == null, "ore table");
        check(GearAbilities.hostile(null) == false, "hostile null-safe");
    }

    private static boolean close(Double v, double expect) { return v != null && Math.abs(v - expect) < 1e-9; }

    private void menu() {
        GearProfile prof = new GearProfile(UUID.randomUUID());
        prof.slots[4] = GearItems.create(GearItem.THERMAL_GOGGLES);
        Inventory inv = Bukkit.createInventory(null, 9, "Survivor Gear");
        plugin.render(inv, prof);
        check(GearItems.identify(inv.getItem(4)) == GearItem.THERMAL_GOGGLES, "menu shows worn");
        for (int i = 0; i < GearType.SLOT_COUNT; i++) if (i != 4) check(GearItems.isIcon(inv.getItem(i)), "menu icon " + i);
        check(inv.getItem(4) != prof.slots[4], "menu shows a copy");
    }

    private void wire() {
        GearProfile prof = new GearProfile(UUID.randomUUID());
        prof.slots[1] = GearItems.create(GearItem.TRITIUM_RING);
        String json = GearPlugin.stateJson(prof);
        com.google.gson.JsonObject root = new com.google.gson.JsonParser().parse(json).getAsJsonObject();
        check(root.get("v").getAsInt() == GearPlugin.PROTOCOL && root.getAsJsonArray("slots").size() == 7, "state shape");
        check(root.getAsJsonArray("slots").get(1).getAsString().contains("tritium_ring"), "state carries item");
        check(root.getAsJsonArray("slots").get(0).getAsString().isEmpty(), "empty slot is empty string");
        check(json.length() < 30000, "state size");
        byte[] hello = varString("hello 1");
        check("hello 1".equals(GearPlugin.decode(hello)), "decode hello");
        byte[] bad = hello.clone();
        bad[0] = 99;
        check(GearPlugin.decode(bad) == null && GearPlugin.decode(new byte[0]) == null && GearPlugin.decode(new byte[400]) == null, "decode rejects");
    }

    private void loot() {
        for (int tier = -1; tier <= 6; tier++) {
            java.util.Random a = new java.util.Random(4242L + tier), b = new java.util.Random(4242L + tier);
            int hits = 0, n = 60000, t = Math.max(0, Math.min(5, tier));
            int[] ranks = new int[6];
            for (int i = 0; i < n; i++) {
                GearItem x = GearApi.pickLoot(a, tier), y = GearApi.pickLoot(b, tier);
                if (x != y) { check(false, "loot determinism tier " + tier); break; }
                if (x == null) continue;
                hits++;
                ranks[x.rank]++;
                if (!GearApi.eligible(x.rank, t)) { check(false, "ineligible rank " + x.rank + " at tier " + tier); break; }
            }
            double rate = hits / (double) n, expect = GearApi.CHANCE[t];
            check(Math.abs(rate - expect) < 4 * Math.sqrt(expect * (1 - expect) / n) + 0.0005, "loot rate tier " + tier + " = " + rate);
            check(t >= 5 ? ranks[5] > 0 : ranks[5] == 0, "rank 5 gating tier " + tier);
            check(t >= 4 ? ranks[4] > 0 : ranks[4] == 0, "rank 4 gating tier " + tier);
            check(t >= 3 ? ranks[3] > 0 : ranks[3] == 0, "rank 3 gating tier " + tier);
            check(ranks[1] > 0 && ranks[2] > 0, "utility everywhere tier " + tier);
        }
        check(GearApi.pickLoot(null, 5) == null, "null random");
        for (int t = 0; t < 6; t++) check(GearApi.CHANCE[t] > 0 && GearApi.CHANCE[t] <= 0.05 && (t == 0 || GearApi.CHANCE[t] > GearApi.CHANCE[t - 1]),
            "very small chance per chest, rising with difficulty: tier " + t);
        ItemStack loot = null;
        java.util.Random r = new java.util.Random(7L);
        for (int i = 0; i < 400 && loot == null; i++) { ItemStack x = GearApi.rollLoot(r, 5); if (GearApi.isGear(x)) loot = x; }
        check(loot != null && GearApi.isGear(loot), "rollLoot returns real gear");
        for (GearItem item : GearItem.values()) {
            check(GearApi.isGear(GearApi.create(item.id)) && item.id.equals(GearApi.gearId(GearApi.create(item.id))), "api create " + item.id);
            check(GearApi.rank(item.id) == item.rank, "api rank " + item.id);
        }
        check(GearApi.create("nope") == null && GearApi.rank("nope") == 0 && !GearApi.isGear(null), "api unknown");
        check(GearApi.ids().size() == GearItem.values().length, "api ids");
    }

    private void bank() {
        check(GearPlugin.pointsForLevel(30) == GearPlugin.BANK_CAP, "30 levels = bank cap");
        check(GearPlugin.pointsForLevel(16) == 352 && GearPlugin.pointsForLevel(31) == 1507, "vanilla xp curve");
        ItemStack journal = GearItems.withStoredXp(GearItems.create(GearItem.FIELD_JOURNAL), 500);
        check(GearItems.identify(journal) == GearItem.FIELD_JOURNAL && GearItems.storedXp(journal) == 500, "journal stores xp");
        java.util.List<String> lore = journal.getItemMeta().getLore();
        check(lore != null && lore.get(lore.size() - 1).endsWith("Stored: 500 XP"), "journal lore shows balance");
        ItemStack emptied = GearItems.withStoredXp(journal, 0);
        check(GearItems.storedXp(emptied) == 0 && !emptied.getItemMeta().getLore().get(emptied.getItemMeta().getLore().size() - 1).contains("Stored"), "journal empties");
        check(GearItems.storedXp(GearItems.create(GearItem.RIOT_VEST)) == 0, "non-journal has no bank");
    }

    // ================================================================ Phase 2

    private void supplies() throws Exception {
        java.util.Set<Integer> models = new java.util.HashSet<Integer>();
        for (GearItem g : GearItem.values()) models.add(g.model);
        for (GearConsumable c : GearConsumable.values()) {
            ItemStack stack = GearItems.create(c);
            check(GearItems.consumable(stack) == c, "consumable identify " + c.id);
            check(GearItems.identify(stack) == null && !GearItems.isIcon(stack) && !GearApi.isGear(stack), "consumable is not gear " + c.id);
            check(GearApi.isConsumable(stack) && GearApi.create(c.id) != null && GearItems.consumable(GearApi.create(c.id)) == c, "api consumable " + c.id);
            check(stack.getType() == org.bukkit.Material.STONE_HOE && stack.getDurability() == c.model, "consumable model " + c.id);
            check(models.add(c.model) && c.model >= 16 && c.model < GearItems.ICON_BASE_MODEL, "unique consumable model " + c.id);
            check(stack.getItemMeta().isUnbreakable(), "consumable unbreakable " + c.id);
            ItemStack back = GearItems.fromSnbt(GearItems.toSnbt(stack));
            check(back != null && GearItems.consumable(back) == c && back.isSimilar(stack), "consumable snbt " + c.id);
            check(GearItems.doses(stack) == c.doses, "full doses " + c.id);
            ItemStack cur = stack;
            for (int left = c.doses - 1; left >= 1; left--) {
                cur = GearItems.afterDose(cur);
                check(cur != null && GearItems.consumable(cur) == c && GearItems.doses(cur) == left, "dose " + left + " " + c.id);
                java.util.List<String> lore = cur.getItemMeta().getLore();
                check(lore.get(lore.size() - 1).endsWith("Doses: " + left + "/" + c.doses), "dose lore " + c.id);
            }
            check(GearItems.afterDose(cur) == null, "last dose used up " + c.id);
            check(c.effects.length >= 1 && c.effects.length <= 3, "consumable lore size " + c.id);
            check(c.minTier >= 0 && c.minTier <= 5 && c.weight(c.minTier) > 0 && (c.minTier == 0 || c.weight(c.minTier - 1) == 0), "tier gate " + c.id);
        }
        ItemStack plain = new ItemStack(org.bukkit.Material.STONE_HOE, 1, (short) 16);
        check(GearItems.consumable(plain) == null && GearItems.doses(plain) == 0, "untagged hoe is not a consumable");
        check(GearItems.consumable(GearItems.create(GearItem.RIOT_VEST)) == null, "gear is not a consumable");
        int recipes = 0;
        for (GearConsumable c : GearConsumable.values()) {
            if (c.shape == null) continue;
            boolean found = false;
            for (org.bukkit.inventory.Recipe r : Bukkit.getRecipesFor(GearItems.create(c)))
                if (GearItems.consumable(r.getResult()) == c) found = true;
            check(found, "consumable recipe " + c.id);
            recipes++;
        }
        check(recipes == 3, "three supply recipes (candy, bandage, purge serum)");
        check(GearConsumable.byId("ADRENALINE_CANDY") == GearConsumable.ADRENALINE_CANDY && GearConsumable.byId("nope") == null, "consumable byId");
    }

    /** The Phase 1 roll, verbatim. Its trinket band has since shrunk to a prefix, so every seed that rolls a
     *  trinket now must have rolled the same trinket then (and nothing may appear that was not there). */
    private static GearItem legacyRoll(java.util.Random random, int tier) {
        double[] chance = {0.03, 0.05, 0.08, 0.12, 0.18, 0.25};
        int t = Math.max(0, Math.min(5, tier));
        if (random.nextDouble() >= chance[t]) return null;
        List<GearItem> pool = new ArrayList<GearItem>();
        List<Integer> weights = new ArrayList<Integer>();
        int total = 0;
        for (GearItem item : GearItem.values()) {
            if (!GearApi.eligible(item.rank, t)) continue;
            int w = GearApi.lootWeight(item.rank, t);
            pool.add(item); weights.add(w); total += w;
        }
        int roll = random.nextInt(total);
        for (int i = 0; i < pool.size(); i++) { roll -= weights.get(i); if (roll < 0) return pool.get(i); }
        return pool.get(pool.size() - 1);
    }

    private void lootParity() {
        for (int tier = 0; tier <= 5; tier++) {
            int mismatch = 0, supplies = 0, n = 40000;
            int[] byItem = new int[GearConsumable.values().length];
            for (int seed = 0; seed < n; seed++) {
                java.util.Random a = new java.util.Random(seed * 31L + tier), b = new java.util.Random(seed * 31L + tier);
                GearItem old = legacyRoll(a, tier);
                Object now = GearApi.pickAny(b, tier);
                if (now instanceof GearItem && now != old) mismatch++;
                if (now instanceof GearItem && a.nextLong() != b.nextLong()) mismatch++; // same draws consumed when a trinket rolls
                if (now instanceof GearConsumable) { supplies++; byItem[((GearConsumable) now).ordinal()]++; }
            }
            check(mismatch == 0, "phase 1 trinket parity tier " + tier + " mismatches=" + mismatch);
            double rate = supplies / (double) n, expect = GearApi.SUPPLY_CHANCE[tier];
            check(Math.abs(rate - expect) < 0.012, "supply rate tier " + tier + " = " + rate);
            for (GearConsumable c : GearConsumable.values())
                check(tier >= c.minTier ? byItem[c.ordinal()] > 0 : byItem[c.ordinal()] == 0, "supply gating " + c.id + " tier " + tier);
            if (tier == 5) check(byItem[GearConsumable.ADRENALINE_CRYSTAL.ordinal()] < byItem[GearConsumable.ADRENALINE_CANDY.ordinal()], "crystal rarest");
        }
        java.util.Random r = new java.util.Random(11L);
        ItemStack supply = null;
        for (int i = 0; i < 400 && supply == null; i++) { ItemStack x = GearApi.rollLoot(r, 5); if (GearApi.isConsumable(x)) supply = x; }
        check(supply != null, "rollLoot returns real consumables");
        check(GearApi.consumableIds().size() == GearConsumable.values().length, "api consumable ids");
    }

    private void vitals() {
        GearProfile prof = new GearProfile(UUID.randomUUID());
        check(prof.maxAdrenaline() == 100, "base max 100");
        prof.adrenaline = 50;
        check(close(GearVitals.gain(prof, 30), 30) && close(prof.adrenaline, 80), "gain");
        check(close(GearVitals.gain(prof, 500), 20) && close(prof.adrenaline, 100), "gain caps at max");
        check(close(GearVitals.gain(prof, -500), -100) && close(prof.adrenaline, 0), "never negative");
        prof.crystals = 3;
        check(prof.maxAdrenaline() == 130, "crystals raise max");
        prof.crystals = 99;
        check(prof.maxAdrenaline() == GearVitals.BASE_MAX + GearVitals.CRYSTAL_BONUS * GearVitals.MAX_CRYSTALS, "crystal cap");
        long now = System.currentTimeMillis();
        prof.status.put(GearStatus.BLEED, now + 3000);
        prof.status.put(GearStatus.PARALYSIS, now - 1);
        check(prof.has(GearStatus.BLEED, now) && !prof.has(GearStatus.PARALYSIS, now) && !prof.has(GearStatus.INVIGORATED, now), "status expiry");
        check(GearVitals.COST_ARC > GearVitals.COST_DODGE && GearVitals.COST_BLINK >= GearVitals.COST_ARC && GearVitals.COST_MAGNET < GearVitals.COST_CHEST, "cost order");
        check(GearVitals.COST_BLINK <= GearVitals.BASE_MAX && GearVitals.REGEN_PER_SECOND > 0, "abilities affordable");
        check(GearStatus.PARALYSIS.maxMs <= 3000 && GearStatus.PARALYSIS.harmful && GearStatus.BLEED.harmful && !GearStatus.INVIGORATED.harmful, "status table");
        check(GearStatus.byId("volt") == GearStatus.LIGHTNING_RESISTANCE && GearStatus.byId("lightning_resistance") == GearStatus.LIGHTNING_RESISTANCE && GearStatus.byId("x") == null, "status ids");
        java.util.Set<String> ids = new java.util.HashSet<String>();
        for (GearStatus s : GearStatus.values()) check(ids.add(s.id) && s.id.length() <= 8, "status id " + s.id);
        GearProfile parked = new GearProfile(UUID.randomUUID());
        parked.adrenaline = 10;
        parked.status.put(GearStatus.ICE_RESISTANCE, now + 50_000);
        plugin.vitals.suspend(parked, now);
        check(parked.status.isEmpty() && parked.parked.get(GearStatus.ICE_RESISTANCE) == 50_000L, "offline time parked");
        plugin.vitals.resume(parked, now + 3_600_000L);
        check(parked.parked.isEmpty() && parked.status.get(GearStatus.ICE_RESISTANCE) == now + 3_600_000L + 50_000L, "resumed after offline hour");
    }

    private void vitalsStore() throws Exception {
        File dir = new File(plugin.getDataFolder(), "selftest-vitals-" + System.nanoTime());
        GearStore store = new GearStore(dir, plugin.getLogger());
        long now = System.currentTimeMillis();
        GearProfile a = new GearProfile(UUID.randomUUID());
        a.crystals = 2;
        a.adrenaline = 73.25;
        a.status.put(GearStatus.INVIGORATED, now + 40_000);
        a.status.put(GearStatus.BLEED, now - 5); // expired: not written
        a.parked.put(GearStatus.ICE_RESISTANCE, 12_000L);
        store.saveVitalsNow(a);
        check(store.vitalsFile(a.uuid).isFile() && !new File(dir, a.uuid + ".vitals.tmp").exists(), "vitals atomic write");
        check(!store.file(a.uuid).exists(), "vitals never touch the gear file");
        GearProfile b = new GearProfile(a.uuid);
        store.loadVitals(b, now);
        check(b.crystals == 2 && close(b.adrenaline, 73.25) && b.maxAdrenaline() == 120, "vitals round trip");
        Long vigor = b.status.get(GearStatus.INVIGORATED), ice = b.status.get(GearStatus.ICE_RESISTANCE);
        check(vigor != null && Math.abs(vigor - (now + 40_000)) < 2000 && ice != null && ice == now + 12_000L && !b.status.containsKey(GearStatus.BLEED), "status time round trip");
        GearProfile fresh = new GearProfile(UUID.randomUUID());
        store.loadVitals(fresh, now);
        check(close(fresh.adrenaline, 100) && fresh.crystals == 0 && fresh.status.isEmpty(), "new survivor starts full");
        Files.write(store.vitalsFile(fresh.uuid).toPath(), "jaspr-vitals 1\nadrenaline=5\nfuture.key=1\nstatus.unknown=9\nstatus.para=999999\n".getBytes(StandardCharsets.UTF_8));
        store.loadVitals(fresh, now);
        check(close(fresh.adrenaline, 5) && fresh.status.get(GearStatus.PARALYSIS) == now + GearStatus.PARALYSIS.maxMs, "unknown keys ignored, durations capped");
        Files.write(store.vitalsFile(fresh.uuid).toPath(), "garbage".getBytes(StandardCharsets.UTF_8));
        store.loadVitals(fresh, now);
        File[] aside = dir.listFiles((d, n) -> n.startsWith(fresh.uuid + ".vitals.corrupt-"));
        check(close(fresh.adrenaline, 100) && aside != null && aside.length == 1, "corrupt vitals preserved aside");
        GearProfile unloaded = new GearProfile(UUID.randomUUID());
        store.saveVitalsAsync(unloaded);
        store.flush();
        check(!store.vitalsFile(unloaded.uuid).exists(), "never-loaded vitals are not written");
        for (File f : dir.listFiles()) Files.deleteIfExists(f.toPath());
        Files.deleteIfExists(dir.toPath());
    }

    private void hud() {
        GearProfile prof = new GearProfile(UUID.randomUUID());
        prof.adrenaline = 42.9;
        prof.crystals = 1;
        long now = System.currentTimeMillis();
        prof.status.put(GearStatus.BLEED, now + 4200);
        prof.status.put(GearStatus.ICE_RESISTANCE, now - 10);
        String json = GearVitals.hudJson(null, prof, now);
        com.google.gson.JsonObject root = new com.google.gson.JsonParser().parse(json).getAsJsonObject();
        check("hud".equals(root.get("t").getAsString()) && root.get("v").getAsInt() == GearPlugin.PROTOCOL, "hud type");
        check(root.get("a").getAsInt() == 42 && root.get("m").getAsInt() == 110 && root.get("on").getAsBoolean(), "hud numbers");
        com.google.gson.JsonArray fx = root.getAsJsonArray("fx");
        check(fx.size() == 1 && "bleed".equals(fx.get(0).getAsJsonArray().get(0).getAsString()) && fx.get(0).getAsJsonArray().get(1).getAsInt() == 5, "hud statuses");
        check(!root.has("slots") && json.length() < 400, "hud packet is small and never a slot packet");
        check("hello 2".equals(GearPlugin.decode(varString("hello 2"))), "decode hello 2");
        check("click 3 0 1".equals(GearPlugin.decode(varString("click 3 0 1"))), "decode click");
    }

    // ================================================================ Phase 3

    private void mutations() throws Exception {
        check(GearMutation.values().length == 9 && GearMutation.values()[0] == GearMutation.BASELINE, "nine races, baseline first");
        java.util.Set<UUID> uuids = new java.util.HashSet<UUID>();
        java.util.Set<String> ids = new java.util.HashSet<String>();
        int serums = 0;
        for (GearMutation m : GearMutation.values()) {
            check(ids.add(m.id) && GearMutation.byId(m.id) == m && GearMutation.byId(m.id.toUpperCase(java.util.Locale.ROOT)) == m, "mutation id " + m.id);
            check(m == GearMutation.BASELINE ? m.ability == null && m.modifiers().isEmpty() : m.ability != null && m.cost > 0 && m.cost <= GearVitals.BASE_MAX
                && m.cooldownMs >= 4000 && !m.modifiers().isEmpty(), "mutation shape " + m.id);
            check(m.effects.length >= 1 && m.effects.length <= 3 && m.title.length() <= 16, "mutation lore " + m.id);
            for (org.bukkit.attribute.AttributeModifier mod : m.modifiers().values()) check(uuids.add(mod.getUniqueId()), "unique modifier " + m.id + " " + mod.getName());
            if (m.modifiers().containsKey(Attribute.GENERIC_MAX_HEALTH))
                check(20 + m.modifiers().get(Attribute.GENERIC_MAX_HEALTH).getAmount() >= 12, "never below 6 hearts " + m.id);
        }
        for (GearConsumable c : GearConsumable.values()) {
            if (c.mutation == null) continue;
            serums++;
            ItemStack stack = GearItems.create(c);
            check(GearItems.consumable(stack) == c && GearItems.identify(stack) == null && c.doses == 1, "serum item " + c.id);
            check(c.mutation == GearMutation.BASELINE ? c == GearConsumable.PURGE_SERUM : c.id.equals("mutagen_" + c.mutation.id), "serum maps to mutation " + c.id);
            check(c.weight(5) == c.lootWeight && c.weight(c.minTier - 1) == 0, "serum flat rare weight " + c.id);
        }
        check(serums == 9, "8 mutagens + purge serum");
        check(GearMutation.byId("nope") == null, "unknown mutation");
        // Persistence: the mutation and the worn-gear visibility ride in the vitals file.
        File dir = new File(plugin.getDataFolder(), "selftest-mutation-" + System.nanoTime());
        GearStore store = new GearStore(dir, plugin.getLogger());
        GearProfile a = new GearProfile(UUID.randomUUID());
        a.adrenaline = 50;
        a.mutation = GearMutation.WYRM;
        a.showWorn = false;
        store.saveVitalsNow(a);
        GearProfile b = new GearProfile(a.uuid);
        store.loadVitals(b, System.currentTimeMillis());
        check(b.mutation == GearMutation.WYRM && !b.showWorn, "mutation persists");
        Files.write(store.vitalsFile(a.uuid).toPath(), "jaspr-vitals 1\nadrenaline=5\nmutation=martian\n".getBytes(StandardCharsets.UTF_8));
        store.loadVitals(b, System.currentTimeMillis());
        check(b.mutation == GearMutation.BASELINE && b.showWorn, "unknown mutation falls back to baseline");
        for (File f : dir.listFiles()) Files.deleteIfExists(f.toPath());
        Files.deleteIfExists(dir.toPath());
        GearProfile hudProf = new GearProfile(UUID.randomUUID());
        hudProf.adrenaline = 10;
        hudProf.mutation = GearMutation.FERAL;
        check(GearVitals.hudJson(null, hudProf, System.currentTimeMillis()).contains("\"mu\":\"Feral\""), "hud carries the mutation");
        hudProf.mutation = GearMutation.BASELINE;
        check(!GearVitals.hudJson(null, hudProf, System.currentTimeMillis()).contains("\"mu\""), "baseline sends no mutation");
    }

    private static byte[] varString(String s) {
        byte[] body = s.getBytes(StandardCharsets.UTF_8);
        byte[] out = new byte[body.length + 1];
        out[0] = (byte) body.length;
        System.arraycopy(body, 0, out, 1, body.length);
        return out;
    }

    /** 3.2.0 backpacks: sizes, identity, recipe prices, loot band, storage round trip, vanilla loot fill. */
    private void backpacks() throws Exception {
        GearBackpack[] all = GearBackpack.values();
        check(all.length == 5, "five backpack tiers");
        check(GearBackpack.SATCHEL.slots() == 18, "tier 1 = 18 slots, half the 36-slot inventory");
        int weights = 0;
        java.util.Map<String, Integer> value = new java.util.HashMap<String, Integer>();
        value.put("LEATHER", 1); value.put("STRING", 1); value.put("CHEST", 2); value.put("IRON_INGOT", 3); value.put("GOLD_INGOT", 4);
        value.put("DIAMOND", 10); value.put("IRON_BLOCK", 27); value.put("GOLD_BLOCK", 36); value.put("SHULKER_SHELL", 40); value.put("DIAMOND_BLOCK", 90);
        int lastCost = 0;
        for (int i = 0; i < all.length; i++) {
            GearBackpack b = all[i];
            weights += b.lootWeight;
            check(b.tier == i + 1 && b.slots() == 18 + 9 * i && b.slots() <= 54, "size by tier " + b.id);
            check(i == 0 || b.lootWeight < all[i - 1].lootWeight, "higher tier rarer in chests " + b.id);
            check(GearBackpack.byId(b.id) == b && GearItem.byId(b.id) == null && GearConsumable.byId(b.id) == null, "unique id " + b.id);
            int cost = 0, leather = 0;
            boolean known = true;
            java.util.Map<Character, String> key = b.ingredientMap();
            for (String row : b.shape) for (char ch : row.toCharArray()) {
                String m = key.get(ch);
                if (m == null) continue;
                if (!value.containsKey(m)) { known = false; continue; }
                cost += value.get(m);
                if (m.equals("LEATHER")) leather++;
            }
            check(known && leather >= 4, "leather backpack recipe " + b.id);
            check(i > 0 || cost <= 8, "tier 1 is cheap (leather and string) " + cost);
            check(cost > lastCost, "recipe cost rises with tier " + b.id + " " + cost);
            lastCost = cost;
            ItemStack item = GearItems.create(b);
            check(GearItems.backpack(item) == b && GearItems.identify(item) == null && GearItems.consumable(item) == null
                && !GearItems.isIcon(item), "backpack identity " + b.id);
            check(GearItems.packUuid(item) == null, "canonical backpack has no uuid " + b.id);
            UUID u = UUID.randomUUID();
            ItemStack stamped = GearItems.withPackUuid(item, u);
            check(u.equals(GearItems.packUuid(stamped)) && GearItems.backpack(stamped) == b, "uuid stamp " + b.id);
            check(item.getType().getMaxStackSize() == 1, "backpacks never stack");
            check(GearApi.isBackpack(GearApi.create(b.id)) && GearApi.backpackIds().contains(b.id), "api backpack " + b.id);
            boolean found = false;
            for (org.bukkit.inventory.Recipe r : Bukkit.getRecipesFor(item)) if (GearItems.backpack(r.getResult()) == b) found = true;
            check(found, "backpack recipe registered " + b.id);
        }
        check(weights == 100, "backpack loot weights are percentages");
        check(plugin.packRecipeCount() == all.length, "backpack recipes " + plugin.packRecipeCount());
        // No other recipe shares a backpack pattern.
        java.util.Map<String, String> mine = new java.util.HashMap<String, String>();
        java.util.List<org.bukkit.inventory.ShapedRecipe> others = new ArrayList<org.bukkit.inventory.ShapedRecipe>();
        for (java.util.Iterator<org.bukkit.inventory.Recipe> it = Bukkit.recipeIterator(); it.hasNext(); ) {
            org.bukkit.inventory.Recipe r = it.next();
            if (!(r instanceof org.bukkit.inventory.ShapedRecipe)) continue;
            GearBackpack b = GearItems.backpack(r.getResult());
            if (b != null) mine.put(b.id, pattern((org.bukkit.inventory.ShapedRecipe) r, false));
            else others.add((org.bukkit.inventory.ShapedRecipe) r);
        }
        int clashes = 0;
        for (org.bukkit.inventory.ShapedRecipe o : others)
            for (java.util.Map.Entry<String, String> m : mine.entrySet())
                if (samePattern(m.getValue(), pattern(o, false)) || samePattern(m.getValue(), pattern(o, true))) {
                    clashes++; failures.add("backpack recipe clash " + m.getKey() + " vs " + o.getResult().getType());
                }
        check(mine.size() == all.length && clashes == 0, "no other recipe shares a backpack pattern");
        // Loot: a band above trinkets and supplies; the lower bands keep every draw.
        for (int tier = 0; tier <= 5; tier++) {
            int n = 60000, packs = 0, outside = 0;
            int[] byTier = new int[6];
            double low = GearApi.CHANCE[tier] + GearApi.SUPPLY_CHANCE[tier];
            for (int seed = 0; seed < n; seed++) {
                java.util.Random a = new java.util.Random(seed * 7919L + tier), b = new java.util.Random(seed * 7919L + tier);
                double d = a.nextDouble();
                Object pick = GearApi.pickAny(b, tier);
                if (!(pick instanceof GearBackpack)) continue;
                packs++;
                byTier[((GearBackpack) pick).tier]++;
                if (d < low || d >= low + GearApi.BACKPACK_CHANCE[tier]) outside++;
            }
            double rate = packs / (double) n, expect = GearApi.BACKPACK_CHANCE[tier];
            check(Math.abs(rate - expect) < 4 * Math.sqrt(expect * (1 - expect) / n) + 0.0005, "backpack rate tier " + tier + " = " + rate);
            check(outside == 0, "backpacks only in their own band tier " + tier);
            check(byTier[1] > byTier[2] && byTier[2] > byTier[3] && byTier[3] > byTier[4] && byTier[4] > byTier[5] && byTier[5] > 0,
                "higher-tier backpacks rarer, all possible, tier " + tier);
            check(tier == 0 || GearApi.BACKPACK_CHANCE[tier] >= GearApi.BACKPACK_CHANCE[tier - 1], "backpack chance never falls with difficulty");
        }
        ItemStack rolled = null;
        java.util.Random r = new java.util.Random(99L);
        for (int i = 0; i < 2000 && rolled == null; i++) { ItemStack x = GearApi.rollLoot(r, 0); if (GearApi.isBackpack(x)) rolled = x; }
        check(rolled != null && GearItems.packUuid(rolled) == null, "rollLoot returns real, unopened backpacks");
        // Storage round trip, including a corrupt file (moved aside, never deleted).
        GearBackpacks packs = plugin.backpacks;
        UUID id = UUID.randomUUID();
        Inventory inv = packs.inventory(id, GearBackpack.RUCKSACK);
        check(inv != null && inv.getSize() == 27 && GearBackpacks.isPack(inv), "fresh rucksack window 27");
        inv.setItem(0, new ItemStack(org.bukkit.Material.COBBLESTONE, 64));
        inv.setItem(26, GearItems.create(GearItem.RAZOR_CLAWS));
        java.io.File dir = packs.dir;
        dir.mkdirs();
        Files.write(packs.file(id).toPath(), GearBackpacks.encodeText(inv.getContents()).getBytes(StandardCharsets.UTF_8));
        ItemStack[] back = packs.load(id);
        check(back[0] != null && back[0].getAmount() == 64 && GearItems.identify(back[26]) == GearItem.RAZOR_CLAWS && back[1] == null, "pack file round trip");
        check(packs.inventory(id, GearBackpack.RUCKSACK) == inv, "one live inventory per backpack");
        UUID bad = UUID.randomUUID();
        Files.write(packs.file(bad).toPath(), "garbage".getBytes(StandardCharsets.UTF_8));
        Inventory fresh = packs.inventory(bad, GearBackpack.SATCHEL);
        java.io.File[] aside = dir.listFiles((d, name) -> name.startsWith(bad + ".pack.corrupt-"));
        check(fresh != null && fresh.firstEmpty() == 0 && aside != null && aside.length == 1, "corrupt pack moved aside");
        if (aside != null) for (java.io.File f : aside) f.delete();
        packs.file(id).delete();
        packs.forget(id);
        packs.forget(bad);
        // Vanilla loot-table fill: one roll into an empty slot of the live inventory.
        final double hit = GearApi.CHANCE[GearBackpacks.VANILLA_TIER] + GearApi.SUPPLY_CHANCE[GearBackpacks.VANILLA_TIER] + 0.001;
        java.util.Random forced = new java.util.Random(5L) { @Override public double nextDouble() { return hit; } };
        Inventory chest = Bukkit.createInventory(null, 27);
        chest.setItem(0, new ItemStack(org.bukkit.Material.BREAD, 3));
        ItemStack added = packs.vanillaFill(chest, forced);
        int count = 0;
        for (ItemStack x : chest.getContents()) if (GearApi.isBackpack(x)) count++;
        check(GearApi.isBackpack(added) && count == 1 && chest.getItem(0).getType() == org.bukkit.Material.BREAD, "vanilla loot chest gets a backpack roll");
        Inventory full = Bukkit.createInventory(null, 9);
        for (int i = 0; i < 9; i++) full.setItem(i, new ItemStack(org.bukkit.Material.DIRT));
        check(packs.vanillaFill(full, forced) == null, "full vanilla chest left alone");
    }
}
