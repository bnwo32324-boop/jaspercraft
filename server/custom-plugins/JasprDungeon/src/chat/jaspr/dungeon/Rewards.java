package chat.jaspr.dungeon;

import java.util.*;
import org.bukkit.*;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.*;

/** Shared room loot only. No recipes, personal bonus rolls, or outside-world acquisition. */
public final class Rewards {
    private Rewards(){}
    public static int budget(Layout.Room r){return LootCatalog.budget(r.kind.name(),r.tier,r.mobCount());}
    public static List<ItemStack> roll(Layout.Room r,double multiplier){
        List<ItemStack> out=new ArrayList<>();if(r.kind==Layout.Kind.REFUGE)return out;
        Random rng=new Random(r.hash^0x4c6f6f74L);int b=LootCatalog.scaledBudget(budget(r),multiplier);
        LootCatalog.Profile profile=LootCatalog.profile(r.theme);
        add(out,profile.provision,Math.min(24,2+b/3));
        add(out,"IRON_INGOT",Math.min(24,1+b/3));
        add(out,profile.essence,Math.min(16,1+b/4));
        if(b<7){add(out,"TORCH",4+b);return out;}
        if(b>=13)add(out,"EXP_BOTTLE",Math.min(16,b/3));
        if(b>=18)add(out,"DIAMOND",Math.min(6,1+(b-18)/6));
        if(b>=12)out.add(gear(profile,profile.gear.get(rng.nextInt(profile.gear.size())),r.tier,b>=26,r.kind==Layout.Kind.BOSS));
        // The armory owns selection/rank gates; this is a dungeon combat reward path only.
        if(r.tier>=3&&r.mobCount()>0){ItemStack armory=Arsenal.roll(r);if(armory!=null)out.add(armory);}
        if(r.kind==Layout.Kind.BOSS){add(out,"GOLDEN_APPLE",2);out.add(lore(r.theme));}
        return out;
    }
    private static void add(List<ItemStack> out,String material,int count){
        Material m=Material.valueOf(material);out.add(new ItemStack(m,Math.max(1,Math.min(m.getMaxStackSize(),count))));
    }
    /** Public so an isolated probe can validate the production item factory. */
    public static ItemStack gear(LootCatalog.Profile profile,LootCatalog.Gear family,int tier,boolean diamond,boolean boss){
        ItemStack item=new ItemStack(Material.valueOf(family.material(diamond)));ItemMeta meta=item.getItemMeta();
        meta.setDisplayName(ChatColor.LIGHT_PURPLE+profile.prefix+" "+item.getType().name().toLowerCase(Locale.ROOT).replace('_',' '));
        if(family.variant())meta.setDisplayName(ChatColor.LIGHT_PURPLE+profile.prefix+" "+family.name().toLowerCase(Locale.ROOT).replace('_',' '));
        meta.setLore(Arrays.asList(ChatColor.GRAY+"Recovered from the "+profile.theme+".",ChatColor.DARK_GRAY+"The Penitent Below | Threat "+Math.max(0,Math.min(5,tier))));item.setItemMeta(meta);
        Enchantment enchantment=Enchantment.getByName(family.enchantment);
        // Never bypass Bukkit's applicability check, including axe/Sharpness differences.
        if(enchantment==null||!enchantment.canEnchantItem(item))enchantment=Enchantment.DURABILITY;
        if(enchantment!=null&&enchantment.canEnchantItem(item))item.addEnchantment(enchantment,Math.min(enchantment.getMaxLevel(),1+Math.max(0,Math.min(5,tier))/2+(boss?1:0)));
        return item;
    }
    public static ItemStack lore(int chapter){
        LootCatalog.Profile profile=LootCatalog.profile(chapter);ItemStack book=new ItemStack(Material.WRITTEN_BOOK);BookMeta meta=(BookMeta)book.getItemMeta();
        meta.setTitle("The House of Mercy");meta.setAuthor("The Last Candle");
        meta.setDisplayName("House of Mercy — "+(Math.floorMod(chapter,LootCatalog.PROFILES.size())+1)+": "+profile.theme);
        meta.setPages("The Penitent Below\n\nChapter "+(Math.floorMod(chapter,LootCatalog.PROFILES.size())+1)+": "+profile.theme+"\n\nAn endless dungeon beneath the House of Mercy. Stone-brick gates remember the way home.",profile.passage,
            "Pass through barred thresholds to enter the next room. Enemies cannot follow. Clear a room to unseal its reliquary. Rewards and cleared rooms persist.\n\nThe gate in the Last Candle leads home.");
        book.setItemMeta(meta);return book;
    }
}
