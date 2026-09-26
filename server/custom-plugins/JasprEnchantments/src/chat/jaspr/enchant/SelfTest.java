package chat.jaspr.enchant;

import chat.jaspr.enchant.data.SmeTable;
import chat.jaspr.enchant.fx.Enchanting;
import chat.jaspr.enchant.fx.TableFx;
import chat.jaspr.enchant.fx.TempBlocks;
import chat.jaspr.enchant.fx.Upgrading;
import chat.jaspr.enchant.nms.Registrar;
import chat.jaspr.enchant.nms.SmeCraftEnchantment;
import chat.jaspr.enchant.nms.SmeEnchantment;
import chat.jaspr.enchant.nms.SmeSlotType;
import chat.jaspr.enchant.util.Log;
import chat.jaspr.enchant.util.Nms;
import net.minecraft.server.v1_12_R1.BlockPosition;
import net.minecraft.server.v1_12_R1.Blocks;
import net.minecraft.server.v1_12_R1.Enchantment;
import net.minecraft.server.v1_12_R1.EnchantmentManager;
import net.minecraft.server.v1_12_R1.Enchantments;
import net.minecraft.server.v1_12_R1.EntityLiving;
import net.minecraft.server.v1_12_R1.EntityMonster;
import net.minecraft.server.v1_12_R1.EnumMonsterType;
import net.minecraft.server.v1_12_R1.ItemEnchantedBook;
import net.minecraft.server.v1_12_R1.ItemStack;
import net.minecraft.server.v1_12_R1.Items;
import net.minecraft.server.v1_12_R1.MinecraftKey;
import net.minecraft.server.v1_12_R1.MobEffect;
import net.minecraft.server.v1_12_R1.MobEffects;
import net.minecraft.server.v1_12_R1.NBTCompressedStreamTools;
import net.minecraft.server.v1_12_R1.NBTTagCompound;
import net.minecraft.server.v1_12_R1.WeightedRandomEnchant;
import net.minecraft.server.v1_12_R1.WorldServer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.craftbukkit.v1_12_R1.CraftWorld;
import org.bukkit.craftbukkit.v1_12_R1.inventory.CraftItemStack;
import org.bukkit.entity.Cow;
import org.bukkit.entity.Husk;
import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * -Djaspr.sme.selftest=true : registry, persistence, enchanting-table/loot rules, API and a set of real
 * mob-vs-mob combat effects driven through vanilla EntityMonster.attackEntityAsMob. Logs SME_TEST PASS/FAIL.
 */
final class SelfTest implements Runnable {
    private final JavaPlugin plugin;
    private int pass = 0, fail = 0;
    private final List<Runnable> steps = new ArrayList<>();
    private final List<LivingEntity> spawned = new ArrayList<>();
    private World world;
    private Location base;

