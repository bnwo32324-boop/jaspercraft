package chat.jaspr.biomes;

import java.util.*;
import java.lang.reflect.Method;
import org.bukkit.*;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.*;
import org.bukkit.potion.*;

/** Role- and difficulty-weighted caches; unique selection, guaranteed basics, no refill timer. */
public final class ExpeditionLoot {
    private static final class Entry {
        final String id;final Material material;final int weight,min,max,tier;
        Entry(Material m,int w,int a,int b,int t){id=null;material=m;weight=w;min=a;max=b;tier=t;}
        Entry(String s,int w,int a,int b,int t){id=s;material=null;weight=w;min=a;max=b;tier=t;}
    }
    private static void add(List<Entry> p,Material m,int w,int a,int b,int t){p.add(new Entry(m,w,a,b,t));}
    private static void add(List<Entry> p,String id,int w,int a,int b,int t){p.add(new Entry(id,w,a,b,t));}
    public static boolean ready(){return Bukkit.getPluginManager().isPluginEnabled("JasprApocalypse");}
    // Reflection keeps the terrain generator STARTUP-safe; equipment loads only after AuthMe/worlds.
    private static Object call(String cls,String method,Class<?>[] types,Object...args){
        try{return Class.forName("chat.jaspr.apocalypse."+cls,true,Bukkit.getPluginManager().getPlugin("JasprApocalypse").getClass().getClassLoader()).getMethod(method,types).invoke(null,args);}
        catch(Exception e){throw new IllegalStateException("Equipment factory unavailable: "+method,e);}
    }
    public static ItemStack custom(String id,int amount){
        ItemStack item;
        if(id.equals("relic")||id.equals("scrap"))item=(ItemStack)call("ApocalypseItems",id,new Class<?>[]{int.class},amount);
        else {item=(ItemStack)call("ApocalypseItems","gear",new Class<?>[]{String.class},id);item.setAmount(amount);}
        return item;
    }
    @SuppressWarnings("unchecked") private static List<String> category(String category){return new ArrayList<>(((Map<String,String>)call("ApocalypseItems","catalogue",new Class<?>[]{String.class},category)).keySet());}
    private static ItemStack equipment(String category,Random r,int tier){return equipment(category,r,tier,"",new HashSet<>());}
    private static ItemStack equipment(String category,Random r,int tier,String family,Set<String> selected){
        List<String> ids=category(category);ids.removeIf(id->selected.contains(id)||!WeaponLootRules.eligible(id,category,tier));
        if(ids.isEmpty())throw new IllegalStateException("Missing eligible equipment category "+category);
        int total=0;for(String id:ids)total+=WeaponLootRules.weight(family,id);int roll=r.nextInt(total);String choice=null;
        for(String id:ids)if((roll-=WeaponLootRules.weight(family,id))<0){choice=id;break;}
        selected.add(choice);return (ItemStack)call("ApocalypseItems","expedition",new Class<?>[]{String.class,int.class},choice,tier);
    }
    /**
     * One salvage-grade firearm, for the small dungeons rather than the planned vaults.
     * Returns null rather than throwing when the equipment plugin is not up yet, because
     * this runs from world generation and a missing gun must never cost a chunk.
     */
    public static ItemStack rareWeapon(Random r){
        if(!ready())return null;
        try{
            List<String> ids=WeaponLootRules.starterGuns();
            String choice=ids.get(r.nextInt(ids.size()));
            return (ItemStack)call("ApocalypseItems","expedition",new Class<?>[]{String.class,int.class},choice,2);
        }catch(RuntimeException e){return null;}
    }
    public static List<ItemStack> fold(long seed,int room){Random r=new Random(Terrain.mix(seed+room*273611L));List<ItemStack> a=new ArrayList<>();Set<String> selected=new HashSet<>();a.add(new ItemStack(Material.BREAD,3+r.nextInt(5)));a.add(new ItemStack(Material.IRON_NUGGET,12+r.nextInt(13)));a.add(book(r,3));a.add(equipment("material",r,3));if(room%4==0)a.add(custom("relic",1));if(room==15){a.add(equipment("melee",r,3,"backroom",selected));a.add(equipment("melee",r,3,"backroom",selected));}else if(room%4==3&&r.nextBoolean())a.add(equipment("melee",r,3,"backroom",selected));return a;}
    private static ItemStack trophy(StructurePlanner.Site site,int tier){ItemStack i=custom("expedition_trophy",1);ItemMeta a=i.getItemMeta();a.setDisplayName(ChatColor.GOLD+site.design.name+" — Expedition Trophy");a.setLore(Arrays.asList(ChatColor.GRAY+"Recovered from a tier "+tier+" guarded vault.",ChatColor.DARK_GRAY+"X "+site.x+" / Z "+site.z));i.setItemMeta(a);return i;}
    private static ItemStack potion(boolean strong){ItemStack i=new ItemStack(Material.POTION);PotionMeta p=(PotionMeta)i.getItemMeta();p.setBasePotionData(new PotionData(PotionType.INSTANT_HEAL,false,strong));i.setItemMeta(p);return i;}
    private static ItemStack book(Random r,int tier){ItemStack i=new ItemStack(Material.ENCHANTED_BOOK);EnchantmentStorageMeta m=(EnchantmentStorageMeta)i.getItemMeta();Enchantment[] a={Enchantment.DURABILITY,Enchantment.DAMAGE_ALL,Enchantment.PROTECTION_ENVIRONMENTAL,Enchantment.DIG_SPEED,Enchantment.ARROW_DAMAGE,Enchantment.LOOT_BONUS_BLOCKS};Enchantment e=a[r.nextInt(a.length)];m.addStoredEnchant(e,Math.min(e.getMaxLevel(),Math.max(1,tier-1)),false);i.setItemMeta(m);return i;}
    public static List<ItemStack> roll(long seed,StructurePlanner.Site site,StructurePlanner.Marker marker){
        int tier=Math.max(1,Math.min(5,site.design.tier));Random r=new Random(Terrain.mix(seed^site.key.hashCode()*173L^marker.ordinal*918273L));String role=marker.kind;
        List<Entry> pool=new ArrayList<>();List<ItemStack> out=new ArrayList<>();Set<String> selectedWeapons=new HashSet<>();String family=site.design.family.toLowerCase(Locale.ROOT);
        add(pool,Material.BREAD,16,2,7,1);add(pool,Material.COAL,12,3,12,1);add(pool,Material.TORCH,12,6,20,1);add(pool,Material.IRON_INGOT,8,1,5,1);add(pool,Material.STRING,7,2,6,1);add(pool,Material.LEATHER,7,2,5,1);
        add(pool,Material.ARROW,9,6,20,1);add(pool,Material.COOKED_BEEF,8,2,6,1);add(pool,Material.EXP_BOTTLE,5,2,8,2);add(pool,Material.SULPHUR,8,2,8,2);
        add(pool,"scrap",12,2,6,2);add(pool,Material.IRON_NUGGET,8,8,24,3);add(pool,Material.GOLD_INGOT,6,1,5,2);
        if(role.equals("medical")){out.add(potion(tier>=4));add(pool,Material.GOLDEN_APPLE,18,1,2,2);add(pool,Material.MILK_BUCKET,12,1,1,1);add(pool,Material.COOKED_CHICKEN,20,3,8,1);}
        if(role.equals("supply")){out.add(new ItemStack(Material.BREAD,4+r.nextInt(5)));add(pool,Material.LOG,12,4,12,1);add(pool,Material.CARROT_ITEM,8,2,6,1);add(pool,Material.SAPLING,6,1,3,1);add(pool,Material.IRON_PICKAXE,4,1,1,2);}
        if(role.equals("armory")||role.equals("vault")){
            out.add(new ItemStack(tier<3?Material.ARROW:Material.SULPHUR,8+tier*3));
            add(pool,Material.IRON_SWORD,8,1,1,1);add(pool,Material.SHIELD,8,1,1,1);add(pool,Material.IRON_CHESTPLATE,6,1,1,2);
            add(pool,Material.IRON_NUGGET,24,24,48,3);add(pool,"scrap",20,5,12,2);add(pool,Material.DIAMOND,8,1,3,3);
        }
        if(role.equals("relic")||role.equals("vault")){
            out.add(book(r,tier));add(pool,"relic",20,1,2,3);add(pool,Material.ENDER_PEARL,10,1,4,2);add(pool,Material.DIAMOND,14,1,4,3);add(pool,Material.EMERALD,12,2,7,2);add(pool,Material.BLAZE_ROD,6,1,3,3);
        }
        if(role.equals("medical")&&tier>=2)out.add(equipment("consumable",r,tier));
        if((role.equals("armory")||role.equals("relic"))&&tier>=3)out.add(equipment("material",r,tier));
        // Purpose-built weapon caches now pay out reliably. IDs are selected without replacement.
        if(role.equals("armory")&&tier>=2)out.add(equipment("melee",r,tier,family,selectedWeapons));
        if(role.equals("armory")&&tier>=4){out.add(equipment("gun",r,tier,family,selectedWeapons));out.add(new ItemStack(Material.IRON_NUGGET,48));}
        if(role.equals("armory")&&tier==5&&r.nextBoolean())out.add(equipment("gun",r,tier,family,selectedWeapons));
        if((role.equals("medical")||role.equals("supply"))&&tier>=2&&r.nextInt(role.equals("medical")?5:6)==0)out.add(equipment("melee",r,tier,family,selectedWeapons));
        if(role.equals("relic")&&tier>=3&&r.nextBoolean())out.add(equipment("melee",r,tier,family,selectedWeapons));
        if(role.equals("relic")&&tier>=4&&r.nextInt(5)==0)out.add(equipment("armor",r,tier));
        if(role.equals("vault")){
            if(tier>=4){out.add(equipment("gun",r,tier,family,selectedWeapons));out.add(equipment("gun",r,tier,family,selectedWeapons));out.add(equipment("melee",r,tier,family,selectedWeapons));out.add(equipment("armor",r,tier));out.add(new ItemStack(Material.IRON_NUGGET,64));}
            else if(tier>=2){out.add(equipment("melee",r,tier,family,selectedWeapons));if(tier==3){out.add(equipment("melee",r,tier,family,selectedWeapons));out.add(equipment("material",r,tier));}}
        }
        // Ecological supplies and clues are additional, not substitutes for the role's loot.
        if(family.matches(".*(water|harbor|sewer|metro|pump).*")){add(pool,Material.SPONGE,5,1,2,3);add(pool,Material.FISHING_ROD,6,1,1,1);}
        if(family.matches(".*(school|hospital|library|asylum).*")){add(pool,Material.BOOK,16,2,6,1);add(pool,Material.PAPER,9,4,12,1);}
        if(family.matches(".*(castle|tower|cathedral|crypt).*")){add(pool,Material.IRON_HELMET,8,1,1,2);add(pool,Material.GOLD_NUGGET,9,5,20,1);}
        pool.removeIf(e->e.tier>tier);
        int rolls=3+tier+r.nextInt(3);
        for(int n=0;n<rolls&&!pool.isEmpty();n++){
            int total=0;for(Entry e:pool)total+=e.weight;int pick=r.nextInt(total),index=0;while((pick-=pool.get(index).weight)>=0)index++;
            Entry e=pool.remove(index);int count=e.min+r.nextInt(e.max-e.min+1);out.add(e.material!=null?new ItemStack(e.material,count):custom(e.id,count));
        }
        if(role.equals("vault"))out.add(trophy(site,tier));
        // Survivor Gear (3.26.0): at most one trinket at the site's tier I-V, drawn last from this chest's own r so
        // everything above rolls exactly as before (GearLoot; none while JasprGear is absent).
        ItemStack gear=GearLoot.roll(r,tier);if(gear!=null)out.add(gear);
        return out;
    }
}
