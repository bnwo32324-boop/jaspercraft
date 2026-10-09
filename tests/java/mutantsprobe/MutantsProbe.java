package mutantsprobe;

import chat.jaspr.mutants.MutantsApi;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import net.minecraft.server.v1_12_R1.BiomeBase;
import net.minecraft.server.v1_12_R1.Biomes;
import net.minecraft.server.v1_12_R1.Entity;
import net.minecraft.server.v1_12_R1.EntityTypes;
import net.minecraft.server.v1_12_R1.EnumCreatureType;
import net.minecraft.server.v1_12_R1.MinecraftKey;
import net.minecraft.server.v1_12_R1.SoundCategory;
import net.minecraft.server.v1_12_R1.SoundEffect;
import net.minecraft.server.v1_12_R1.WorldServer;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;
import org.bukkit.block.Block;
import org.bukkit.block.BrewingStand;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.craftbukkit.v1_12_R1.CraftWorld;
import org.bukkit.craftbukkit.v1_12_R1.entity.CraftEntity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.BrewerInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Test-only probe for tests/mutants-runtime.cjs (never deployed). Console command "mprobe &lt;op&gt; [args]"; every op
 * prints one PROBE_* line (or PROBE_FAIL).
 */
public final class MutantsProbe extends JavaPlugin implements Listener {
    private static final String[] LIVING = {"mutant_zombie", "mutant_skeleton", "mutant_creeper", "mutant_enderman", "mutant_snow_golem", "spider_pig", "creeper_minion", "endersoul_clone"};
    private final List<String> deaths = new ArrayList<String>();
    private final List<int[]> held = new ArrayList<int[]>();