    SelfTest(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    private void check(String name, boolean ok, String detail) {
        if (ok) pass++;
        else fail++;
        Log.info("SME_TEST " + (ok ? "PASS " : "FAIL ") + name + (detail == null ? "" : " " + detail));
    }

    private void step(Runnable r) {
        steps.add(r);
    }

    @Override
    public void run() {
        try {
            staticTests();
        } catch (Throwable t) {
            check("static", false, t.toString());
        }
        world = Bukkit.getWorlds().get(0);
        world.setTime(18000);
        base = world.getSpawnLocation().clone();
        base.setY(200);
        base.setYaw(0);
        base.setPitch(0);
        world.getChunkAt(base).load();
        combatSteps();
        step(this::chestPersistence);
        step(() -> {
            for (LivingEntity e : spawned) e.remove();
            Log.info("SME_SELFTEST_DONE pass=" + pass + " fail=" + fail + " errors=" + Log.errorCount());
        });
        runSteps(0);
    }

    private void runSteps(int i) {
        if (i >= steps.size()) return;
        try {
            steps.get(i).run();
        } catch (Throwable t) {
            check("step" + i, false, t.getClass().getSimpleName() + ": " + t.getMessage());
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> runSteps(i + 1), 3L);
    }

    // ================================================================== static tests

    private void staticTests() throws Exception {
        // registry: NMS + Bukkit, fixed ids, keys
        int nmsOk = 0, bukkitOk = 0, keyOk = 0;
        for (int i = 0; i < SmeTable.ALL.length; i++) {
            int id = 72 + i;
            Enchantment e = Enchantment.c(id);
            if (e instanceof SmeEnchantment && ((SmeEnchantment) e).def.regName.equals(SmeTable.ALL[i].regName)) nmsOk++;
            org.bukkit.enchantments.Enchantment b = org.bukkit.enchantments.Enchantment.getById(id);
            if (b instanceof SmeCraftEnchantment && b.getName().equals("SME_" + SmeTable.ALL[i].regName.toUpperCase())
                    && org.bukkit.enchantments.Enchantment.getByName(b.getName()) == b) bukkitOk++;
            Enchantment byKey = Enchantment.enchantments.get(new MinecraftKey("somanyenchantments", SmeTable.ALL[i].regName));
            if (byKey == e && Enchantment.getId(e) == id) keyOk++;
        }
        check("registry.nms", nmsOk == 130, "count=" + nmsOk + " ids=72-201");
        check("registry.bukkit", bukkitOk == 130, "count=" + bukkitOk);
        check("registry.keys", keyOk == 130, "count=" + keyOk);
        check("registry.vanillaIntact", Enchantment.c(16) == Enchantments.DAMAGE_ALL && Enchantment.c(72) == E.ADEPT && Enchantment.c(201) == E.SUPREMESMITE, null);
        int exact = 0;
        for (SmeEnchantment e : Registrar.BY_INDEX) if (e.itemTarget instanceof SmeSlotType) exact++;
        check("registry.exactTargets", exact == 130, "customSlotTypes=" + exact);

        // values straight from the SME config
        check("data.levels", E.ADVANCEDSHARPNESS.getMaxLevel() == 5 && E.SUPREMEPROTECTION.getStartLevel() == 10 && E.MORTALITAS.getMaxLevel() == 8, null);
        check("data.enchantability", E.ADEPT.a(1) == 26 && E.ADEPT.b(1) == 66 && E.ARCSLASH.b(2) == 21 + 40
                && E.ANCIENTSEALEDCURSES.b(1) == 720 && E.SUPREMEPROTECTION.a(10) == 1000, null);
        check("data.treasureCurse", E.ADEPT.isTreasure() && !E.ARCSLASH.isTreasure() && E.RUSTED.isCursed() && E.RUSTED.isTreasure()
                && E.INSTABILITY.isCursed() && !E.INSTABILITY.isTreasure(), null);

        // incompatibility groups, matched like SME (substring line match, unresolved entries ignored)
        check("incompat.sharpness", !E.ADVANCEDSHARPNESS.c(Enchantments.DAMAGE_ALL) && !Enchantments.DAMAGE_ALL.c(E.ADVANCEDSHARPNESS), null);
        check("incompat.supremeGroup", !E.SUPREMESHARPNESS.c(E.PENETRATINGEDGE) && !E.SPELLBREAKER.c(E.SUPREMESMITE), null);
        check("incompat.compatible", E.LIFESTEAL.c(E.ADVANCEDSHARPNESS) && E.MAGMAWALKER.c(Enchantments.j), "magmawalker+frost_walker compatible as in SME");

        // item sets: table set via the custom target, anvil set via canApply
        ItemStack sword = new ItemStack(Items.DIAMOND_SWORD), axe = new ItemStack(Items.DIAMOND_AXE), bow = new ItemStack(Items.BOW);
        check("canApply.table", E.ADEPT.itemTarget.canEnchant(Items.BOW) && E.ADEPT.itemTarget.canEnchant(Items.DIAMOND_AXE)
                && !E.LIFESTEAL.itemTarget.canEnchant(Items.DIAMOND_AXE) && E.BRUTALITY.itemTarget.canEnchant(Items.DIAMOND_AXE)
                && !E.BRUTALITY.itemTarget.canEnchant(Items.DIAMOND_SWORD), null);
        check("canApply.anvil", E.ADVANCEDSHARPNESS.canEnchant(axe) && !E.ADVANCEDSHARPNESS.itemTarget.canEnchant(Items.DIAMOND_AXE)
                && !E.LIFESTEAL.canEnchant(axe) && E.LIFESTEAL.canEnchant(sword) && !E.UPGRADEDPOTENTIALS.canEnchant(sword)
                && E.CURSEOFDECAY.canEnchant(new ItemStack(Items.STICK)), null);
        List<WeightedRandomEnchant> axeCand = EnchantmentManager.a(30, axe, false);
        boolean hasCrit = false, hasLife = false;
        for (WeightedRandomEnchant w : axeCand) {
            if (w.enchantment == E.CRITICALSTRIKE) hasCrit = true;
            if (w.enchantment == E.LIFESTEAL) hasLife = true;
        }
        check("vanillaTable.candidates", hasCrit && !hasLife, "axe@30 criticalstrike=" + hasCrit + " lifesteal=" + hasLife);

        // enchanting table never offers blacklisted enchantments (and never SME curses on books)
        Random r = new Random(7);
        int rolls = 0, sme = 0, bad = 0, curseBooks = 0;
        ItemStack[] items = {sword, axe, bow, new ItemStack(Items.BOOK), new ItemStack(Items.DIAMOND_CHESTPLATE), new ItemStack(Items.DIAMOND_BOOTS),
                new ItemStack(Items.DIAMOND_PICKAXE), new ItemStack(Items.FISHING_ROD), new ItemStack(Items.GOLDEN_HELMET), new ItemStack(Items.IRON_HOE)};
        for (ItemStack it : items) {
            for (int cost = 1; cost <= 30; cost++) {
                for (int n = 0; n < 60; n++) {
                    for (WeightedRandomEnchant w : Enchanting.tableList(it, r.nextInt(), n % 3, cost)) {
                        rolls++;
                        if (w.enchantment instanceof SmeEnchantment) sme++;
                        if (Enchanting.TABLE_BLACKLIST.contains(w.enchantment)) bad++;
                        if (it.getItem() == Items.BOOK && w.enchantment instanceof SmeEnchantment && ((SmeEnchantment) w.enchantment).def.curse) curseBooks++;
                    }
                }
            }
        }
        check("table.blacklist", bad == 0 && sme > 0 && curseBooks == 0, "rolls=" + rolls + " sme=" + sme + " blacklisted=" + bad + " curseBooks=" + curseBooks);

        // API for other plugins (structure loot)
        int apiSme = 0, apiBad = 0;
        for (int i = 0; i < 4000; i++) {
            Map.Entry<org.bukkit.enchantments.Enchantment, Integer> e1 = EnchantApi.randomLootEnchantment(r, null, 5 + r.nextInt(35), true);
            Map.Entry<org.bukkit.enchantments.Enchantment, Integer> e2 = EnchantApi.randomUniformEnchantment(r, null);
            for (Map.Entry<org.bukkit.enchantments.Enchantment, Integer> e : new Map.Entry[]{e1, e2}) {
                if (e == null) continue;
                String n = EnchantApi.smeName(e.getKey());
                if (n != null) apiSme++;
                if ("supremeprotection".equals(n) || "pandorascurse".equals(n)) apiBad++;
            }
        }
        check("api.randomLoot", apiSme > 0 && apiBad == 0, "sme=" + apiSme + " blacklisted=" + apiBad);
        org.bukkit.inventory.ItemStack enchantedSword = EnchantApi.enchantWithLevels(r, new org.bukkit.inventory.ItemStack(Material.DIAMOND_SWORD), 30, false);
        check("api.enchantWithLevels", !enchantedSword.getEnchantments().isEmpty(), "enchants=" + enchantedSword.getEnchantments().size());

        // fresh loot: a Random-blacklisted book is re-rolled
        ItemStack supreme = ItemEnchantedBook.a(new WeightedRandomEnchant(E.SUPREMEPROTECTION, 10));
        org.bukkit.inventory.Inventory inv = Bukkit.createInventory(null, 9);
        inv.setItem(0, CraftItemStack.asBukkitCopy(supreme));
        TableFx.fixLoot(inv, "minecraft:chests/simple_dungeon");
        Map<Enchantment, Integer> after = EnchantmentManager.a(CraftItemStack.asNMSCopy(inv.getItem(0)));
        check("loot.randomBlacklist", !after.containsKey(E.SUPREMEPROTECTION) && after.size() == 1, "now=" + after.keySet());

        // upgrading: Sharpness V book -> Advanced Sharpness I recipe (tier), level upgrade denied at max
        ItemStack sharp5 = ItemEnchantedBook.a(new WeightedRandomEnchant(Enchantments.DAMAGE_ALL, 5));
        Upgrading.Recipe tier = null;
        for (Upgrading.Recipe rc : Upgrading.RECIPES) if (rc.in == Enchantments.DAMAGE_ALL && rc.out == E.ADVANCEDSHARPNESS) tier = rc;
        boolean upOk = tier != null && tier.canUpgrade(sharp5) == 1 && tier.cursing != null && tier.cursing.out == E.BLUNTNESS
                && tier.output(sharp5).containsKey(E.ADVANCEDSHARPNESS) && tier.tokenCount == 8;
        Upgrading.Option[] opts = Upgrading.options(sharp5, new ItemStack(Items.PRISMARINE_SHARD, 8), 12345);
        check("upgrade.recipe", upOk && opts[0] != null, "recipes=" + Upgrading.RECIPES.size() + " cost=" + (opts[0] == null ? -1 : opts[0].cost));

        // books: NMS NBT (compressed) round trip and Bukkit meta round trip for all 130
        int nbtOk = 0, metaOk = 0;
        for (SmeEnchantment e : Registrar.BY_INDEX) {
            ItemStack book = ItemEnchantedBook.a(new WeightedRandomEnchant(e, e.getMaxLevel()));
            NBTTagCompound tag = book.save(new NBTTagCompound());
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            NBTCompressedStreamTools.a(tag, bo);
            NBTTagCompound back = NBTCompressedStreamTools.a(new ByteArrayInputStream(bo.toByteArray()));
            ItemStack reloaded = new ItemStack(back);
            if (EnchantmentManager.a(reloaded).getOrDefault(e, 0) == e.getMaxLevel()) nbtOk++;
            org.bukkit.inventory.ItemStack bukkit = CraftItemStack.asBukkitCopy(book);
            EnchantmentStorageMeta meta = (EnchantmentStorageMeta) bukkit.getItemMeta();
            org.bukkit.enchantments.Enchantment be = org.bukkit.enchantments.Enchantment.getById(e.def.id);
            org.bukkit.inventory.ItemStack copy = bukkit.clone();
            copy.setItemMeta(meta);
            if (meta.getStoredEnchantLevel(be) == e.getMaxLevel()
                    && EnchantmentManager.a(CraftItemStack.asNMSCopy(copy)).getOrDefault(e, 0) == e.getMaxLevel()) metaOk++;
        }
        check("books.nbtRoundTrip", nbtOk == 130, "count=" + nbtOk);
        check("books.bukkitMetaRoundTrip", metaOk == 130, "count=" + metaOk);

        // creature damage bonus (vanilla crit/cooldown scaling applies on top)
        float adv = EnchantmentManager.a(enchanted(Items.DIAMOND_SWORD, E.ADVANCEDSHARPNESS, 5), EnumMonsterType.UNDEFINED);
        float lesserSmiteSpider = EnchantmentManager.a(enchanted(Items.DIAMOND_SWORD, E.LESSERSMITE, 2), EnumMonsterType.ARTHROPOD);
        float blunt = EnchantmentManager.a(enchanted(Items.DIAMOND_SWORD, E.BLUNTNESS, 3), EnumMonsterType.UNDEFINED);
        check("damage.creature", Math.abs(adv - 6.0F) < 1e-4 && Math.abs(lesserSmiteSpider - 2.5F) < 1e-4 && Math.abs(blunt + 3.0F) < 1e-4,
                "advsharp5=" + adv + " lessersmite2_vs_arthropod(fallthrough)=" + lesserSmiteSpider + " bluntness3=" + blunt);
    }

    private static ItemStack enchanted(net.minecraft.server.v1_12_R1.Item item, Enchantment e, int level) {
        ItemStack s = new ItemStack(item);
        s.addEnchantment(e, level);
        return s;
    }

    // ================================================================== combat

    private Husk husk(double dx, double dz, double health) {
        Location l = base.clone().add(dx, 0, dz);
        Husk h = world.spawn(l, Husk.class);
        h.setAI(false);
        h.setGravity(false);
        h.setSilent(true);
        h.setBaby(false);
        h.setRemoveWhenFarAway(false);
        EntityEquipment eq = h.getEquipment();
        eq.clear();
        h.getAttribute(Attribute.GENERIC_MAX_HEALTH).setBaseValue(health);
        h.getAttribute(Attribute.GENERIC_ARMOR).setBaseValue(0); // zombies have 2 natural armor points
        // zombies get a random 0-5% knockback resistance spawn bonus, which would make knockback checks flaky
        org.bukkit.attribute.AttributeInstance kr = h.getAttribute(Attribute.GENERIC_KNOCKBACK_RESISTANCE);
        for (org.bukkit.attribute.AttributeModifier m : new ArrayList<>(kr.getModifiers())) kr.removeModifier(m);
        kr.setBaseValue(0);
        h.setHealth(health);
        spawned.add(h);
        return h;
    }

    private static void hold(LivingEntity e, net.minecraft.server.v1_12_R1.Item item, Enchantment ench, int level) {
        ItemStack s = new ItemStack(item);
        if (ench != null) s.addEnchantment(ench, level);
        e.getEquipment().setItemInMainHand(CraftItemStack.asBukkitCopy(s));
    }

    private static float attack(LivingEntity attacker, LivingEntity victim) {
        EntityLiving v = Nms.living(victim);
        v.noDamageTicks = 0;
        float before = v.getHealth();
        ((EntityMonster) Nms.living(attacker)).B(v);
        return before - v.getHealth();
    }

    private static float attr(LivingEntity e) {
        return (float) e.getAttribute(Attribute.GENERIC_ATTACK_DAMAGE).getValue();
    }

    private void combatSteps() {
        final Husk[] a = new Husk[1], v = new Husk[1], t = new Husk[1];
        final Cow[] cow = new Cow[1];
        step(() -> {
            a[0] = husk(0, 0, 20);
            v[0] = husk(0, 1.5, 100);
            t[0] = husk(1.0, 1.5, 100);
            cow[0] = world.spawn(base.clone().add(-3, 0, 2), Cow.class);
            cow[0].setAI(false);
            cow[0].setGravity(false);
            cow[0].getAttribute(Attribute.GENERIC_MAX_HEALTH).setBaseValue(100);
            cow[0].setHealth(100);
            spawned.add(cow[0]);
            hold(a[0], Items.STICK, null, 0);
        });
        // plain stick hit (baseline)
        final float[] baseDmg = new float[1];
        step(() -> {
            baseDmg[0] = attack(a[0], v[0]);
            check("combat.baseline", Math.abs(baseDmg[0] - attr(a[0])) < 0.01, "damage=" + baseDmg[0] + " attackAttr=" + attr(a[0]));
        });
        // Advanced Sharpness V: +6 through calcDamageByCreature
        step(() -> hold(a[0], Items.STICK, E.ADVANCEDSHARPNESS, 5));
        step(() -> {
            float d = attack(a[0], v[0]);
            check("combat.advancedsharpness", Math.abs(d - (attr(a[0]) + 6.0F)) < 0.01, "damage=" + d + " expected=" + (attr(a[0]) + 6.0F));
        });
        // Lifesteal IV: heals 3% x level of the hurt amount
        step(() -> {
            hold(a[0], Items.STICK, E.LIFESTEAL, 4);
            a[0].setHealth(10);
        });
        step(() -> {
            float d = attack(a[0], v[0]);
            double healed = a[0].getHealth() - 10;
            check("combat.lifesteal", Math.abs(healed - d * 0.03 * 4) < 0.01, "damage=" + d + " healed=" + healed);
        });
        // Viper V vs a poisoned target (undead cannot be poisoned, so the cow): +1.75+0.75L
        step(() -> {
            hold(a[0], Items.STICK, E.VIPER, 5);
            Nms.living(cow[0]).addEffect(new MobEffect(MobEffects.POISON, 2000, 0));
        });
        step(() -> {
            float d = attack(a[0], cow[0]);
            check("combat.viper", Math.abs(d - (attr(a[0]) + 5.5F)) < 0.01, "damage=" + d + " expected=" + (attr(a[0]) + 5.5F));
            Nms.living(cow[0]).removeAllEffects();
            cow[0].setHealth(100);
        });
        // Butchering V vs an animal: +2L
        step(() -> hold(a[0], Items.STICK, E.BUTCHERING, 5));
        step(() -> {
            float d = attack(a[0], cow[0]);
            check("combat.butchering", Math.abs(d - (attr(a[0]) + 10.0F)) < 0.01, "damage=" + d + " expected=" + (attr(a[0]) + 10.0F));
        });
        // Arc Slash III: a second mob in front takes 0.25*L of the hurt amount as mobCleave damage
        step(() -> {
            hold(a[0], Items.STICK, E.ARCSLASH, 3);
            EntityLiving al = Nms.living(a[0]);
            al.yaw = 0;
            al.pitch = 0;
            al.setHeadRotation(0);
            t[0].teleport(base.clone().add(0.8, 0, 1.5));
        });
        step(() -> {
            float before = (float) t[0].getHealth();
            float d = attack(a[0], v[0]);
            float cleave = before - (float) t[0].getHealth();
            check("combat.arcslash", Math.abs(cleave - d * 0.75F) < 0.05, "hit=" + d + " cleave=" + cleave + " expected=" + d * 0.75F);
        });
        // Advanced Protection IV on the victim (item in the chest slot, 0 armor): SME extra reduction then EPF 8
        step(() -> {
            hold(a[0], Items.STICK, null, 0);
            ItemStack chest = new ItemStack(Items.STICK);
            chest.addEnchantment(E.ADVANCEDPROTECTION, 4);
            v[0].getEquipment().setChestplate(CraftItemStack.asBukkitCopy(chest));
        });
        step(() -> {
            float d = attack(a[0], v[0]);
            float expected = attr(a[0]) * (1.0F - 28.5F / 80.0F) * (1.0F - 8.0F / 25.0F);
            check("combat.advancedprotection", Math.abs(d - expected) < 0.01, "damage=" + d + " expected=" + expected);
            v[0].getEquipment().setChestplate(null);
        });
        // Evasion III (legs): 50% dodge chance per attack
        step(() -> {
            ItemStack legs = new ItemStack(Items.STICK);
            legs.addEnchantment(E.EVASION, 3);
            v[0].getEquipment().setLeggings(CraftItemStack.asBukkitCopy(legs));
        });
        step(() -> {
            int dodged = 0;
            for (int i = 0; i < 40; i++) {
                v[0].teleport(base.clone().add(0, 0, 1.5));
                v[0].setHealth(100);
                if (attack(a[0], v[0]) == 0.0F) dodged++;
            }
            check("combat.evasion", dodged >= 8 && dodged <= 32, "dodged=" + dodged + "/40");
            v[0].getEquipment().setLeggings(null);
            v[0].teleport(base.clone().add(0, 0, 1.5));
        });
        // Envenomed III through the arthropod pass (onEntityDamagedAlt): 60% poison (+wither) per hit
        step(() -> hold(a[0], Items.STICK, E.ENVENOMED, 3));
        step(() -> {
            int poisoned = 0;
            for (int i = 0; i < 10; i++) {
                Nms.living(cow[0]).removeAllEffects();
                cow[0].setHealth(100);
                attack(a[0], cow[0]);
                if (Nms.living(cow[0]).hasEffect(MobEffects.POISON) && Nms.living(cow[0]).hasEffect(MobEffects.WITHER)) poisoned++;
            }
            check("combat.envenomed(alt)", poisoned >= 2, "poisonAndWither=" + poisoned + "/10");
            Nms.living(cow[0]).removeAllEffects();
            cow[0].setHealth(100);
        });
        // Advanced Fire Aspect II: fire aspect modifier 2*2 -> 16 s of fire after a mob hit
        step(() -> hold(a[0], Items.STICK, E.ADVANCEDFIREASPECT, 2));
        step(() -> {
            EntityLiving vl = Nms.living(v[0]);
            vl.extinguish();
            attack(a[0], v[0]);
            check("combat.advancedfireaspect", vl.fireTicks >= 300, "fireTicks=" + vl.fireTicks);
            vl.extinguish();
        });
        // Culling III: an attack leaving <= 15% health kills instead
        step(() -> {
            hold(a[0], Items.STICK, E.CULLING, 3);
            v[0].getAttribute(Attribute.GENERIC_MAX_HEALTH).setBaseValue(20);
            v[0].setHealth(5);
        });
        step(() -> {
            attack(a[0], v[0]);
            check("combat.culling", v[0].isDead() || v[0].getHealth() <= 0, "health=" + v[0].getHealth());
        });
        // Advanced Knockback II: floor(2.5*2)=5 extra enchantment knockback after the standard push
        final double[] kbBase = new double[1];
        step(() -> {
            hold(a[0], Items.STICK, null, 0);
            v[0].remove(); // Culling killed it; use a fresh victim
            v[0] = husk(0, 1.5, 100);
        });
        step(() -> {
            EntityLiving vl = Nms.living(v[0]);
            vl.motX = vl.motY = vl.motZ = 0;
            attack(a[0], v[0]);
            kbBase[0] = Math.sqrt(vl.motX * vl.motX + vl.motZ * vl.motZ);
            hold(a[0], Items.STICK, E.ADVANCEDKNOCKBACK, 2);
            v[0].teleport(base.clone().add(0, 0, 1.5));
        });
        step(() -> {
            EntityLiving vl = Nms.living(v[0]);
            vl.motX = vl.motY = vl.motZ = 0;
            attack(a[0], v[0]);
            double kb = Math.sqrt(vl.motX * vl.motX + vl.motZ * vl.motZ);
            // standard 0.4 push then knockBack(5*0.5): |v| = 0.4/2 + 2.5 = 2.7 (vanilla without enchant: 0.4)
            check("combat.advancedknockback", kbBase[0] > 0.3 && kb > 2.0, "withoutEnchant=" + kbBase[0] + " withAdvancedKnockbackII=" + kb);
            vl.motX = vl.motY = vl.motZ = 0;
            v[0].teleport(base.clone().add(0, 0, 1.5));
        });
        // Rune: Resurrection II in the off hand: a lethal hit leaves the holder at min(max, max*0.5*2) health, rune consumed
        final Husk[] r = new Husk[1];
        step(() -> {
            hold(a[0], Items.STICK, E.ADVANCEDSHARPNESS, 5);
            r[0] = husk(-1.0, 1.5, 20);
            ItemStack shield = new ItemStack(Items.SHIELD);
            shield.addEnchantment(E.RUNE_RESURRECTION, 2);
            r[0].getEquipment().setItemInOffHand(CraftItemStack.asBukkitCopy(shield));
            r[0].setHealth(2);
        });
        step(() -> {
            attack(a[0], r[0]);
            ItemStack off = Nms.living(r[0]).getItemInOffHand();
            boolean runeGone = Nms.level(E.RUNE_RESURRECTION, off) == 0;
            boolean regen = Nms.living(r[0]).hasEffect(MobEffects.ABSORBTION); // husks are undead: regeneration is rejected, absorption applies
            check("combat.runeresurrection", !r[0].isDead() && Math.abs(r[0].getHealth() - 20.0) < 0.01 && runeGone && regen,
                    "alive=" + !r[0].isDead() + " health=" + r[0].getHealth() + " runeConsumed=" + runeGone + " absorption=" + regen);
        });
        // Advanced Looting III: the killer's weapon carries SME's looting only during loot generation
        step(() -> {
            hold(a[0], Items.STICK, E.ADVANCEDLOOTING, 3);
            cow[0].getAttribute(Attribute.GENERIC_MAX_HEALTH).setBaseValue(2);
            cow[0].setHealth(1);
        });
        step(() -> {
            long before = chat.jaspr.enchant.fx.LootFx.lootingAdjusted;
            attack(a[0], cow[0]);
            ItemStack held = Nms.living(a[0]).getItemInMainHand();
            boolean restored = Nms.level(Enchantments.LOOT_BONUS_MOBS, held) == 0 && Nms.level(E.ADVANCEDLOOTING, held) == 3;
            check("loot.advancedlooting", cow[0].isDead() && chat.jaspr.enchant.fx.LootFx.lootingAdjusted == before + 1 && restored,
                    "dead=" + cow[0].isDead() + " adjusted=" + (chat.jaspr.enchant.fx.LootFx.lootingAdjusted - before) + " weaponRestored=" + restored);
        });
        // librarian: a blacklisted enchanted book trade is re-rolled
        step(() -> {
            org.bukkit.entity.Villager vill = world.spawn(base.clone().add(3, 0, -3), org.bukkit.entity.Villager.class);
            vill.setAI(false);
            vill.setGravity(false);
            spawned.add(vill);
            org.bukkit.inventory.MerchantRecipe rec = new org.bukkit.inventory.MerchantRecipe(
                    CraftItemStack.asBukkitCopy(ItemEnchantedBook.a(new WeightedRandomEnchant(E.ADVANCEDSHARPNESS, 3))), 0, 7, true);
            rec.addIngredient(new org.bukkit.inventory.ItemStack(Material.BOOK));
            rec.addIngredient(new org.bukkit.inventory.ItemStack(Material.EMERALD, 20));
            org.bukkit.event.entity.VillagerAcquireTradeEvent ev = new org.bukkit.event.entity.VillagerAcquireTradeEvent(vill, rec);
            Bukkit.getPluginManager().callEvent(ev);
            Map<Enchantment, Integer> got = EnchantmentManager.a(CraftItemStack.asNMSCopy(ev.getRecipe().getResult()));
            boolean ok = got.size() == 1 && !Enchanting.LIBRARIAN_BLACKLIST.contains(got.keySet().iterator().next());
            check("librarian.blacklist", ok, "trade now=" + got);
        });
        // temporary blocks: frosted ice and magma are tracked and reverted
        step(() -> {
            WorldServer w = ((CraftWorld) world).getHandle();
            BlockPosition p = new BlockPosition(base.getBlockX() + 6, 201, base.getBlockZ() + 6);
            TempBlocks.placeIce(w, p, 100);
            BlockPosition q = p.a(0, 2, 0);
            TempBlocks.placeMagma(w, q, 100);
            boolean placed = w.getType(p).getBlock() == Blocks.FROSTED_ICE && w.getType(q).getBlock() == Blocks.df;
            int n = TempBlocks.revertAll();
            boolean reverted = w.getType(p).getBlock() == Blocks.AIR && w.getType(q).getBlock() == Blocks.LAVA;
            check("tempblocks", placed && reverted && n == 2, "placed=" + placed + " reverted=" + reverted + " n=" + n);
            w.setAir(q);
        });
    }

    // ================================================================== world save / reload

    private void chestPersistence() {
        File marker = new File(plugin.getDataFolder(), "selftest-books.txt");
        try {
            if (!marker.isFile()) {
                List<String> lines = new ArrayList<>();
                int idx = 0;
                for (int c = 0; c < 5; c++) {
                    Block b = world.getBlockAt(base.getBlockX() + 10 + c * 2, 60, base.getBlockZ() + 10);
                    b.setType(Material.CHEST);
                    Chest chest = (Chest) b.getState();
                    for (int s = 0; s < 27 && idx < 130; s++, idx++) {
                        SmeEnchantment e = Registrar.BY_INDEX[idx];
                        chest.getBlockInventory().setItem(s, CraftItemStack.asBukkitCopy(ItemEnchantedBook.a(new WeightedRandomEnchant(e, e.getMaxLevel()))));
                    }
                    lines.add(b.getX() + " " + b.getY() + " " + b.getZ());
                }
                world.save();
                Files.write(marker.toPath(), String.join("\n", lines).getBytes(StandardCharsets.UTF_8));
                check("books.worldSave", idx == 130, "written=" + idx + " chests=" + lines.size() + " (verified on next start)");
            } else {
                int found = 0;
                boolean[] seen = new boolean[130];
                for (String line : Files.readAllLines(marker.toPath(), StandardCharsets.UTF_8)) {
                    String[] a = line.trim().split(" ");
                    if (a.length != 3) continue;
                    Block b = world.getBlockAt(Integer.parseInt(a[0]), Integer.parseInt(a[1]), Integer.parseInt(a[2]));
                    if (b.getType() != Material.CHEST) continue;
                    for (org.bukkit.inventory.ItemStack it : ((Chest) b.getState()).getBlockInventory().getContents()) {
                        if (it == null || it.getType() != Material.ENCHANTED_BOOK) continue;
                        for (Map.Entry<Enchantment, Integer> en : EnchantmentManager.a(CraftItemStack.asNMSCopy(it)).entrySet()) {
                            if (en.getKey() instanceof SmeEnchantment) {
                                SmeEnchantment se = (SmeEnchantment) en.getKey();
                                if (en.getValue() == se.getMaxLevel() && !seen[se.def.index]) {
                                    seen[se.def.index] = true;
                                    found++;
                                }
                            }
                        }
                    }
                    ((Chest) b.getState()).getBlockInventory().clear();
                    b.setType(Material.AIR);
                }
                check("books.worldReload", found == 130, "found=" + found + "/130 after restart");
                marker.delete();
            }
        } catch (Exception ex) {
            check("books.world", false, ex.toString());
        }
    }
}
