package chat.jaspr.biomes;

import java.lang.reflect.*;
import java.util.*;
import org.bukkit.*;
import org.bukkit.inventory.ItemStack;

/** Real Paper item factories, deterministic roles, all tiers, no mutation of production claims. */
final class WeaponLootProbe {
    private static int assertions;
    private static void check(boolean value,String message){assertions++;if(!value)throw new AssertionError(message);}
    @SuppressWarnings("unchecked") static int run()throws Exception{
        Class<?> items=Class.forName("chat.jaspr.apocalypse.ApocalypseItems",true,Bukkit.getPluginManager().getPlugin("JasprApocalypse").getClass().getClassLoader());
        Method identify=items.getMethod("id",ItemStack.class),catalogue=items.getMethod("catalogue",String.class);
        Set<String> guns=((Map<String,String>)catalogue.invoke(null,"gun")).keySet(),melee=((Map<String,String>)catalogue.invoke(null,"melee")).keySet();
        check(guns.size()==35&&melee.size()==24,"Expanded weapon catalogues");Set<String> seenGuns=new HashSet<>(),seenMelee=new HashSet<>();
        Constructor<StructurePlanner.Site> siteConstructor=StructurePlanner.Site.class.getDeclaredConstructor(long.class,StructureCatalog.Design.class,int.class,int.class,String.class,int.class,int.class);siteConstructor.setAccessible(true);
        Constructor<StructurePlanner.Marker> markerConstructor=StructurePlanner.Marker.class.getDeclaredConstructor(int.class,int.class,int.class,String.class,int.class);markerConstructor.setAccessible(true);
        for(int tier=1;tier<=5;tier++){
            final int difficulty=tier;StructureCatalog.Design design=StructureCatalog.ALL.stream().filter(d->d.tier==difficulty).findFirst().get();
            StructurePlanner.Site site=siteConstructor.newInstance(891L,design,0,0,"loot-fixture-"+tier,0,0);
            for(String role:Arrays.asList("supply","medical","armory","relic","vault")){
                StructurePlanner.Marker marker=markerConstructor.newInstance(0,70,0,role,1);
                for(int roll=0;roll<160;roll++){
                    List<ItemStack> loot=ExpeditionLoot.roll(roll,site,marker);Set<String> selected=new HashSet<>();int gunCount=0,meleeCount=0,ammo=0;
                    check(loot.size()<=27,"One chest slot budget");
                    for(ItemStack item:loot){
                        check(item.getAmount()>0&&item.getAmount()<=item.getMaxStackSize(),"Native valid stack size");
                        String id=(String)identify.invoke(null,item);
                        if(guns.contains(id)){gunCount++;seenGuns.add(id);check(selected.add(id),"Distinct guns in same cache");}
                        if(melee.contains(id)){meleeCount++;seenMelee.add(id);check(selected.add(id),"Distinct melee in same cache");check(WeaponLootRules.eligible(id,"melee",tier),"Melee tier eligibility");}
                        if(id.equals("ammo"))ammo+=item.getAmount();
                    }
                    check(tier>=4||gunCount==0,"Guns remain late-game tier 4/5");
                    check(tier>=2||meleeCount==0,"Tier 1 uses ordinary weapons");
                    if(role.equals("armory")&&tier>=2)check(meleeCount>=1,"Armory guarantees custom melee");
                    if(role.equals("armory")&&tier>=4)check(gunCount>=1&&ammo>=48,"Late armory guarantees gun and ammunition");
                    if(role.equals("vault")&&tier>=4)check(gunCount==2&&meleeCount==1&&ammo>=64,"Guarded vault guarantees 2 unique guns, melee and ammunition");
                    if(role.equals("vault")&&tier==3)check(meleeCount==2,"Mid-tier vault has 2 distinct melee weapons");
                    if(role.equals("vault")&&tier==2)check(meleeCount==1,"Low-tier vault has an eligible melee weapon");
                }
            }
        }
        check(seenGuns.equals(guns),"Every gun is reachable in finite-cache loot");check(seenMelee.equals(melee),"Every melee weapon is reachable in finite-cache loot");
        check(WeaponLootRules.weight("metro","tunnelrat")>WeaponLootRules.weight("metro","witchlight"),"Metro biases military salvage");
        check(WeaponLootRules.weight("cathedral","choir")>WeaponLootRules.weight("cathedral","tunnelrat"),"Cathedral biases occult weapons");
        check(WeaponLootRules.weight("backroom","nullpoint")>WeaponLootRules.weight("backroom","turnstile"),"Fold biases anomalous weapons");
        for(String f:Arrays.asList("research","infirmary","catacomb","reliquary","foundry","barracks","refuge","signal","cistern")){
            check(StructureCatalog.ALL.stream().anyMatch(d->d.family.equals(f)),"Affinity uses actual catalogue family "+f);
            check(guns.stream().anyMatch(id->WeaponLootRules.weight(f,id)>1),"Expanded family has themed weapon weights "+f);
        }
        for(int room=0;room<16;room++){List<ItemStack> loot=ExpeditionLoot.fold(891,room);Set<String> selected=new HashSet<>();int count=0;for(ItemStack item:loot){check(item.getAmount()<=item.getMaxStackSize(),"Fold stack budget");String id=(String)identify.invoke(null,item);check(!guns.contains(id),"Tier3 Fold has no late-game guns");if(melee.contains(id)){check(selected.add(id),"Fold distinct melee");count++;}}if(room==15)check(count==2,"Final Fold cache has two distinct melee weapons");}
        Bukkit.getLogger().info("WEAPON_LOOT_PASS assertions="+assertions+" guns="+seenGuns.size()+" melee="+seenMelee.size()+" rolls=4000");return assertions;
    }
}