    @Override
    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, this);
    }

    @EventHandler
    public void onDeath(EntityDeathEvent e) {
        String kind = MutantsApi.kindOf(e.getEntity());
        if (kind == null) return;
        StringBuilder sb = new StringBuilder("PROBE_DEATH kind=" + kind + " drops=");
        for (ItemStack s : e.getDrops()) sb.append(s.getType().name()).append('x').append(s.getAmount()).append(',');
        sb.append(" xp=").append(e.getDroppedExp());
        this.deaths.add(sb.toString());
        this.getLogger().info(sb.toString());
    }

    private void out(String line) {
        this.getLogger().info(line);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        try {
            this.run(args);
        } catch (Throwable t) {
            this.out("PROBE_FAIL " + t.getClass().getSimpleName() + " " + t.getMessage());
            t.printStackTrace();
        }
        return true;
    }

    private Player player(String name) {
        Player p = Bukkit.getPlayerExact(name);
        if (p == null) throw new IllegalStateException("no player " + name);
        return p;
    }

    private Map<String, Integer> counts(World w) {
        Map<String, Integer> m = new TreeMap<String, Integer>();
        for (org.bukkit.entity.Entity e : w.getEntities()) {
            String k = MutantsApi.kindOf(e);
            if (k != null) m.merge(k, 1, Integer::sum);
        }
        return m;
    }

    private void run(String[] a) throws Exception {
        String op = a[0];
        World main = Bukkit.getWorlds().get(0);
        if ("spawnall".equals(op)) {
            Player p = this.player(a[1]);
            Location base = p.getLocation();
            int i = 0;
            for (String kind : LIVING) {
                double ang = Math.PI * 2 * i++ / LIVING.length;
                Location at = base.clone().add(Math.cos(ang) * 9, 0, Math.sin(ang) * 9);
                at.setY(main.getHighestBlockYAt(at) + 0.1);
                LivingEntity e = MutantsApi.spawn(kind, at);
                if (e == null) throw new IllegalStateException("spawn refused: " + kind);
                e.setAI(false);
                this.out("PROBE_SPAWN kind=" + kind + " id=" + e.getEntityId() + " type=" + e.getType() + " bukkit=" + e.getClass().getSimpleName()
                        + " health=" + e.getHealth() + " max=" + e.getMaxHealth() + " kindOf=" + MutantsApi.kindOf(e) + " monster=" + (e instanceof org.bukkit.entity.Monster));
            }
            this.out("PROBE_OK spawnall");
        } else if ("spawn".equals(op)) {
            Player p = this.player(a[1]);
            Location at = p.getLocation().clone().add(Double.parseDouble(a[3]), 0, Double.parseDouble(a[4]));
            at.setY(main.getHighestBlockYAt(at) + 0.1);
            LivingEntity e = MutantsApi.spawn(a[2], at);
            if (e == null) throw new IllegalStateException("spawn refused: " + a[2]);
            if (a.length > 5 && "noai".equals(a[5])) e.setAI(false);
            this.out("PROBE_SPAWNED kind=" + a[2] + " id=" + e.getEntityId());
        } else if ("count".equals(op)) {
            this.out("PROBE_COUNT " + this.counts(main));
        } else if ("api".equals(op)) {
            Location at = main.getSpawnLocation();
            this.out("PROBE_API unknown=" + (MutantsApi.spawn("not_a_kind", at) == null) + " nonliving=" + (MutantsApi.spawn("skull_spirit", at) == null) + " available=" + MutantsApi.available());
        } else if ("unloadmod".equals(op)) {
            this.held.clear();
            for (org.bukkit.entity.Entity e : main.getEntities()) {
                if (MutantsApi.kindOf(e) == null) continue;
                Chunk c = e.getLocation().getChunk();
                boolean seen = false;
                for (int[] k : this.held) if (k[0] == c.getX() && k[1] == c.getZ()) seen = true;
                if (!seen) this.held.add(new int[]{c.getX(), c.getZ()});
            }
            int unloaded = 0;
            for (int[] k : this.held) if (main.unloadChunk(k[0], k[1], true, false)) unloaded++;
            this.out("PROBE_UNLOADED chunks=" + this.held.size() + " unloaded=" + unloaded);
        } else if ("loadmod".equals(op)) {
            for (int[] k : this.held) main.loadChunk(k[0], k[1], false);
            this.out("PROBE_LOADED chunks=" + this.held.size());
        } else if ("reloadchunks".equals(op)) {
            // the player must be far away; unloads (saving) every chunk holding mod entities, then loads them again
            List<int[]> chunks = new ArrayList<int[]>();
            for (org.bukkit.entity.Entity e : main.getEntities()) {
                if (MutantsApi.kindOf(e) == null) continue;
                Chunk c = e.getLocation().getChunk();
                boolean seen = false;
                for (int[] k : chunks) if (k[0] == c.getX() && k[1] == c.getZ()) seen = true;
                if (!seen) chunks.add(new int[]{c.getX(), c.getZ()});
            }
            Map<String, Integer> before = this.counts(main);
            int unloaded = 0;
            for (int[] k : chunks) if (main.unloadChunk(k[0], k[1], true, false)) unloaded++;
            Map<String, Integer> mid = this.counts(main);
            for (int[] k : chunks) main.loadChunk(k[0], k[1], false);
            this.out("PROBE_RELOAD chunks=" + chunks.size() + " unloaded=" + unloaded + " before=" + before + " during=" + mid + " after=" + this.counts(main));
        } else if ("kill".equals(op)) {
            Player p = this.player(a[1]);
            int n = 0;
            for (org.bukkit.entity.Entity e : main.getEntities()) {
                if (a[2].equals(MutantsApi.kindOf(e)) && e instanceof LivingEntity) {
                    if ("mutant_zombie".equals(a[2])) {   // its last life: the mod's zombie otherwise rises again after 140 ticks
                        Object h = ((CraftEntity) e).getHandle();
                        Method lives = h.getClass().getDeclaredMethod("setLives", int.class);
                        lives.setAccessible(true);
                        lives.invoke(h, 0);
                    }
                    ((LivingEntity) e).damage(100000.0D, p);
                    n++;
                }
            }
            this.out("PROBE_KILLED kind=" + a[2] + " n=" + n);
        } else if ("clear".equals(op)) {
            int n = 0;
            for (World w : Bukkit.getWorlds()) for (org.bukkit.entity.Entity e : w.getEntities()) if (MutantsApi.kindOf(e) != null) { e.remove(); n++; }
            this.out("PROBE_CLEARED n=" + n);
        } else if ("biomes".equals(op)) {
            int mutantEntries = 0;
            StringBuilder kinds = new StringBuilder();
            for (Object o : BiomeBase.REGISTRY_ID) {
                BiomeBase b = (BiomeBase) o;
                for (BiomeBase.BiomeMeta m : b.getMobs(EnumCreatureType.MONSTER)) {
                    java.lang.reflect.Field f = BiomeBase.BiomeMeta.class.getDeclaredField("b");
                    f.setAccessible(true);
                    Class<?> c = (Class<?>) f.get(m);
                    if (c.getName().startsWith("chumbanotz")) {
                        mutantEntries++;
                        if (b == Biomes.c) {  // plains: class and weight
                            java.lang.reflect.Field w = net.minecraft.server.v1_12_R1.WeightedRandom.WeightedRandomChoice.class.getDeclaredField("a");
                            w.setAccessible(true);
                            kinds.append(c.getSimpleName()).append(':').append(w.getInt(m)).append(',');
                        }
                    }
                }
            }
            this.out("PROBE_BIOMES entries=" + mutantEntries + " plains=" + kinds);
        } else if ("natural".equals(op)) {
            // a NATURAL spawn of a mod mob: kept in the main world, refused anywhere else
            World other = Bukkit.getWorld("mutants_other");
            if (other == null) other = new WorldCreator("mutants_other").type(WorldType.FLAT).generateStructures(false).createWorld();
            String r = "";
            for (World w : new World[]{main, other}) {
                WorldServer ws = ((CraftWorld) w).getHandle();
                Entity e = EntityTypes.a(new MinecraftKey("mutantbeasts", "mutant_zombie"), ws);
                Location l = w.getSpawnLocation();
                e.setPositionRotation(l.getX(), w.getHighestBlockYAt(l) + 0.1, l.getZ(), 0, 0);
                boolean added = ws.addEntity(e, CreatureSpawnEvent.SpawnReason.NATURAL);
                r += " " + (w == main ? "main" : "other") + "=" + added;
                if (added) e.getBukkitEntity().remove();
            }
            this.out("PROBE_NATURAL" + r);
        } else if ("sound".equals(op)) {
            Player p = this.player(a[1]);
            int id = Integer.parseInt(a[2]);
            WorldServer ws = ((CraftWorld) p.getWorld()).getHandle();
            SoundEffect s = SoundEffect.a.getId(id);
            if (s == null) throw new IllegalStateException("no sound " + id);
            Location l = p.getLocation();
            ws.a(null, l.getX(), l.getY(), l.getZ(), s, SoundCategory.HOSTILE, 1.0F, 1.0F);
            this.out("PROBE_SOUND id=" + id + " name=" + SoundEffect.a.b(s));
        } else if ("items".equals(op)) {
            Player p = this.player(a[1]);
            int n = 0;
            StringBuilder sb = new StringBuilder();
            for (Material m : Material.values()) {
                if (!m.name().startsWith("MUTANTBEASTS_")) continue;
                ItemStack s = new ItemStack(m, 1);
                org.bukkit.inventory.meta.ItemMeta meta = s.getItemMeta();   // CraftBukkit switches over Material here
                if (meta != null) { meta.setDisplayName(null); s.setItemMeta(meta); }
                s.serialize();
                p.getInventory().addItem(s);
                sb.append(m.name()).append('=').append(m.getId()).append(',');
                n++;
            }
            this.out("PROBE_ITEMS n=" + n + " " + sb);
        } else if ("give".equals(op)) {
            Player p = this.player(a[1]);
            ItemStack s = new ItemStack(Material.valueOf(a[2]), 1);
            p.getInventory().setItemInMainHand(s);
            this.out("PROBE_GIVE " + a[2]);
        } else if ("recipes".equals(op)) {
            int n = 0;
            for (java.util.Iterator<org.bukkit.inventory.Recipe> it = Bukkit.recipeIterator(); it.hasNext(); ) {
                org.bukkit.inventory.Recipe r = it.next();
                if (r instanceof org.bukkit.Keyed && "mutantbeasts".equals(((org.bukkit.Keyed) r).getKey().getNamespace())) n++;
            }
            boolean chest = !Bukkit.getRecipesFor(new ItemStack(Material.valueOf("MUTANTBEASTS_MUTANT_SKELETON_CHESTPLATE"))).isEmpty();
            this.out("PROBE_RECIPES mod=" + n + " chestplate=" + chest);
        } else if ("advancements".equals(op)) {
            int n = 0;
            for (java.util.Iterator<org.bukkit.advancement.Advancement> it = Bukkit.advancementIterator(); it.hasNext(); ) {
                if ("mutantbeasts".equals(it.next().getKey().getNamespace())) n++;
            }
            this.out("PROBE_ADVANCEMENTS mod=" + n + " root=" + (Bukkit.getAdvancement(new NamespacedKey("mutantbeasts", "root")) != null));
        } else if ("brew".equals(op)) {
            Player p = this.player(a[1]);
            Block b = p.getLocation().clone().add(2, 0, 0).getBlock();
            b.setType(Material.BREWING_STAND);
            BrewingStand stand = (BrewingStand) b.getState();
            BrewerInventory inv = stand.getInventory();
            for (int i = 0; i < 3; i++) {
                ItemStack thick = new ItemStack(Material.POTION, 1);
                org.bukkit.inventory.meta.PotionMeta meta = (org.bukkit.inventory.meta.PotionMeta) thick.getItemMeta();
                meta.setBasePotionData(new org.bukkit.potion.PotionData(org.bukkit.potion.PotionType.THICK));
                thick.setItemMeta(meta);
                inv.setItem(i, thick);
            }
            inv.setIngredient(new ItemStack(Material.END_CRYSTAL, 1));
            inv.setFuel(new ItemStack(Material.BLAZE_POWDER, 4));
            this.out("PROBE_BREW_STARTED x=" + b.getX() + " y=" + b.getY() + " z=" + b.getZ());
        } else if ("brewfast".equals(op)) {
            Block b = main.getBlockAt(Integer.parseInt(a[1]), Integer.parseInt(a[2]), Integer.parseInt(a[3]));
            BrewingStand stand = (BrewingStand) b.getState();
            int t = stand.getBrewingTime();
            if (t > 2) {
                stand.setBrewingTime(2);
                stand.update(true);
            }
            this.out("PROBE_BREWTIME was=" + t);
        } else if ("brewed".equals(op)) {
            Block b = main.getBlockAt(Integer.parseInt(a[1]), Integer.parseInt(a[2]), Integer.parseInt(a[3]));
            BrewerInventory inv = ((BrewingStand) b.getState()).getInventory();
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 5; i++) {
                ItemStack s = inv.getItem(i);
                sb.append(i).append('=').append(s == null ? "EMPTY" : s.getType().name() + "x" + s.getAmount()).append(',');
            }
            this.out("PROBE_BREWED " + sb);
        } else if ("golem".equals(op)) {
            org.bukkit.entity.Entity e = null;
            for (org.bukkit.entity.Entity x : main.getEntities()) if ("mutant_snow_golem".equals(MutantsApi.kindOf(x))) e = x;
            if (e == null) throw new IllegalStateException("no golem");
            Object handle = ((CraftEntity) e).getHandle();
            Method pumpkin = handle.getClass().getMethod("isPumpkinEquipped");
            Method owner;
            try { owner = handle.getClass().getMethod("getOwnerUUID"); } catch (NoSuchMethodException ex) { owner = handle.getClass().getMethod("getOwnerId"); }
            this.out("PROBE_GOLEM id=" + e.getEntityId() + " pumpkin=" + pumpkin.invoke(handle) + " owner=" + owner.invoke(handle));
        } else if ("drops".equals(op)) {
            int lit = 0;
            for (org.bukkit.entity.Entity x : main.getEntities()) {
                if (x instanceof org.bukkit.entity.Item && ((org.bukkit.entity.Item) x).getItemStack().getType() == Material.JACK_O_LANTERN) lit++;
            }
            this.out("PROBE_DROPS jack_o_lantern=" + lit);
        } else if ("enchant".equals(op)) {
            // Forge's enchanting table for the Hulk Hammer (EnchantingHooks), and a vanilla sword at the same table
            Player p = this.player(a[1]);
            p.setLevel(100);
            Location table = p.getLocation().clone().add(0, 0, 3);
            table.getBlock().setType(Material.ENCHANTMENT_TABLE);
            StringBuilder sb = new StringBuilder("PROBE_ENCHANT");
            // five tries per item (each enchanting changes the player's seed): the drawn lists are random
            for (String item : new String[]{"MUTANTBEASTS_HULK_HAMMER", "MUTANTBEASTS_HULK_HAMMER", "MUTANTBEASTS_HULK_HAMMER", "MUTANTBEASTS_HULK_HAMMER", "MUTANTBEASTS_HULK_HAMMER",
                    "MUTANTBEASTS_ENDERSOUL_HAND", "MUTANTBEASTS_ENDERSOUL_HAND", "MUTANTBEASTS_ENDERSOUL_HAND", "MUTANTBEASTS_ENDERSOUL_HAND", "MUTANTBEASTS_ENDERSOUL_HAND", "DIAMOND_SWORD"}) {
                org.bukkit.inventory.InventoryView view = p.openEnchanting(table, true);
                org.bukkit.inventory.EnchantingInventory inv = (org.bukkit.inventory.EnchantingInventory) view.getTopInventory();
                inv.setSecondary(new ItemStack(Material.INK_SACK, 64, (short) 4));
                inv.setItem(new ItemStack(Material.valueOf(item), 1));
                net.minecraft.server.v1_12_R1.EntityPlayer h = ((org.bukkit.craftbukkit.v1_12_R1.entity.CraftPlayer) p).getHandle();
                net.minecraft.server.v1_12_R1.ContainerEnchantTable c = (net.minecraft.server.v1_12_R1.ContainerEnchantTable) h.activeContainer;
                java.lang.reflect.Field clueF = c.getClass().getDeclaredField("h");
                clueF.setAccessible(true);
                int[] clue = (int[]) clueF.get(c);
                int slot = c.costs[2] > 0 ? 2 : c.costs[1] > 0 ? 1 : 0;
                String clues = clue[0] + "/" + clue[1] + "/" + clue[2];
                boolean done = c.a(h, slot);
                ItemStack result = inv.getItem();
                sb.append(' ').append(item).append("[costs=").append(c.costs[0]).append('/').append(c.costs[1]).append('/').append(c.costs[2])
                        .append(" clues=").append(clues).append(" done=").append(done).append(" enchants=");
                if (result != null) for (Map.Entry<org.bukkit.enchantments.Enchantment, Integer> en : result.getEnchantments().entrySet()) sb.append(en.getKey().getName()).append(':').append(en.getValue()).append(',');
                sb.append(']');
                p.closeInventory();
            }
            this.out(sb.toString());
        } else if ("armor".equals(op)) {
            Player p = this.player(a[1]);
            p.getInventory().setLeggings(new ItemStack(Material.valueOf("MUTANTBEASTS_MUTANT_SKELETON_LEGGINGS")));
            p.getInventory().setBoots(new ItemStack(Material.valueOf("MUTANTBEASTS_MUTANT_SKELETON_BOOTS")));
            p.getInventory().setHelmet(new ItemStack(Material.valueOf("MUTANTBEASTS_MUTANT_SKELETON_SKULL")));
            p.getInventory().setChestplate(new ItemStack(Material.valueOf("MUTANTBEASTS_MUTANT_SKELETON_CHESTPLATE")));
            this.out("PROBE_ARMOR worn");
        } else if ("effects".equals(op)) {
            Player p = this.player(a[1]);
            this.out("PROBE_EFFECTS speed=" + p.hasPotionEffect(org.bukkit.potion.PotionEffectType.SPEED) + " jump=" + p.hasPotionEffect(org.bukkit.potion.PotionEffectType.JUMP) + " armor=" + p.getAttribute(org.bukkit.attribute.Attribute.GENERIC_ARMOR).getValue());
        } else if ("unarmor".equals(op)) {
            Player p = this.player(a[1]);
            p.getInventory().setArmorContents(new ItemStack[4]);
            for (org.bukkit.potion.PotionEffect e : p.getActivePotionEffects()) p.removePotionEffect(e.getType());
            this.out("PROBE_UNARMOR");
        } else if ("shard".equals(op)) {
            // vanilla explosions skip Creeper Shard items (the mod's ExplosionEvent.Detonate); a dirt item next to it is destroyed
            Player p = this.player(a[1]);
            Location l = p.getLocation().clone().add(6, 0, 6);
            l.setY(main.getHighestBlockYAt(l) + 0.2);
            org.bukkit.entity.Item shard = main.dropItem(l.clone().add(0.5, 0, 0), new ItemStack(Material.valueOf("MUTANTBEASTS_CREEPER_SHARD")));
            org.bukkit.entity.Item dirt = main.dropItem(l.clone().add(-0.5, 0, 0), new ItemStack(Material.DIRT));
            shard.setVelocity(new org.bukkit.util.Vector(0, 0, 0));
            dirt.setVelocity(new org.bukkit.util.Vector(0, 0, 0));
            Bukkit.getScheduler().runTaskLater(this, () -> {
                main.createExplosion(l.getX(), l.getY(), l.getZ(), 3.0F, false, false);
                Bukkit.getScheduler().runTaskLater(this, () -> this.out("PROBE_SHARD shard=" + shard.isValid() + " dirt=" + dirt.isValid()), 3L);
            }, 10L);
        } else if ("victim".equals(op)) {
            Player p = this.player(a[1]);
            org.bukkit.util.Vector dir = p.getLocation().getDirection().setY(0).normalize();
            Location l = p.getLocation().clone().add(dir.multiply(Double.parseDouble(a[2])));
            l.setY(main.getHighestBlockYAt(l) + 0.1);
            LivingEntity z = (LivingEntity) main.spawnEntity(l, org.bukkit.entity.EntityType.ZOMBIE);
            z.setAI(false);
            z.getEquipment().setHelmet(new ItemStack(Material.STONE_BUTTON));
            this.out("PROBE_VICTIM id=" + z.getEntityId() + " health=" + z.getHealth());
        } else if ("health".equals(op)) {
            int id = Integer.parseInt(a[1]);
            String h = "gone";
            for (org.bukkit.entity.Entity e : main.getEntities()) if (e.getEntityId() == id && e instanceof LivingEntity) h = String.valueOf(((LivingEntity) e).getHealth());
            this.out("PROBE_HEALTH id=" + id + " health=" + h);
        } else if ("deaths".equals(op)) {
            this.out("PROBE_DEATHS n=" + this.deaths.size() + " " + String.join(" | ", this.deaths));
        } else {
            this.out("PROBE_FAIL unknown op " + op);
        }
    }
}
