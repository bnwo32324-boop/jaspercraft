package chat.jaspr.biomes;

import java.util.*;

/** Pure loot affinities: broad variety remains possible, but the ruin biases its arsenal. */
public final class WeaponLootRules {
    private static Set<String> ids(String values){return Collections.unmodifiableSet(new HashSet<>(Arrays.asList(values.split(" "))));}
    private static final Set<String> STARTER_MELEE=ids("trench_blade shock_baton gravespike wardcleaver suture_sickle vesper_dagger wire_whip");
    private static final Set<String> INDUSTRIAL=ids("rifle shotgun whisper tempest bastion longwatch adjudicator sepulcher turnstile tunnelrat blackbox quarantine signal watchtower lockjaw ashfall pallbearer deadfrequency trench_blade breacher_axe shock_baton sentinel_spear railpick rebar_sword mourning_glaive wire_whip");
    private static final Set<String> OCCULT=ids("railgun sunlance cyclops vesper ossuary gallows bellringer choir cenotaph witchlight hexbreaker ironpsalm reaper_scythe gravespike pilgrim_lance tollhammer vesper_dagger hollow_halberd ossuary_flail altar_mallet execution_sword");
    private static final Set<String> ANOMALOUS=ids("railgun sunlance frostbite cinder whiteout nullpoint witchlight stormcoil hexbreaker deadfrequency mono_katana gravity_maul thermal_machete cautery_sabre suture_sickle ember_falchion");
    /** Salvage-grade firearms: the only ones common finds are ever allowed to be. */
    private static final Set<String> STARTER_GUNS=ids("sepulcher turnstile cinder vesper whisper tunnelrat ossuary lockjaw cyclops bellringer tempest quarantine");
    public static List<String> starterGuns(){return new ArrayList<>(STARTER_GUNS);}
    private WeaponLootRules(){}
    public static boolean eligible(String id,String category,int tier){return category.equals("gun")?tier>=4:!category.equals("melee")||tier>=3||tier==2&&STARTER_MELEE.contains(id);}
    public static int weight(String family,String id){
        String f=family.toLowerCase(Locale.ROOT);
        boolean occult=f.matches(".*(castle|cathedral|crypt|catacomb|chapel|abbey|ossuary|tower|dungeon|shrine|monastery|fortress|reliquary|mortuary).*"),
            anomaly=f.matches(".*(backroom|laboratory|research|reactor|asylum|hospital|infirmary|observatory|alien|silo|thermal).*"),
            industry=f.matches(".*(metro|sewer|rail|bunker|military|barracks|telecom|signal|plane|city|metropolis|harbor|port|factory|foundry|industrial|prison|checkpoint|pump|refuge|cistern|excavation).*" );
        return (occult&&OCCULT.contains(id)||anomaly&&ANOMALOUS.contains(id)||industry&&INDUSTRIAL.contains(id))?4:1;
    }
}
