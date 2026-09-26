package chat.jaspr.enchant.fx;

import net.minecraft.server.v1_12_R1.MobEffectList;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** SME PotionUtil: DEBUFFS/BUFFS built from the potion registry (empty blacklist). */
public final class Potions {
    private Potions() {}

    private static List<MobEffectList> debuffs, buffs;

    private static void init() {
        if (debuffs != null) return;
        List<MobEffectList> d = new ArrayList<>(), b = new ArrayList<>();
        try {
            Field bad = MobEffectList.class.getDeclaredField("c"); // isBadEffect
            bad.setAccessible(true);
            for (MobEffectList p : MobEffectList.REGISTRY) {
                if (bad.getBoolean(p)) d.add(p);
                else b.add(p);
            }
        } catch (Throwable t) {
            throw new IllegalStateException("potion registry unreadable", t);
        }
        debuffs = d;
        buffs = b;
    }

    public static MobEffectList negative(Random r) {
        init();
        if (debuffs.isEmpty()) return null;
        return debuffs.get(r.nextInt(debuffs.size()));
    }

    public static MobEffectList positive(Random r) {
        init();
        if (buffs.isEmpty()) return null;
        return buffs.get(r.nextInt(buffs.size()));
    }

    public static int debuffCount() {
        init();
        return debuffs.size();
    }
}
