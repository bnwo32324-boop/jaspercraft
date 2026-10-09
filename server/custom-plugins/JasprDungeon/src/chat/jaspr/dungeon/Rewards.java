package chat.jaspr.dungeon;

import java.util.*;
import org.bukkit.*;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.*;

/** Shared room loot only. No recipes, personal bonus rolls, or outside-world acquisition. */
public final class Rewards {
    private Rewards(){}
    /** Second lore line of dungeon gear, followed by the threat. Gear from generation 3 still carries LEGACY_GEAR_ORIGIN. */
    public static final String GEAR_ORIGIN="The Dungeon Dimension | Threat ", LEGACY_GEAR_ORIGIN="The Penitent Below | Threat ";
    public static int budget(Layout.Room r){return LootCatalog.budget(r.kind.name(),r.tier,r.mobCount());}
    /** Generation 7: absolving a room pays experience by its threat, more on deeper floors (Floors.loot: 1, 1.5, 2.2). */
    public static int experience(Layout.Room r){return r.kind==Layout.Kind.REFUGE?0:(int)Math.round(Math.max(0,Math.min(5,r.tier))*5*Floors.loot(r.floor));}
    /** A stack ceiling on this floor: Floor I's own, raised with Floors.loot below it. */
    static int cap(int base,int floor){return (int)Math.round(base*Floors.loot(floor));}
    /**
     * The room's reliquary. multiplier is the caller's (DungeonPlugin.rewardMultiplier already holds the configuration, the rift
     * depth and Floors.loot of the floor), so the budget grows with the floor; generation 7 also raises every stack's ceiling with
     * Floors.loot (Floor I's chests reached them), adds a second and third piece of gear to the deepest floors' richest chests, and
     * pays a Descent and the Throne as boss chambers with more: a second relic, and on the Throne an enchanted golden apple.
     * Floor I's ordinary and boss rooms roll exactly as before.
     */
    public static List<ItemStack> roll(Layout.Room r,double multiplier){
        List<ItemStack> out=new ArrayList<>();if(r.kind==Layout.Kind.REFUGE)return out;
        Random rng=new Random(r.hash^0x4c6f6f74L);int b=LootCatalog.scaledBudget(budget(r),multiplier);
        LootCatalog.Profile profile=LootCatalog.profile(r.theme);boolean boss=LootCatalog.boss(r.kind.name());
        add(out,profile.provision,Math.min(cap(24,r.floor),2+b/3));
        add(out,"IRON_INGOT",Math.min(cap(24,r.floor),1+b/3));
        add(out,profile.essence,Math.min(cap(16,r.floor),1+b/4));
        if(b<7){add(out,"TORCH",4+b);finale(r,out);return out;}
        if(b>=13)add(out,"EXP_BOTTLE",Math.min(cap(16,r.floor),b/3));
        if(b>=18)add(out,"DIAMOND",Math.min(cap(6,r.floor),1+(b-18)/6));
        if(b>=12){
            int pieces=1+(r.floor>=2&&b>=40?1:0)+(r.floor>=3&&b>=70?1:0);List<LootCatalog.Gear> families=new ArrayList<>(profile.gear);
            for(int n=0;n<pieces&&!families.isEmpty();n++)out.add(gear(profile,families.remove(rng.nextInt(families.size())),r.tier,b>=26,boss,r.floor));
        }
        // The armory owns selection/rank gates; this is a dungeon combat reward path only. A finale room rolls as a boss chamber.
        if(r.tier>=3&&r.mobCount()>0){ArmoryCatalog.Type armory=ArmoryCatalog.roll(r.hash,r.theme,r.tier,LootCatalog.lootKind(r.kind.name()));if(armory!=null)out.add(Arsenal.create(armory));}
        if(boss){add(out,"GOLDEN_APPLE",(int)Math.round(2*Floors.loot(r.floor)));out.add(lore(r.theme));}
        finale(r,out);
        return out;
    }
    /** A floor guardian's or the Throne's own reward, whatever the loot budget: the second relic, and the Throne's enchanted apple. */
    private static void finale(Layout.Room r,List<ItemStack> out){
        if(r.finale()){ItemStack relic=Relics.finale(r);if(relic!=null)out.add(relic);}
        if(r.kind==Layout.Kind.THRONE)out.add(new ItemStack(Material.GOLDEN_APPLE,1,(short)1));
    }
    private static void add(List<ItemStack> out,String material,int count){
        Material m=Material.valueOf(material);out.add(new ItemStack(m,Math.max(1,Math.min(m.getMaxStackSize(),count))));
    }
    /** Public so an isolated probe can validate the production item factory. */
    public static ItemStack gear(LootCatalog.Profile profile,LootCatalog.Gear family,int tier,boolean diamond,boolean boss){return gear(profile,family,tier,diamond,boss,1);}
    /** Generation 7: a deeper floor's gear has its defining enchantment one level higher per floor below the first, and Unbreaking. */
    public static ItemStack gear(LootCatalog.Profile profile,LootCatalog.Gear family,int tier,boolean diamond,boolean boss,int floor){
        ItemStack item=new ItemStack(Material.valueOf(family.material(diamond)));ItemMeta meta=item.getItemMeta();
        meta.setDisplayName(ChatColor.LIGHT_PURPLE+profile.prefix+" "+item.getType().name().toLowerCase(Locale.ROOT).replace('_',' '));
        if(family.variant())meta.setDisplayName(ChatColor.LIGHT_PURPLE+profile.prefix+" "+family.name().toLowerCase(Locale.ROOT).replace('_',' '));
        meta.setLore(Arrays.asList(ChatColor.GRAY+"Recovered from "+(profile.theme.startsWith("The ")?"":"the ")+profile.theme+".",ChatColor.DARK_GRAY+GEAR_ORIGIN+Math.max(0,Math.min(5,tier))));item.setItemMeta(meta);
        Enchantment enchantment=Enchantment.getByName(family.enchantment);int deep=Math.max(0,Math.min(2,floor-1));
        // Never bypass Bukkit's applicability check, including axe/Sharpness differences.
        if(enchantment==null||!enchantment.canEnchantItem(item))enchantment=Enchantment.DURABILITY;
        if(enchantment!=null&&enchantment.canEnchantItem(item))item.addEnchantment(enchantment,Math.min(enchantment.getMaxLevel(),1+Math.max(0,Math.min(5,tier))/2+(boss?1:0)+deep));
        if(deep>0&&!Enchantment.DURABILITY.equals(enchantment)&&Enchantment.DURABILITY.canEnchantItem(item))item.addEnchantment(Enchantment.DURABILITY,Math.min(Enchantment.DURABILITY.getMaxLevel(),deep));
        return item;
    }
    /** The chapter book of a theme. Floor I keeps generation 6's book; the Underworks and the Citadel count their own chapters. */
    public static ItemStack lore(int chapter){
        int theme=Math.floorMod(chapter,LootCatalog.PROFILES.size()),floor=Floors.floorOfTheme(theme),number=theme-Floors.themeBase(floor)+1;
        LootCatalog.Profile profile=LootCatalog.profile(theme);ItemStack book=new ItemStack(Material.WRITTEN_BOOK);BookMeta meta=(BookMeta)book.getItemMeta();
        meta.setTitle(floor==1?"The House of Mercy":Floors.title(floor));meta.setAuthor("The Last Candle");
        meta.setDisplayName((floor==1?"House of Mercy":Floors.title(floor))+" — "+number+": "+profile.theme);
        if(floor==2)meta.setPages("The Underworks\n\nChapter "+number+": "+profile.theme+"\n\nBeneath the House of Mercy the stone goes on: galleries, forges and mines the Deep Tyrant dug out of fear. His stair leads deeper still.",profile.passage,
            "Every room keeps its own dangers, and down here they bite harder. Clear a room and its chest is yours to choose from.\n\nAbsolve enough rooms to unseal the Descent, where the Deep Tyrant waits. The gate in the Last Candle leads home.");
        else if(floor==3)meta.setPages("The Abyssal Citadel\n\nChapter "+number+": "+profile.theme+"\n\nRamparts over a sea of fire, gardens of nothing, and an army that forgot why it fights. The Abyssal Sovereign rules from the Throne.",profile.passage,
            "Nothing here is merciful by accident. Clear a room and its chest is yours to choose from.\n\nAbsolve enough rooms to unseal the Throne. When the Sovereign falls, the well in the Throne carries the victors home.");
        else meta.setPages("The Dungeon Dimension\n\nChapter "+number+": "+profile.theme+"\n\nAn endless dungeon beneath the House of Mercy. Stone-brick gates remember the way home.",profile.passage,
            "Barred doorways lead on; enemies cannot follow. Every room keeps its own dangers, marked in its stone. Clear a room and its chest is yours to choose from; treasure guardians wake when it opens.\n\nThe gate in the Last Candle leads home.");
        book.setItemMeta(meta);return book;
    }
}
