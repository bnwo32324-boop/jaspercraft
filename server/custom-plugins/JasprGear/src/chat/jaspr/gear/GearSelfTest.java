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
 * panel wire format. Run with -Djaspr.gear.selftest=true or "gear selftest" on the console.
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
        for (GearItem item : GearItem.values()) {
            boolean found = false;
            for (org.bukkit.inventory.Recipe r : Bukkit.getRecipesFor(GearItems.create(item)))
                if (GearItems.identify(r.getResult()) == item) found = true;
            check(found, "recipe lookup " + item.id);
            for (String ingredient : item.ingredientMap().values())
                check(!ingredient.startsWith("STONE_HOE"), "no gear/carrier ingredient " + item.id);
        }
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
            int hits = 0, n = 20000, t = Math.max(0, Math.min(5, tier));
            int[] ranks = new int[6];
            for (int i = 0; i < n; i++) {
                GearItem x = GearApi.pickLoot(a, tier), y = GearApi.pickLoot(b, tier);
                if (x != y) { check(false, "loot determinism tier " + tier); break; }
                if (x == null) continue;
                hits++;
                ranks[x.rank]++;
                if (!GearApi.eligible(x.rank, t)) { check(false, "ineligible rank " + x.rank + " at tier " + tier); break; }
            }
            double rate = hits / (double) n, expect = new double[]{0.03, 0.05, 0.08, 0.12, 0.18, 0.25}[t];
            check(Math.abs(rate - expect) < 0.015, "loot rate tier " + tier + " = " + rate);
            check(t >= 5 ? ranks[5] > 0 : ranks[5] == 0, "rank 5 gating tier " + tier);
            check(t >= 4 ? ranks[4] > 0 : ranks[4] == 0, "rank 4 gating tier " + tier);
            check(t >= 3 ? ranks[3] > 0 : ranks[3] == 0, "rank 3 gating tier " + tier);
            check(ranks[1] > 0 && ranks[2] > 0, "utility everywhere tier " + tier);
        }
        check(GearApi.pickLoot(null, 5) == null, "null random");
        ItemStack loot = null;
        java.util.Random r = new java.util.Random(7L);
        for (int i = 0; i < 200 && loot == null; i++) loot = GearApi.rollLoot(r, 5);
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

    private static byte[] varString(String s) {
        byte[] body = s.getBytes(StandardCharsets.UTF_8);
        byte[] out = new byte[body.length + 1];
        out[0] = (byte) body.length;
        System.arraycopy(body, 0, out, 1, body.length);
        return out;
    }
}
