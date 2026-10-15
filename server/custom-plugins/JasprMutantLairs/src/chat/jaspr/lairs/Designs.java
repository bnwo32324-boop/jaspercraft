package chat.jaspr.lairs;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** The twelve lairs, three per hostile mutant, in a fixed order (the planner's lattice indexes this list). */
final class Designs {
    private Designs() {}

    static final List<Design> ALL = Collections.unmodifiableList(Arrays.asList(
        new ZombieLairs.PlaguePit(), new ZombieLairs.Abattoir(), new ZombieLairs.Mausoleum(),
        new SkeletonLairs.OssuarySpire(), new SkeletonLairs.Ribcage(), new SkeletonLairs.Barbican(),
        new CreeperLairs.BlastCrater(), new CreeperLairs.Greenhouse(), new CreeperLairs.PowderMill(),
        new EndermanLairs.VoidObelisk(), new EndermanLairs.Observatory(), new EndermanLairs.EnderGrove()
    ));

    static Design byId(String id) {
        for (Design d : ALL) if (d.id.equals(id)) return d;
        return null;
    }
}
